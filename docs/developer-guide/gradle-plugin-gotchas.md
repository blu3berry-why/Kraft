# Gradle Plugin — Authoring Gotchas

Traps hit while building and releasing `kraft-gradle-plugin`. Each is enforced by a code comment at the site; this page is the central index so the next contributor recognizes the pattern before rediscovering it.

## Configuration-time vs execution-time

### `extensions` / project state inside `doLast` resolves to the **Task**, not the Project

Inside a `doLast { }` block, `extensions.getByName("ksp")` returns the *Task's* extension container (usually just `ExtraPropertiesExtension`), not the project's — and reading project state there is illegal under the configuration cache anyway.

**Fix:** capture what you need at configuration time (when the task is realized), print/use it in `doLast`:

```kotlin
tasks.register("probe") {
    val kspArgs = project.extensions.getByName("ksp") /* … */   // configuration time
    doLast { println(kspArgs) }                                 // execution time
}
```

Bit twice this repo — the functional-test probes both started in `doLast`.

### Reading `task.publication` in an `onlyIf` breaks the configuration cache

`onlyIf { publication.name == "jvm" }` captures a live Gradle model object into the spec, which the config cache can't serialize.

**Fix:** read the value at configuration time into a local, close over the local:

```kotlin
val publicationName = publication.name
onlyIf { publicationName == "jvm" }
```

(Was config-cache-incompatible in `kraft-core` and `kraft-ksp` publishing; only survived CI because `publishAggregation…` runs with `--no-configuration-cache`.)

### Emit KSP args in `afterEvaluate`

The `kraft { }` DSL is populated during script evaluation, *after* the plugin applies. Emit the derived `kraft.*` KSP options inside `project.afterEvaluate { }` so the user's block is fully read first. The project-path `moduleId` default must not clobber a value the user set via a raw `ksp { arg(...) }` block.

## TestKit functional tests

### Escape `$` in generated build scripts

A build script written into a TestKit temp project is itself compiled by Kotlin. A literal `${target}` in a string the *consumer* passes to Kraft gets interpreted by the temp script's own compiler → `Unresolved reference: target`. Escape it so the file contains `\${target}`.

### Same-version artifact resolution

An end-to-end test that actually *compiles* a consumer must resolve `kraft-ksp`/`kraft-annotations`/`kraft-core` at the in-dev version. Publish them to a shared local test repo (`kraftTestRepository`) and point the temp project's `dependencyResolutionManagement` at it. Config-only probe tests don't need this — inspecting a configuration's declared dependencies doesn't download them.

## Publishing (java-gradle-plugin)

### The marker POM gets metadata the `pluginMaven` POM does not

`java-gradle-plugin` produces two publications: the **plugin marker** (`<id>.gradle.plugin`, POM-only) and **`pluginMaven`** (the real jar). The marker inherits `name`/`description` from the `gradlePlugin { plugins { … } }` block; `pluginMaven` does **not**. Maven Central validation rejects a jar publication missing `<name>`/`<description>`.

**Fix:** set them on `pluginMaven` explicitly (the marker keeps its own):

```kotlin
publications.withType<MavenPublication>()
    .matching { it.name == "pluginMaven" }
    .configureEach {
        pom { name.set("kraft-gradle-plugin"); description.set("…") }
    }
```

This failed an actual release (0.12.0), fixed in #96. The sources-jar requirement is the same class — `pluginMaven` needs `withSourcesJar()` too, while the POM-only marker is exempt.

### Signing hangs on gpg in non-interactive shells

Wiring `signing { sign(publications) }` unconditionally makes local/test-repo publishes (the functional tests publish on every `test` run) hang on a gpg passphrase prompt. Gate signing to real Central publishes by task name (excluding `MavenLocal`/`TestRepository`). Caveat: matching is on literal task names, so a CLI abbreviation skips signing — CI uses full task names.
