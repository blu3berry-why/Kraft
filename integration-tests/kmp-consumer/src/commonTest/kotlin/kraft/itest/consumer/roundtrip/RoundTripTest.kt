package kraft.itest.consumer.roundtrip

import kraft.itest.consumer.roundtrip.generated.toCustomer
import kraft.itest.consumer.roundtrip.generated.toCustomerDto
import kraft.itest.consumer.roundtrip.generated.toDense
import kraft.itest.consumer.roundtrip.generated.toInbox
import kraft.itest.consumer.roundtrip.generated.toInboxDto
import kraft.itest.consumer.roundtrip.generated.toPerson
import kraft.itest.consumer.roundtrip.generated.toPersonDto
import kraft.itest.consumer.roundtrip.generated.toPriority
import kraft.itest.consumer.roundtrip.generated.toPriorityDto
import kraft.itest.consumer.roundtrip.generated.toStatus
import kraft.itest.consumer.roundtrip.generated.toStatusDto
import kraft.itest.consumer.roundtrip.generated.toTicket
import kraft.itest.consumer.roundtrip.generated.toTicketDto
import kraft.itest.consumer.roundtrip.generated.toWideningDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Roadmap #18: runs the generated mappers instead of reading their source. Each case maps a
 * value, maps it back through the `@MapReverse` mapper, and asserts the result equals the input,
 * plus the forward value itself where a wrong-but-symmetric mapping (two swapped fields) would
 * otherwise round-trip cleanly.
 */
class RoundTripTest {

    private val home = Address("Main St 1", "1011")
    private val work = Address("Side St 2", "2022")
    private val homeDto = AddressDto("Main St 1", "1011")
    private val workDto = AddressDto("Side St 2", "2022")

    private val customer = Customer(
        id = TagId("c-1"),
        name = "Ada",
        nickname = "ada",
        parent = TagId("c-0"),
        address = home,
        billing = work,
        priority = Priority.HIGH,
        status = Status.DISABLED,
        previousStatus = Status.ACTIVE,
        createdAt = Millis(1_000),
        deletedAt = Millis(2_000),
        tags = listOf(TagId("a"), TagId("b"), TagId("a")),
        maybeTags = listOf(TagId("m")),
        tagSet = setOf(TagId("x"), TagId("y")),
        statusHistory = listOf(Status.ACTIVE, Status.DISABLED),
        timeline = listOf(Millis(1), Millis(2)),
        addresses = listOf(home, work),
        addressSet = setOf(home, work),
        byKind = mapOf("home" to home, "work" to work),
        byPriority = mapOf(Priority.LOW to listOf(home), Priority.HIGH to listOf(work, home)),
        nested = listOf(listOf(home), emptyList(), listOf(work, home)),
        maybeAddresses = listOf(work),
        maybeAddressSet = setOf(home),
        maybeByKind = mapOf("work" to work),
        sparse = listOf(home, null, work),
    )

    /** Every nullable property null, every collection empty. */
    private val sparseCustomer = customer.copy(
        nickname = null,
        parent = null,
        billing = null,
        previousStatus = null,
        deletedAt = null,
        tags = emptyList(),
        maybeTags = null,
        tagSet = emptySet(),
        statusHistory = emptyList(),
        timeline = emptyList(),
        addresses = emptyList(),
        addressSet = emptySet(),
        byKind = emptyMap(),
        byPriority = emptyMap(),
        nested = emptyList(),
        maybeAddresses = null,
        maybeAddressSet = null,
        maybeByKind = null,
        sparse = listOf(null),
    )

    @BeforeTest
    fun resetCounters() {
        TicketMapper.reset()
    }

