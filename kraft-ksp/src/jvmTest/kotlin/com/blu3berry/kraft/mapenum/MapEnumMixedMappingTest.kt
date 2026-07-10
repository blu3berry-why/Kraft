package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapEnumMixedMappingTest {

    @Test
    fun `@MapEnum auto-maps same-named entries and custom-maps renamed ones`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class PaymentState  { PAID, PENDING, FAILED }
            enum class PaymentStatus { PAID, AWAITING, ERROR }

            // PAID matches by name → auto-mapped
            // PENDING and FAILED differ → covered by fieldMappings
            @com.blu3berry.kraft.config.MapEnum(
                source = PaymentState::class,
                target   = PaymentStatus::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "PENDING", target = "AWAITING"),
                    com.blu3berry.kraft.config.FieldMapping(source = "FAILED",  target = "ERROR")
                ]
            )
            object PaymentMapping
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first { "EnumMapper" in it.name }.readText()

        assertThat(content).contains("fun PaymentState.toPaymentStatus()")
        assertThat(content).contains("PaymentState.PAID -> PaymentStatus.PAID")
        assertThat(content).contains("PaymentState.PENDING -> PaymentStatus.AWAITING")
        assertThat(content).contains("PaymentState.FAILED -> PaymentStatus.ERROR")
    }
}
