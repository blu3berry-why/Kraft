package com.blu3berry.kraft.gradle

import org.gradle.api.Project

/**
 * Wiring for single-target modules: `org.jetbrains.kotlin.jvm` and
 * `org.jetbrains.kotlin.android`. Deliberately free of AGP (and Kotlin-plugin)
 * types: on these targets the KSP plugin wires generated sources and task
 * dependencies itself, and the `ksp` configuration fans out to every Android
 * variant — so all Kraft has to add is version-pinned dependencies and the
 * shared KSP args. No AGP classes touched keeps the plugin insensitive to AGP
 * version changes.
 */
internal object KraftSingleTargetWiring {

    fun configure(project: Project, kraftVersion: String, extension: KraftExtension) {
        project.dependencies.add("ksp", "com.blu3berry.kraft:kraft-ksp:$kraftVersion")
        project.dependencies.add("implementation", "com.blu3berry.kraft:kraft-annotations:$kraftVersion")
        KraftKspArgEmitter.register(project, extension)
    }
}
