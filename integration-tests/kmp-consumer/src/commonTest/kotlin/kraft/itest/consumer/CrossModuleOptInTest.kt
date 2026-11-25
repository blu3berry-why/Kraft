package kraft.itest.consumer

import kraft.itest.producer.ExperimentalProducerApi
import kraft.itest.producer.TraceEvent
import kraft.itest.producer.TraceId
import kraft.itest.producer.generated.toTraceEventDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compiling `commonMain` proves the generated mapper carries the producer's opt-in marker
 * (the marker is `ERROR` level); `verifyCrossModuleOptIn` asserts the annotation text. This
 * test proves the mapper reaches the producer's converter through its delegate at runtime.
 */
class CrossModuleOptInTest {

    @OptIn(ExperimentalProducerApi::class)
    @Test
    fun mapperUsesOptInConverterAcrossModuleBoundary() {
        val event = TraceEvent(name = "login", trace = TraceId(7L))
        assertEquals(TraceEventDto(name = "login", trace = "trace-7"), event.toTraceEventDto())
    }
}
