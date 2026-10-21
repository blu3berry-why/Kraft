@file:Suppress("DEPRECATION")

package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.config.KraftConverterDelegate
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapConstant
import com.blu3berry.kraft.config.MapEnum
import com.blu3berry.kraft.config.MapIgnoreField
import com.blu3berry.kraft.config.MapUsing
import com.blu3berry.kraft.mapping.MapNested
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.getEnumArgOrDefault
import com.blu3berry.kraft.processor.util.getKClassArgOrNull
import com.blu3berry.kraft.processor.util.getStringArgOrDefault
import com.blu3berry.kraft.processor.util.getStringArgOrNull
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSValueArgument
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * Covers `argument-defaults`: omitted defaulted arguments fall back to the declared
 * default silently, and missing required arguments get a kind-appropriate example.
 *
 * KSP omits defaulted arguments from [KSAnnotation.arguments] in KMP metadata
 * compilations, which `TestKspRunner` (JVM) does not exercise, so the annotation is
 * synthesised here with a missing argument. A compile test cannot reach the missing
 * required-argument path either: the Kotlin frontend rejects the source first.
 */
class AnnotationArgumentDefaultsTest {

    private val symbol: KSNode = proxy()

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(
        crossinline handler: (String) -> Any? = { error("unexpected call to $it") }
    ): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        handler(method.name)
    } as T

    private fun name(value: String): KSName = proxy { if (it == "asString") value else error(it) }

    private fun argument(argName: String, argValue: Any?): KSValueArgument = proxy {
        when (it) {
            "getName" -> name(argName)
            "getValue" -> argValue
            else -> error(it)
        }
    }

    private fun annotation(vararg args: Pair<String, Any?>): KSAnnotation = proxy {
        if (it == "getArguments") args.map { (n, v) -> argument(n, v) } else error(it)
    }

    @Test
    fun `an omitted defaulted String yields its default and logs nothing`() {
        val logger = RecordingKspLogger()
        val ann = annotation(KraftKspConstants.ARG_TARGET to "inputs")

        val source = ann.getStringArgOrDefault(
            KraftKspConstants.ARG_SOURCE,
            KraftKspConstants.DEFAULT_MAP_USING_SOURCE,
            logger,
            symbol,
            KraftKspConstants.FQ_MAP_USING
        )

        assertThat(source).isEqualTo("")
        assertThat(logger.entries).isEmpty()
    }

    @Test
    fun `an omitted defaulted enum yields AUTO and logs nothing`() {
        val logger = RecordingKspLogger()
        val ann = annotation(KraftKspConstants.ARG_SOURCE to "id", KraftKspConstants.ARG_TARGET to "id")

        val direction = ann.getEnumArgOrDefault(
            KraftKspConstants.ARG_DIRECTION,
            KraftKspConstants.DEFAULT_MAP_USING_DIRECTION,
            logger,
            symbol,
            KraftKspConstants.FQ_MAP_USING
        )

        assertThat(ConverterDirection.valueOf(direction)).isEqualTo(ConverterDirection.AUTO)
        assertThat(logger.entries).isEmpty()
    }

    @Test
    fun `a present but mistyped defaulted argument is still reported`() {
        val logger = RecordingKspLogger()
        val ann = annotation(KraftKspConstants.ARG_SOURCE to 42)

        val source = ann.getStringArgOrDefault(
            KraftKspConstants.ARG_SOURCE, "", logger, symbol, KraftKspConstants.FQ_MAP_USING
        )

        assertThat(source).isEqualTo("")
        assertThat(logger.entries.single().severity).isEqualTo("error")
    }

    @Test
    fun `a missing String argument is reported with a String example and no class literal`() {
        val logger = RecordingKspLogger()
        val ann = annotation(KraftKspConstants.ARG_SOURCE to "id")

        val value = ann.getStringArgOrNull(
            KraftKspConstants.ARG_TARGET, logger, symbol, KraftKspConstants.FQ_MAP_USING
        )

        assertThat(value).isNull()
        val message = logger.entries.single().message
        assertThat(message).contains("Missing required argument 'target' in @MapUsing.")
        assertThat(message).contains("@MapUsing(target = \"propertyName\")")
        assertThat(message).doesNotContain("::class")
    }

    @Test
    fun `a missing KClass argument is reported with a class literal example`() {
        val logger = RecordingKspLogger()
        val ann = annotation(KraftKspConstants.ARG_TARGET to "ignored")

        val value = ann.getKClassArgOrNull(
            KraftKspConstants.ARG_SOURCE, logger, symbol, KraftKspConstants.FQ_MAP_CONFIG
        )

        assertThat(value).isNull()
        val message = logger.entries.single().message
        assertThat(message).contains("Missing required argument 'source' in @MapConfig.")
        assertThat(message).contains("@MapConfig(source = MyType::class)")
    }

    @Suppress("DEPRECATION")
    @Test
    fun `every DEFAULT constant equals the default declared on the annotation`() {
        assertThat(MapUsing(target = "x").source)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_USING_SOURCE)
        assertThat(MapUsing(target = "x").direction.name)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_USING_DIRECTION)
        assertThat(MapIgnoreField(name = "x").direction.name)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_IGNORE_FIELD_DIRECTION)
        val config = MapConfig(source = Any::class, target = Any::class)
        assertThat(config.aliasEmitMode.name)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_CONFIG_ALIAS_EMIT_MODE)
        assertThat(config.useGlobalConverters)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_CONFIG_USE_GLOBAL_CONVERTERS)
        assertThat(MapEnum(source = Any::class, target = Any::class).aliasEmitMode.name)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_ENUM_ALIAS_EMIT_MODE)
        assertThat(MapNested().sourceName)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_NESTED_SOURCE_NAME)
        assertThat(KraftConverterDelegate().moduleId)
            .isEqualTo(KraftKspConstants.DEFAULT_KRAFT_CONVERTER_DELEGATE_MODULE_ID)
        assertThat(MapConstant().target)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_CONSTANT_TARGET)
        assertThat(MapConstant().direction.name)
            .isEqualTo(KraftKspConstants.DEFAULT_MAP_CONSTANT_DIRECTION)
    }
}
