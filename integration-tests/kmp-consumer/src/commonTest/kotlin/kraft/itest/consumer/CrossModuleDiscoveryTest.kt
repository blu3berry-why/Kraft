package kraft.itest.consumer

import kraft.itest.consumer.generated.toDst
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compiling this module at all proves cross-module discovery: the `Int → String`
 * converter lives in `:integration-tests:kmp-producer` and reaches this module only
 * through its published `@KraftConverterDelegate`. This test additionally proves the
 * delegate trampolines to the producer's real implementation at runtime.
 */
class CrossModuleDiscoveryTest {

    @Test
    fun mapperUsesProducerConverterAcrossModuleBoundary() {
        assertEquals(Dst(count = "n=42"), Src(count = 42).toDst())
    }
}
