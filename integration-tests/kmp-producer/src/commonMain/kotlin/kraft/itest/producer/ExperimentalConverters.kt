@file:OptIn(ExperimentalProducerApi::class)

package kraft.itest.producer

import com.blu3berry.kraft.config.KraftConverter

/**
 * `@file:OptIn` converter over an experimental type. The consumer reaches it only as a
 * classpath `@KraftConverterDelegate` from this module's klib — the RC-3 configuration.
 */
@KraftConverter
fun TraceId.toTraceLabel(): String = "trace-$raw"

/**
 * Source class for the consumer's mapper. It lives here, not in the consumer, so the only
 * opt-in the consumer could ever see is the one carried across the module boundary: a
 * consumer-side `@file:OptIn` would be collected in-module and mask a propagation failure.
 */
data class TraceEvent(val name: String, val trace: TraceId)
