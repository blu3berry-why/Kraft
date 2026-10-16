package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec requirements "One-to-one key mappings" and "Key mappings that can merge entries are
 * refused" (tasks 6.2 and 6.3).
 */
@OptIn(ExperimentalCompilerApi::class)
class MapKeyTest {

    private val items = """
        data class ItemSource(val id: Int)
        data class ItemDto(val id: Int)
        @JvmInline value class UserId(val value: String)
    """

    private val sameNamedEnums = """
        enum class StatusDto { OPEN, CLOSED }
        enum class Status { OPEN, CLOSED }
    """

    private val oneToOneMapEnum = """
        enum class StatusDto { OPEN, CLOSED }
        enum class Status { ACTIVE, DONE }

        @com.blu3berry.kraft.config.MapEnum(
            source = StatusDto::class,
            target = Status::class,
            fieldMappings = [
                com.blu3berry.kraft.config.FieldMapping(source = "OPEN", target = "ACTIVE"),
                com.blu3berry.kraft.config.FieldMapping(source = "CLOSED", target = "DONE")
            ]
        )
        object StatusMapping
    """

    // REOPENED is mapped before OPEN in fieldMappings; the message lists them in declaration order.
    private val manyToOneMapEnum = """
        enum class StatusDto { OPEN, REOPENED, CLOSED }
        enum class Status { ACTIVE, CLOSED }

        @com.blu3berry.kraft.config.MapEnum(
            source = StatusDto::class,
            target = Status::class,
            fieldMappings = [
                com.blu3berry.kraft.config.FieldMapping(source = "REOPENED", target = "ACTIVE"),
                com.blu3berry.kraft.config.FieldMapping(source = "OPEN", target = "ACTIVE")
            ]
        )
        object StatusMapping
    """

    private val fix =
        "Keep the key type the same on both sides, use a value-class or one-to-one enum key, or map the property with @MapUsing."

    // ---------- One-to-one key mappings ----------

    @Test
    fun `Value class keys and nested values`() {
        val text = generatedText(
            models("Map<String, ItemSource>", "Map<UserId, ItemDto>", name = "byUser", extraModels = items)
        )
        assertThat(text).contains("byUser = this.byUser.entries.associate { UserId(it.key) to it.value.toItemDto() }")
    }

    @Test
    fun `Keys only`() {
        val text = generatedText(models("Map<UserId, Int>", "Map<String, Int>", name = "counts", extraModels = items))
        assertThat(text).contains("counts = this.counts.mapKeys { it.key.value }")
    }

    @Test
    fun `Auto-derived enum keys`() {
        val text = generatedText(
            models("Map<StatusDto, Int>", "Map<Status, Int>", name = "counts", extraModels = sameNamedEnums)
        )
        assertThat(text).contains("counts = this.counts.mapKeys { it.key.toStatus() }")
        // The enum pair is discovered from the map key, with no @MapEnum.
        assertThat(text).contains("fun StatusDto.toStatus()")
    }

    @Test
    fun `One-to-one MapEnum keys`() {
        val text = generatedText(
            models("Map<StatusDto, Int>", "Map<Status, Int>", name = "counts", extraModels = oneToOneMapEnum)
        )
        assertThat(text).contains("counts = this.counts.mapKeys { it.key.toStatus() }")
    }

    @Test
    fun `Wrapped keys into a MutableMap and from a nullable map`() {
        assertThat(
            generatedText(models("Map<String, ItemSource>", "MutableMap<UserId, ItemDto>", name = "m", extraModels = items))
        ).contains("m = this.m.entries.associate { UserId(it.key) to it.value.toItemDto() }.toMutableMap()")
        assertThat(generatedText(models("Map<UserId, Int>", "MutableMap<String, Int>", name = "m", extraModels = items)))
            .contains("m = this.m.mapKeys { it.key.value }.toMutableMap()")
        assertThat(generatedText(models("Map<UserId, Int>?", "Map<String, Int>?", name = "m", extraModels = items)))
            .contains("m = this.m?.mapKeys { it.key.value }")
        assertThat(
            generatedText(models("Map<String, ItemSource>?", "Map<UserId, ItemDto>?", name = "m", extraModels = items))
        ).contains("m = this.m?.entries?.associate { UserId(it.key) to it.value.toItemDto() }")
    }

    @Test
    fun `Nullable keys are not mapped`() {
        val result = compile(models("Map<String?, Int>", "Map<UserId, Int>", name = "m", extraModels = items))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).doesNotContain("[KRAFT-E064]")
    }

    // ---------- Key mappings that can merge entries are refused ----------

    @Test
    fun `Nested-mapper keys`() {
        val keys = """
            data class KeySource(val id: Int)
            data class KeyDto(val id: Int)
        """
        val result = compile(models("Map<KeySource, Int>", "Map<KeyDto, Int>", name = "m", extraModels = keys))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E064] Cannot map property 'm': keys KeySource → KeyDto would go through a nested mapper, " +
                "which can map two different keys to the same key and silently drop entries."
        )
        assertThat(result.messages).contains(fix)
        assertThat(result.messages).doesNotContain("[KRAFT-E005]")
    }

    @Test
    fun `KraftConverter keys`() {
        val converter = """
            @com.blu3berry.kraft.config.KraftConverter
            fun Long.toKey(): String = toString()
        """
        val result = compile(models("Map<Long, Int>", "Map<String, Int>", name = "m", extraModels = converter))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E064] Cannot map property 'm': keys Long → String would go through @KraftConverter Long.toKey(), " +
                "which can map two different keys to the same key and silently drop entries."
        )
        assertThat(result.messages).contains(fix)
        assertThat(result.messages).doesNotContain("[KRAFT-E005]")
    }

    @Test
    fun `Many-to-one MapEnum keys`() {
        val result = compile(
            models("Map<StatusDto, Int>", "Map<Status, Int>", name = "counts", extraModels = manyToOneMapEnum)
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E064] Cannot map property 'counts': keys StatusDto → Status would go through " +
                "@MapEnum StatusDto → Status, which maps OPEN, REOPENED to ACTIVE and would silently drop entries."
        )
        assertThat(result.messages).contains(fix)
        assertThat(result.messages).doesNotContain("[KRAFT-E005]")
    }

    @Test
    fun `Many-to-one enum as a value is still allowed`() {
        val text = generatedText(
            models("Map<String, StatusDto>", "Map<String, Status>", name = "m", extraModels = manyToOneMapEnum)
        )
        assertThat(text).contains("m = this.m.mapValues { it.value.toStatus() }")
    }
}
