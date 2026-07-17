package com.blu3berry.kraft.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Typed `kraft { }` configuration for the Kraft Gradle plugin.
 *
 * A thin front-end over the KSP processor options the AutoMapper reads from
 * `env.options`: every value set here is translated 1:1 into a `kraft.*` KSP
 * argument by [KraftKmpWiring]. Nothing here changes the processor — it exists
 * so consumers write typed Gradle instead of raw `ksp { arg("kraft.side.…") }`.
 *
 * ```kotlin
 * kraft {
 *     functionNameFormat = "to${'$'}{target}From${'$'}{source}"   // optional
 *     side("dto")    { packagePattern = "com.example.dto.**" }     // name defaults to "Dto"
 *     side("domain") { packagePattern = "com.example.domain.**" }  // name defaults to "Domain"
 * }
 * ```
 */
abstract class KraftExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * `kraft.moduleId`. Defaults to the project path (set by the plugin) so
     * cross-module delegate diagnostics name real modules. Override only to pin
     * a stable id independent of the project path.
     */
    abstract val moduleId: Property<String>

    /** `kraft.functionNameFormat`. Unset → the processor default `to${'$'}{target}`. */
    abstract val functionNameFormat: Property<String>

    /** Named side registry: `side("dto") { … }` → `kraft.side.dto.*`. */
    val sides: NamedDomainObjectContainer<KraftSide> =
        objects.domainObjectContainer(KraftSide::class.java)

    /** Configure (creating if needed) the side in slot [slot]. */
    fun side(slot: String, action: Action<KraftSide>) {
        action.execute(sides.maybeCreate(slot))
    }

    /** Configure the whole side container, e.g. `sides { create("dto") { … } }`. */
    fun sides(action: Action<NamedDomainObjectContainer<KraftSide>>) {
        action.execute(sides)
    }
}

/**
 * One entry in the Kraft side registry. The slot (container name) is the
 * `kraft.side.<slot>` key; [sideName] is the readable name used in generated
 * short aliases and defaults to the slot capitalized.
 */
abstract class KraftSide(private val slot: String) : Named {

    override fun getName(): String = slot

    /** `kraft.side.<slot>.name`. Unset → the slot capitalized (`dto` → `Dto`). */
    abstract val sideName: Property<String>

    /** `kraft.side.<slot>.packagePattern`. Required — the glob matching this side's classes. */
    abstract val packagePattern: Property<String>

    /** `kraft.side.<slot>.template`. Unset → the processor default `to{side}`. */
    abstract val template: Property<String>

    /** `kraft.side.<slot>.emitMode`. Unset → the processor default `BOTH`. `BOTH` | `FULL_NAME_ONLY`. */
    abstract val emitMode: Property<String>
}
