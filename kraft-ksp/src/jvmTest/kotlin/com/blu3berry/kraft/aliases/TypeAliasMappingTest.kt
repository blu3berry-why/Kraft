package com.blu3berry.kraft.aliases

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Properties whose declared type is a `typealias` must map as if they were
 * declared with the underlying type. KSP never auto-expands aliases —
 * `KSType.declaration` returns the [com.google.devtools.ksp.symbol.KSTypeAlias]
 * node, not the underlying class — so the processor has to unwrap them itself.
 *
 * Real-world repro: kmpgen 1.5.0 declares DTO date-time fields as
 * `typealias SerializableISO8601Instant = @Serializable(...) Instant`.
 */
@OptIn(ExperimentalCompilerApi::class)
class TypeAliasMappingTest {

    @Test
    fun `non-null aliased property maps as underlying type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            data class EventDto(val name: String, val createdAt: AliasedStamp)
            data class Event(val name: String, val createdAt: Stamp)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun EventDto.toEvent()")
        assertThat(file).contains("createdAt = this.createdAt")
    }

    @Test
    fun `nullable alias of non-null underlying type carries use-site nullability`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            data class EventDto(val name: String, val updatedAt: AliasedStamp?)
            data class Event(val name: String, val updatedAt: Stamp?)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun EventDto.toEvent()")
        assertThat(file).contains("updatedAt = this.updatedAt")
    }

    @Test
    fun `alias whose expansion is nullable maps to the nullable underlying type`() {
        // kmpgen 1.6.0-RC01 emits these for nullable $refs:
        // `typealias NullableRefTypealias = NullableInlineObject?`
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias NullableStamp = Stamp?

            data class EventDto(val name: String, val updatedAt: NullableStamp)
            data class Event(val name: String, val updatedAt: Stamp?)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun EventDto.toEvent()")
        assertThat(file).contains("updatedAt = this.updatedAt")
    }

    @Test
    fun `chained aliases unwrap to the underlying type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias InnerStamp = Stamp
            typealias OuterStamp = InnerStamp

            data class EventDto(val createdAt: OuterStamp)
            data class Event(val createdAt: Stamp)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("createdAt = this.createdAt")
    }

    @Test
    fun `aliased property on both sides maps directly`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            data class EventDto(val createdAt: AliasedStamp)
            data class Event(val createdAt: AliasedStamp)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("createdAt = this.createdAt")
    }

    @Test
    fun `converter declared with alias receiver applies to underlying-typed property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package aliased

            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            @com.blu3berry.kraft.config.KraftConverter
            fun AliasedStamp.toIso(): String = epochMillis.toString()

            data class EventDto(val createdAt: Stamp)
            data class Event(val createdAt: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val mapper = generated.first { it.readText().contains("fun EventDto.toEvent()") }.readText()

        assertThat(mapper).contains("createdAt = this.createdAt.toIso()")
    }

    @Test
    fun `alias in MapConfig class literal resolves to underlying class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class EventDto(val name: String)
            data class Event(val name: String)

            typealias AliasedEventDto = EventDto

            @com.blu3berry.kraft.config.MapConfig(
                source = AliasedEventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun EventDto.toEvent()")
        assertThat(file).contains("name = this.name")
    }

    @Test
    fun `list of aliased elements maps as list of underlying type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            data class EventDto(val stamps: List<AliasedStamp>)
            data class Event(val stamps: List<Stamp>)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("stamps = this.stamps")
    }

    @Test
    fun `MapUsing converter with alias parameter matches underlying-typed property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Stamp(val epochMillis: Long)

            typealias AliasedStamp = Stamp

            data class EventDto(val createdAt: Stamp)
            data class Event(val createdAt: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventDto::class,
                target = Event::class
            )
            object EventMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "createdAt", target = "createdAt")
                fun convert(stamp: AliasedStamp): String = stamp.epochMillis.toString()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("createdAt = EventMapper.convert(this.createdAt)")
    }

    @Test
    fun `list of aliased elements with different element types generates child mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            typealias AliasedItemSource = ItemSource

            data class OrderSource(val items: List<AliasedItemSource>)

            @com.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val items: List<ItemDto>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun ItemSource.toItemDto()")
        assertThat(content).contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `alias in MapFrom class literal resolves to underlying class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderSource(val ref: String)

            typealias AliasedOrderSource = OrderSource

            @com.blu3berry.kraft.mapping.MapFrom(AliasedOrderSource::class)
            data class OrderDto(val ref: String)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val file = generated.first().readText()

        assertThat(file).contains("fun OrderSource.toOrderDto()")
        assertThat(file).contains("ref = this.ref")
    }

    @Test
    fun `aliased enum property auto-derives enum mapping`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class StatusDto { ACTIVE, INACTIVE }
            enum class Status { ACTIVE, INACTIVE }

            typealias AliasedStatusDto = StatusDto

            data class UserDto(val status: AliasedStatusDto)
            data class User(val status: Status)

            @com.blu3berry.kraft.config.MapConfig(
                source = UserDto::class,
                target = User::class
            )
            object UserMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
