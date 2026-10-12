package kraft.itest.producer

import kotlin.jvm.JvmInline

/** Consumed across the module boundary by the consumer's value-class mappers. */
@JvmInline
value class OrderId(val raw: Long)
