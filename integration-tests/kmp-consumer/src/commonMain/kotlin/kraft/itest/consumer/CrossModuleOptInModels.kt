package kraft.itest.consumer

import com.blu3berry.kraft.config.MapConfig
import kraft.itest.producer.TraceEvent

/**
 * Cross-module opt-in fixture (roadmap item 17). [TraceEvent.trace] is an experimental
 * `TraceId` and its `TraceId → String` converter is a `@file:OptIn` producer converter, so
 * the generated `TraceEvent.toTraceEventDto()` compiles only if the producer's opt-in marker
 * crosses the klib boundary onto it. Nothing in this module opts in on its own.
 */
data class TraceEventDto(val name: String, val trace: String)

@MapConfig(source = TraceEvent::class, target = TraceEventDto::class)
object TraceEventMapper
