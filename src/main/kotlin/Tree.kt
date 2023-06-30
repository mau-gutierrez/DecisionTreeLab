import java.time.*
import java.time.format.DateTimeFormatter

const val LEAF_NODE = "LeafNode"

sealed class Node {
    abstract fun evaluate(inputs: LoaderDTO): TreeResult
}

data class BranchNode(
    val propertyName: String, val branches: Map<Boolean, Node>
) : Node() {
    override fun evaluate(inputs: LoaderDTO): TreeResult {


        val propertyValue = inputs.getPropertyValue(propertyName)
        val nextNode = branches[propertyValue] ?: throw IllegalStateException("Invalid property value: $propertyValue")

//      val nodelabel = nextNode.toString()
//      if (nodelabel.contains(LEAF_NODE)){
//           return finalizeTree(nextNode)
//       }
        return nextNode.evaluate(inputs)
    }
}

data class LeafNode(val label: VendorSatus) : Node() {
    override fun evaluate(inputs: LoaderDTO): TreeResult {
        return TreeResult(id = inputs.id, status = label)
    }
}

class DecisionTree {
    private val tree: Node = BranchNode(
        "IsOpen", mapOf(
            true to BranchNode(
                "IsClosedForHurrierDelay", mapOf(
                    true to BranchNode(
                        "IsPickup", mapOf(
                            true to LeafNode(VendorSatus.PICK_UP_ONLINE),
                            false to LeafNode(VendorSatus.WITHOUT_DELIVERY)
                        )
                    ), false to BranchNode(
                        "IsCloseEventActive", mapOf(
                            true to BranchNode(
                                "IsPickup", mapOf(
                                    true to LeafNode(VendorSatus.PICK_UP_ONLINE),
                                    false to LeafNode(VendorSatus.WITHOUT_DELIVERY)
                                )
                            ), false to BranchNode(
                                "IsShrinkEventActive", mapOf(
                                    true to BranchNode(
                                        "IsPickup", mapOf(
                                            true to LeafNode(VendorSatus.PICK_UP_ONLINE),
                                            false to LeafNode(VendorSatus.WITHOUT_DELIVERY)
                                        )
                                    ), false to BranchNode(
                                        "IsDelivery", mapOf(
                                            true to LeafNode(VendorSatus.DELIVERY_ONLINE),
                                            false to BranchNode(
                                                "IsPickup", mapOf(
                                                    true to LeafNode(VendorSatus.PICK_UP_ONLINE),
                                                    false to LeafNode(VendorSatus.OPEN_DEFAULT)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ), false to BranchNode(
                "OutsideWorkingHours", mapOf(
                    true to BranchNode(
                        "OpensLater", mapOf(
                            true to BranchNode(
                                "PreOrder", mapOf(
                                    true to LeafNode(VendorSatus.PROGRAM_ORDER_OPENS_AT),
                                    false to LeafNode(VendorSatus.OPENS_AT)
                                )
                            ), false to LeafNode(VendorSatus.CLOSED)
                        )
                    ), false to BranchNode(
                        "IsScheduleVariation", mapOf(
                            true to BranchNode(
                                "OpensLater", mapOf(
                                    true to LeafNode(VendorSatus.CLOSED_TEMPORARILY),
                                    false to LeafNode(VendorSatus.CLOSED)
                                )
                            ), false to LeafNode(VendorSatus.CLOSED)
                        )
                    )
                )
            )
        )
    )

    fun traverseTree(inputs: List<LoaderDTO>): List<TreeResult> {
        val treeResultsList = mutableListOf<TreeResult>()
        inputs.forEach {
            val treeResult = tree.evaluate(it)
            treeResultsList.add(treeResult)
        }
        return treeResultsList
    }
}

data class LoaderDTO(
    val id: Long,
    val isOpen: Boolean,
    val isDelivery: Boolean,
    val isPickup: Boolean,
    val opensLater: Boolean,
    val outsideWorkingHours: Boolean,
    val isShrinkEvent: Boolean,
    val isCloseEvent: Boolean,
    val isScheduleVariation: Boolean,
    val isPreOrder: Boolean,
    val isClosedForHurrierDelay: Boolean
) {
    fun getPropertyValue(propertyName: String): Boolean {
        return when (propertyName) {
            "IsOpen" -> isOpen
            "IsDelivery" -> isDelivery
            "IsPickup" -> isPickup
            "OpensLater" -> opensLater
            "OutsideWorkingHours" -> outsideWorkingHours
            "IsShrinkEventActive" -> isShrinkEvent
            "IsCloseEventActive" -> isCloseEvent
            "IsScheduleVariation" -> isScheduleVariation
            "PreOrder" -> isPreOrder
            "IsClosedForHurrierDelay" -> isClosedForHurrierDelay

            else -> throw IllegalStateException("Invalid property name: $propertyName")
        }
    }
}

enum class DeliveryStatus {
    OPEN, CLOSED, NOT_AVAILABLE
}

enum class PickUpStatus {
    OPEN, CLOSED, NOT_AVAILABLE
}

enum class DeliveryReason {
    OUTSIDE_WORKING_HOURS, SCHEDULE_VARIATION, HIGH_DEMAND, CUSTOMER_LOCATION_DISABLED, UNKNOWN
}

enum class PickUpReason {
    OUTSIDE_WORKING_HOURS, SCHEDULE_VARIATION, UNKNOWN
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class UntilVendor(
    val dateVendor: String, val timeVendor: String, val isToday: Boolean
)

class ActionType {

    companion object {
        const val CLOSE = "CLOSE"
        const val SHRINK = "SHRINK"

        fun isClose(status: String?): Boolean = CLOSE == status
        fun isShrink(status: String?): Boolean = SHRINK == status
    }
}

fun getUntilVendor(until: String?, zoneDateTime: ZonedDateTime = ZonedDateTime.now()): UntilVendor? {

    if (until.isNullOrEmpty()) {
        return null
    }
    val dateTime = ZonedDateTime.parse(until, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    val timeFormatter = timeFormatter
    val timeVendor = dateTime.toLocalTime().format(timeFormatter)

    return UntilVendor(
        dateTime.toLocalDate().toString(), timeVendor, isSameDay(dateTime, zoneDateTime)
    )
}

fun isSameDay(dateTime: ZonedDateTime, zoneDateTime: ZonedDateTime) =
    zoneDateTime.withZoneSameInstant(dateTime.zone).toLocalDate().isEqual(dateTime.toLocalDate())

private fun validateOpensLater(isOpen: Boolean, until: String?): Boolean {
    if (isOpen) {
        return false
    }

    val untilVendor = getUntilVendor(until)
    if (untilVendor != null) {
        val now = ZonedDateTime.now()

        if (untilVendor.isToday) {
            val untilTime = LocalTime.parse(untilVendor.timeVendor)
            val currentTime = now.toLocalTime()

            // Check if the until time is later than the current time
            if (untilTime.isAfter(currentTime)) {
                return true // Vendor opens later
            }
        }
    }

    return false

}

//private fun validateOutsideWorkingHours(isOpen: Boolean): Boolean {
//    if (isOpen) {
//        return false
//    }
//
//    if (delivery != null) {
//        return when {
//            delivery.status == DeliveryStatus.CLOSED && delivery.until != null -> {
//                val untilDate = LocalDateTime.parse(delivery.until, DateTimeFormatter.ISO_DATE_TIME)
//                val currentDateTime = LocalDateTime.now(ZoneOffset.UTC)
//                currentDateTime.isBefore(untilDate)
//            }
//
//            else -> false
//        }
//    }
//    return false
//}


class InputLoader {
    fun loadInputs(vendorInputsDTO: List<VendorOfferStateDTO>): List<LoaderDTO> {
        val loaderList = mutableListOf<LoaderDTO>()
        vendorInputsDTO.forEach {
            val isModeDelivery = it.vendorInfo.delivery.containsMode(OfferTypes.DELIVERY.name)
            val isOwnDelivery = VendorDeliveryType.isOwnDelivery(it.vendorInfo.delivery.type)

            val isOpen = (isModeDelivery && it.offerVendor.state.status == DeliveryStatus.OPEN.name)
            val isDelivery = isOpen && isOwnDelivery
            val isPickup = (it.vendorInfo.delivery.containsMode(OfferTypes.PICK_UP.name))
            val opensLater = validateOpensLater(isOpen, it.offerVendor.state.until)
            //val outsideWorkingHours =
            val isShrinkEvent = ActionType.isShrink(it.offerVendor.action?.action)
            val isCloseEvent = ActionType.isClose(it.offerVendor.action?.action)
            val isScheduleVariation = it.offerVendor.state.activeScheduleVariationId == 1
            val preOrder = it.vendorInfo.acceptsPreOrder
            val isClosedForHurrierDelay = it.vendorToCloseHurrierDelay

            val loaderDTO = LoaderDTO(
                id = it.id,
                isOpen = isOpen,
                isDelivery = isDelivery,
                isPickup = isPickup,
                opensLater = opensLater,
                outsideWorkingHours = true,
                isShrinkEvent = isShrinkEvent,
                isCloseEvent = isCloseEvent,
                isScheduleVariation = isScheduleVariation,
                isPreOrder = preOrder,
                isClosedForHurrierDelay = isClosedForHurrierDelay,
            )
            loaderList.add(loaderDTO)
        }
        return loaderList
    }
}

data class VendorOfferStateDTO(
    val id: Long,
    val vendorInfo: VendorServiceVendorInfoDTO,
    val offerVendor: OfferVendor,
    var vendorToCloseHurrierDelay: Boolean
)

data class VendorServiceVendorInfoDTO(
    val id: Long,
    val acceptsPreOrder: Boolean,
    val business: VendorServiceBusinessDTO,
    val address: VendorServiceAddressDTO?,
    val delivery: VendorServiceDeliveryDTO,
    val withDefaultData: Boolean = false,
    val isTest: Boolean = false
)

data class OfferVendor(
    val vendorId: Long, val action: VendorAction?, val geoStatus: String?, val state: VendorStateLaas
)

data class VendorServiceBusinessDTO(
    val types: Set<String>, val isDarkstore: Boolean
)

data class VendorServiceDeliveryDTO(
    val type: String, // OWN_DELIVERY, VENDOR_DELIVERY
    val modes: List<String>? // DELIVERY, PICK_UP,DINE_IN
) {
    fun containsMode(mode: String): Boolean {
        return modes?.contains(mode) == true
    }
}

class VendorDeliveryType {

    companion object {
        const val VENDOR_DELIVERY = "VENDOR_DELIVERY"

        const val OWN_DELIVERY = "OWN_DELIVERY"
        fun isVendorDelivery(type: String?): Boolean = VENDOR_DELIVERY == type
        fun isOwnDelivery(type: String?): Boolean = OWN_DELIVERY == type
    }
}

data class VendorServiceAddressDTO(
    val latitude: Double, val longitude: Double
)

data class VendorStateLaas(
    val vendorId: Long = 0,
    val status: String = "", // OPEN, CLOSED
    val reason: String? = null, // OUTSIDE_WORKING_HOURS, para cualquier otro caso asignar(SCHEDULE_VARIATION)
    var until: String? = null,
    val activeScheduleVariationId: Int? = null,
)

data class VendorAction(
    val action: String, val value: String?
)

data class Pickup(
    val status: PickUpStatus, val reason: String?, val until: String?
)

data class TreeResult(
    val id: Long,
    val status: VendorSatus,
)

enum class OfferTypes {
    DELIVERY, PICK_UP, DINE_IN
}

enum class VendorSatus {
    DELIVERY_ONLINE, PICK_UP_ONLINE, PROGRAM_ORDER_OPENS_AT, OPENS_AT, CDU_DEFAULT, DINE_IN_ONLINE, PRE_ORDER_ONLINE, CLOSED_TEMPORARILY, WITHOUT_DELIVERY, OPEN_DEFAULT, CLOSED, CLOSED_DEFAULT
}

fun main() {
//    val vendorInfo1 = VendorServiceVendorInfoDTO(
//        id = 1,
//        acceptsPreOrder = true,
//        business = VendorServiceBusinessDTO(setOf("type1", "type2"), isDarkstore = false),
//        address = VendorServiceAddressDTO(latitude = 12.345, longitude = 67.890),
//        delivery = VendorServiceDeliveryDTO(type = "OWN_DELIVERY", modes = listOf("DELIVERY", "PICK_UP"))
//    )

    val vendorInfo2 = VendorServiceVendorInfoDTO(
        id = 2,
        acceptsPreOrder = true,
        business = VendorServiceBusinessDTO(setOf("type1", "type2"), isDarkstore = false),
        address = VendorServiceAddressDTO(latitude = 12.345, longitude = 67.890),
        delivery = VendorServiceDeliveryDTO(type = "OWN_DELIVERY", modes = listOf("DINE_IN", "DELIVERY"))
    )

//    val vendorStateLaas1 = VendorStateLaas(
//        vendorId = 1,
//        status = "OPEN",
//        reason = "reason1",
//        until = "2023-06-30T12:00:00+02:00",
//        activeScheduleVariationId = 0
//    )

    val vendorStateLaas2 = VendorStateLaas(
        vendorId = 2,
        status = "OPEN",
        reason = "reason1",
        until = "2023-06-30T12:00:00+02:00",
        activeScheduleVariationId = 0
    )

//    val offerVendor1 = OfferVendor(
//        vendorId = 1,
//        action = VendorAction(action = "action1", value = "value1"),
//        geoStatus = "geoStatus1",
//        state = vendorStateLaas1
//    )

    val offerVendor2 = OfferVendor(
        vendorId = 2,
        action = VendorAction(action = "action1", value = "value1"),
        geoStatus = "geoStatus1",
        state = vendorStateLaas2
    )

// Create a list of VendorOfferStateDTO
    val vendorOfferStateList = listOf(
//        VendorOfferStateDTO(
//            id = 1, vendorInfo = vendorInfo1, offerVendor = offerVendor1, vendorToCloseHurrierDelay = true
//        ),
        VendorOfferStateDTO(
            id = 2, vendorInfo = vendorInfo2, offerVendor = offerVendor2, vendorToCloseHurrierDelay = false
        )
    )
    val inputLoader = InputLoader()

    val inputs = inputLoader.loadInputs(vendorOfferStateList)

    val decisionTree = DecisionTree()
    val treeResult = decisionTree.traverseTree(inputs)
    println(treeResult)
}
//[TreeResult(id=1, status=PICK_UP_ONLINE), TreeResult(id=2, status=WITHOUT_DELIVERY)]