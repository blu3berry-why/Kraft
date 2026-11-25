package kraft.itest.producer

/**
 * Opt-in marker for the cross-module opt-in fixture (roadmap item 17). `ERROR` level, so a
 * consumer mapper that touches [TraceId] without opting in fails to compile instead of warning.
 */
@RequiresOptIn(
    message = "Experimental producer API, used to test cross-module opt-in propagation.",
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ExperimentalProducerApi

/** Experimental type: every use, including reading a property of this type, needs the opt-in. */
@ExperimentalProducerApi
class TraceId(val raw: Long)
