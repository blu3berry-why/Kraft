plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "com.blu3berry.kraft"
version = (project.properties["kraft.version"] as? String) ?: "0.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

// Central Portal validation requires a sources jar on jar-packaged artifacts
// (the POM-only plugin marker is exempt). KMP modules publish sources
// automatically; this kotlin-jvm module must opt in.
java {
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create("kraft") {
            id = "com.blu3berry.kraft"
            implementationClass = "com.blu3berry.kraft.gradle.KraftGradlePlugin"
            displayName = "Kraft"
            description = "Applies the Kraft KSP automapper to a Kotlin Multiplatform, JVM, or " +
                "Android module: adds version-aligned kraft-ksp/kraft-annotations dependencies, " +
                "wires generated sources and task ordering where needed, and provides the typed " +
                "kraft { } configuration DSL."
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.multiplatform)
    compileOnly(libs.ksp.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
}

// The plugin pins kraft-ksp/kraft-annotations to its own version at runtime;
// the version is injected into a resource at build time (see KraftGradlePlugin).
tasks.processResources {
    val kraftVersion = version.toString()
    inputs.property("kraftVersion", kraftVersion)
    filesMatching("kraft-plugin.properties") {
        expand("version" to kraftVersion)
    }
}

// Functional tests resolve the plugin from a local maven repo instead of
// TestKit's withPluginClasspath: the injected TestKit classpath is isolated
// from the plugins the test build applies (Kotlin, KSP), which breaks the
// production classloader topology this plugin depends on (all plugins of one
// plugins-block share a classpath realm).
val testRepoDir = layout.buildDirectory.dir("test-repo")

publishing {
    repositories {
        maven {
            name = "test"
            url = uri(testRepoDir)
        }
    }
}

tasks.test {
    useJUnitPlatform {
        // CI drops tagged tests on toolchain combinations the tag documents as
        // unsupported (e.g. -PkraftExcludeTags=android on Gradle 9.x, which AGP
        // 8.11.2 cannot load). Comma-separated.
        (findProperty("kraftExcludeTags") as? String)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.takeIf { it.isNotEmpty() }
            ?.let { excludeTags(*it.toTypedArray()) }
    }
    dependsOn("publishAllPublicationsToTestRepository")
    // The end-to-end functional test compiles a real consumer project, which
    // must resolve kraft-ksp/kraft-annotations (and kraft-core, ksp's runtime
    // dep) at the in-dev version — publish them to the shared local test repo.
    dependsOn(
        ":kraft-annotations:publishAllPublicationsToKraftTestRepository",
        ":kraft-core:publishAllPublicationsToKraftTestRepository",
        ":kraft-ksp:publishAllPublicationsToKraftTestRepository",
    )
    // Functional tests generate small Gradle projects that apply real Kotlin/KSP
    // plugin versions; keep them aligned with the catalog. (Explicit catalog API:
    // `libs.versions.kotlin` would resolve against the `kotlin {}` DSL accessor here.)
    val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    // Overridable from CI to matrix-test other Kotlin versions without test changes.
    systemProperty(
        "kraft.test.kotlinVersion",
        (findProperty("kraft.test.kotlinVersion") as? String)?.takeIf { it.isNotBlank() }
            ?: catalog.findVersion("kotlin").get().requiredVersion
    )
    // Overridable from CI to matrix-test other KSP versions without test changes.
    systemProperty(
        "kraft.test.kspVersion",
        (findProperty("kraft.test.kspVersion") as? String)?.takeIf { it.isNotBlank() }
            ?: catalog.findVersion("ksp").get().requiredVersion
    )
    // Overridable from CI to matrix-test other AGP versions without test changes.
    systemProperty(
        "kraft.test.agpVersion",
        (findProperty("kraft.test.agpVersion") as? String)?.takeIf { it.isNotBlank() }
            ?: catalog.findVersion("agp").get().requiredVersion
    )
    // Unset by default: TestKit then runs the generated builds on the Gradle
    // version running this build. CI overrides it to matrix-test other Gradles.
    (findProperty("kraft.test.gradleVersion") as? String)?.takeIf { it.isNotBlank() }?.let {
        systemProperty("kraft.test.gradleVersion", it)
    }
    systemProperty("kraft.test.compileSdk", catalog.findVersion("android-compileSdk").get().requiredVersion)
    systemProperty("kraft.test.pluginVersion", version.toString())
    systemProperty("kraft.test.repo", testRepoDir.get().asFile.absolutePath)
    systemProperty(
        "kraft.test.libsRepo",
        rootProject.layout.buildDirectory.dir("kraft-test-repo").get().asFile.absolutePath
    )
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

// Deliberately NOT applying the kraft-publish convention: it attaches the javadoc
// jar to every MavenPublication, which would corrupt the plugin MARKER publication
// (com.blu3berry.kraft:com.blu3berry.kraft.gradle.plugin) — markers must be POM-only.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://github.com/blu3berry-why/Kraft")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("blu3berry-why")
                    name.set("blu3berry")
                    url.set("https://github.com/blu3berry-why")
                }
            }
            scm {
                url.set("https://github.com/blu3berry-why/Kraft")
                connection.set("scm:git:git://github.com/blu3berry-why/Kraft.git")
                developerConnection.set("scm:git:ssh://github.com/blu3berry-why/Kraft.git")
            }
        }
    }
    // pluginMaven-only additions, never on the marker: the javadoc jar, and the
    // POM name/description Central validation requires — the marker POM gets its
    // own from the gradlePlugin { } displayName/description, pluginMaven doesn't.
    // (matching{} instead of named(): java-gradle-plugin registers pluginMaven
    // lazily in afterEvaluate, so it isn't addressable by name yet.)
    publications.withType<MavenPublication>()
        .matching { it.name == "pluginMaven" }
        .configureEach {
            artifact(javadocJar)
            pom {
                name.set("kraft-gradle-plugin")
                description.set(
                    "Gradle plugin for the Kraft KSP automapper: one-line setup for Kotlin " +
                        "Multiplatform, JVM, and Android modules with version-aligned dependencies " +
                        "and a typed kraft { } configuration DSL."
                )
            }
        }
}

// Sign only when a real publish was requested. The functional tests publish to
// the local test repo on every `test` run, and wiring signing there makes the
// build hang on a gpg passphrase prompt in non-interactive shells.
// Caveat: matching is on literal task names, so an abbreviated invocation
// (e.g. `gradlew pubAgg…`) skips signing — Central then rejects the upload
// loudly. Always use full task names for release publishes (CI does).
val realPublishRequested = gradle.startParameter.taskNames.any {
    it.contains("publish", ignoreCase = true) &&
        !it.contains("MavenLocal") &&
        !it.contains("TestRepository")
}
if (realPublishRequested) {
    signing {
        useGpgCmd()
        isRequired = findProperty("signing.gnupg.keyName") != null
        sign(publishing.publications)
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
