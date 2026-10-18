package kraft.itest.consumer

import kraft.itest.consumer.generated.toFlatDst
import kotlin.test.Test
import kotlin.test.assertEquals

/** Flat config rules and an untyped @MapConstant resolve in commonMain on every target. */
class FlatConfigTest {

    private val dst = FlatSrc(fullName = "Ada", secret = "x").toFlatDst()

    @Test
    fun renamesField() = assertEquals("Ada", dst.name)

    @Test
    fun ignoredFieldKeepsDefault() = assertEquals("none", dst.secret)

    @Test
    fun inferredConstant() = assertEquals("HUF", dst.currency)
}