    @Test
    fun customerForwardValues() {
        val dto = customer.toCustomerDto()
        assertEquals(
            CustomerDto(
                id = "c-1",
                name = "Ada",
                nickname = "ada",
                parent = "c-0",
                address = homeDto,
                billing = workDto,
                priority = PriorityDto.HIGH,
                status = StatusDto.INACTIVE,
                previousStatus = StatusDto.ACTIVE,
                createdAt = 1_000,
                deletedAt = 2_000,
                tags = listOf("a", "b", "a"),
                maybeTags = listOf("m"),
                tagSet = setOf("x", "y"),
                statusHistory = listOf(StatusDto.ACTIVE, StatusDto.INACTIVE),
                timeline = listOf(1L, 2L),
                addresses = listOf(homeDto, workDto),
                addressSet = setOf(homeDto, workDto),
                byKind = mapOf("home" to homeDto, "work" to workDto),
                byPriority = mapOf(PriorityDto.LOW to listOf(homeDto), PriorityDto.HIGH to listOf(workDto, homeDto)),
                nested = listOf(listOf(homeDto), emptyList(), listOf(workDto, homeDto)),
                maybeAddresses = listOf(workDto),
                maybeAddressSet = setOf(homeDto),
                maybeByKind = mapOf("work" to workDto),
                sparse = listOf(homeDto, null, workDto),
            ),
            dto,
        )
    }

    @Test
    fun customerRoundTrips() {
        assertEquals(customer, customer.toCustomerDto().toCustomer())
    }

    @Test
    fun customerWithNullsAndEmptiesRoundTrips() {
        val dto = sparseCustomer.toCustomerDto()
        assertEquals(null, dto.billing)
        assertEquals(null, dto.maybeAddresses)
        assertEquals(listOf<AddressDto?>(null), dto.sparse)
        assertEquals(sparseCustomer, dto.toCustomer())
    }

    @Test
    fun dtoRoundTripsTheOtherWay() {
        val dto = customer.toCustomerDto()
        assertEquals(dto, dto.toCustomer().toCustomerDto())
    }

    @Test
    fun enumsRoundTripEveryEntry() {
        Priority.entries.forEach { assertEquals(it, it.toPriorityDto().toPriority()) }
        Status.entries.forEach { assertEquals(it, it.toStatusDto().toStatus()) }
        assertEquals(StatusDto.INACTIVE, Status.DISABLED.toStatusDto())
        assertEquals(Status.DISABLED, StatusDto.INACTIVE.toStatus())
    }

    @Test
    fun nullableToNonNullCollectionsFallBackToEmpty() {
        val inbox = Inbox(messages = listOf(home), folders = mapOf("f" to work))
        assertEquals(InboxDto(listOf(homeDto), mapOf("f" to workDto)), inbox.toInboxDto())
        assertEquals(inbox, inbox.toInboxDto().toInbox())

        // null and empty collapse to the same DTO; the way back yields empty, not null (documented).
        val empty = Inbox(messages = null, folders = null)
        assertEquals(InboxDto(emptyList(), emptyMap()), empty.toInboxDto())
        assertEquals(Inbox(emptyList(), emptyMap()), empty.toInboxDto().toInbox())
    }

    @Test
    fun nonNullIntoNullableKeepsEveryValue() {
        val widening = Widening(
            id = TagId("w-1"),
            address = home,
            addresses = listOf(work),
            tags = listOf(TagId("t")),
        )
        assertEquals(
            WideningDto(
                id = "w-1",
                address = homeDto,
                addresses = listOf(workDto),
                tags = listOf("t"),
            ),
            widening.toWideningDto(),
        )
    }

    @Test
    fun nullableElementsAreDroppedIntoNonNullElements() {
        val sparse = Sparse(
            addresses = listOf(null, home, null, work),
            statuses = listOf(Status.DISABLED, null, Status.ACTIVE),
        )
        assertEquals(
            Dense(listOf(homeDto, workDto), listOf(StatusDto.INACTIVE, StatusDto.ACTIVE)),
            sparse.toDense(),
        )
    }

    @Test
    fun mapUsingConvertersRunInBothDirections() {
        val person = Person(birthYear = 1990, name = "Grace")
        assertEquals(PersonDto(age = 36, name = "Grace"), person.toPersonDto())
        assertEquals(person, person.toPersonDto().toPerson())
    }

    @Test
    fun mapConstantsFillTheirOwnDirection() {
        val ticket = Ticket(title = "bug", origin = "api")
        assertEquals(TicketDto(title = "bug", schema = 2, sequence = 1), ticket.toTicketDto())
        assertEquals(TicketDto(title = "bug", schema = 2, sequence = 2), ticket.toTicketDto())

        // `origin` is not on the DTO: the reverse mapper fills it from the constant.
        assertEquals(ticket, ticket.toTicketDto().toTicket())
        assertEquals("api", TicketDto(title = "t", schema = 9, sequence = 0).toTicket().origin)
    }
}
