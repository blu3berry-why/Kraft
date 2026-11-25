package kraft.itest.consumer.roundtrip

import com.blu3berry.kraft.config.FieldMapping
import com.blu3berry.kraft.config.KraftConverter
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapConstant
import com.blu3berry.kraft.config.MapEnum
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.config.MapUsing
import kotlin.jvm.JvmInline

// Round-trip fixtures (roadmap #18): every mapper here is executed by RoundTripTest, which maps a
// value, maps it back through the @MapReverse mapper and asserts the result equals the input.

// --- Enums -------------------------------------------------------------------------------------

/** Same entries on both sides and no @MapEnum: Kraft derives both directions by name. */
enum class Priority { LOW, NORMAL, HIGH }

enum class PriorityDto { LOW, NORMAL, HIGH }

/** One renamed entry, so this pair needs an explicit @MapEnum, reversed with @MapReverse. */
enum class Status { ACTIVE, DISABLED }

enum class StatusDto { ACTIVE, INACTIVE }

@MapReverse
@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [FieldMapping(source = "DISABLED", target = "INACTIVE")],
)
object StatusMapping

// --- Value class and @KraftConverter types -------------------------------------------------------

@JvmInline
value class TagId(val value: String)

/** Not a value class: crosses to `Long` only through the two @KraftConverter functions below. */
data class Millis(val raw: Long)

@KraftConverter
fun Millis.toEpochMillis(): Long = raw

@KraftConverter
fun Long.toMillis(): Millis = Millis(this)

// --- The aggregate: nested objects, collections, enums, value classes, converters --------------

data class Address(val street: String, val zip: String)

data class AddressDto(val street: String, val zip: String)

@Suppress("LongParameterList")
data class Customer(
    val id: TagId,
    val name: String,
    val nickname: String?,
    val parent: TagId?,
    val address: Address,
    val billing: Address?,
    val priority: Priority,
    val status: Status,
    val previousStatus: Status?,
    val createdAt: Millis,
    val deletedAt: Millis?,
    val tags: List<TagId>,
    val maybeTags: List<TagId>?,
    val tagSet: Set<TagId>,
    val statusHistory: List<Status>,
    val timeline: List<Millis>,
    val addresses: List<Address>,
    val addressSet: Set<Address>,
    val byKind: Map<String, Address>,
    val byPriority: Map<Priority, List<Address>>,
    val nested: List<List<Address>>,
    val maybeAddresses: List<Address>?,
    val maybeAddressSet: Set<Address>?,
    val maybeByKind: Map<String, Address>?,
    val sparse: List<Address?>,
)

@Suppress("LongParameterList")
data class CustomerDto(
    val id: String,
    val name: String,
    val nickname: String?,
    val parent: String?,
    val address: AddressDto,
    val billing: AddressDto?,
    val priority: PriorityDto,
    val status: StatusDto,
    val previousStatus: StatusDto?,
    val createdAt: Long,
    val deletedAt: Long?,
    val tags: List<String>,
    val maybeTags: List<String>?,
    val tagSet: Set<String>,
    val statusHistory: List<StatusDto>,
    val timeline: List<Long>,
    val addresses: List<AddressDto>,
    val addressSet: Set<AddressDto>,
    val byKind: Map<String, AddressDto>,
    val byPriority: Map<PriorityDto, List<AddressDto>>,
    val nested: List<List<AddressDto>>,
    val maybeAddresses: List<AddressDto>?,
    val maybeAddressSet: Set<AddressDto>?,
    val maybeByKind: Map<String, AddressDto>?,
    val sparse: List<AddressDto?>,
)

@MapReverse
@MapConfig(source = Customer::class, target = CustomerDto::class)
object CustomerMapper

// --- Asymmetric nullability that still has a reverse: nullable <-> non-null collections --------

/**
 * `T?` on the domain side, non-null on the DTO side: forward falls back to empty, reverse lifts.
 * A value-class element (`Set<TagId>?` into `Set<String>`) is left out: it fails with KRAFT-E005
 * instead of falling back to empty (roadmap #45).
 */
data class Inbox(
    val messages: List<Address>?,
    val folders: Map<String, Address>?,
)

data class InboxDto(
    val messages: List<AddressDto>,
    val folders: Map<String, AddressDto>,
)

@MapReverse
@MapConfig(source = Inbox::class, target = InboxDto::class)
object InboxMapper

// --- One-way nullable permutations (their reverse would be a compile-time error by design) -----

/**
 * Non-null source into nullable target. Only the kinds Kraft accepts today: the same widening
 * for a same-type property, an enum or a @KraftConverter pair fails with KRAFT-E005 (roadmap #44).
 */
data class Widening(
    val id: TagId,
    val address: Address,
    val addresses: List<Address>,
    val tags: List<TagId>,
)

data class WideningDto(
    val id: String?,
    val address: AddressDto?,
    val addresses: List<AddressDto>?,
    val tags: List<String>?,
)

@MapConfig(source = Widening::class, target = WideningDto::class)
object WideningMapper

/** Nullable elements into non-null elements: dropped by `mapNotNull`. */
data class Sparse(val addresses: List<Address?>, val statuses: List<Status?>)

data class Dense(val addresses: List<AddressDto>, val statuses: List<StatusDto>)

@MapConfig(source = Sparse::class, target = Dense::class)
object SparseMapper

// --- @MapUsing and @MapConstant ------------------------------------------------------------------

const val CURRENT_YEAR = 2026

data class Person(val birthYear: Int, val name: String)

data class PersonDto(val age: Int, val name: String)

@MapReverse
@MapConfig(source = Person::class, target = PersonDto::class)
object PersonMapper {
    @MapUsing(source = "birthYear", target = "age")
    fun toAge(birthYear: Int): Int = CURRENT_YEAR - birthYear

    @MapUsing(source = "age", target = "birthYear")
    fun toBirthYear(age: Int): Int = CURRENT_YEAR - age
}

/** `schema` exists only on the DTO, `origin` only on the domain side. */
data class Ticket(val title: String, val origin: String)

data class TicketDto(val title: String, val schema: Int, val sequence: Long)

@MapReverse
@MapConfig(source = Ticket::class, target = TicketDto::class)
object TicketMapper {
    private var counter = 0L

    /** AUTO direction: the target (TicketDto) has `schema`, so this fills the forward mapper. */
    @MapConstant
    val schema = 2

    /** AUTO direction: only Ticket has `origin`, so this fills the reverse mapper. */
    @MapConstant(target = "origin")
    val defaultOrigin = "api"

    /** A function constant runs on every mapping. */
    @MapConstant(target = "sequence")
    fun nextSequence(): Long = ++counter

    fun reset() {
        counter = 0L
    }
}
