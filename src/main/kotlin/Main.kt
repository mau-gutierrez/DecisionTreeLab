//sealed class Node {
//    abstract fun evaluate(inputs: Map<String, Boolean>): String
//}
//
//data class BranchNode(
//    val propertyName: String,
//    val branches: Map<Boolean, Node>
//) : Node() {
//    override fun evaluate(inputs: Map<String, Boolean>): String {
//        val propertyValue = inputs[propertyName] ?: throw IllegalStateException("Invalid property name: $propertyName")
//        val nextNode = branches[propertyValue] ?: throw IllegalStateException("Invalid property value: $propertyValue")
//        return nextNode.evaluate(inputs)
//    }
//}
//
//data class LeafNode(val label: String) : Node() {
//    override fun evaluate(inputs: Map<String, Boolean>): String {
//        return label
//    }
//}
//
//class DecisionTree {
//    private val tree: Node = BranchNode(
//        "IsOpen",
//        mapOf(
//            true to BranchNode(
//                "IsDelivery",
//                mapOf(
//                    true to LeafNode("Delivery_online"),
//                    false to BranchNode(
//                        "IsPickup",
//                        mapOf(
//                            true to LeafNode("Pickup_Online"),
//                            false to LeafNode("CDU_Default")
//                        )
//                    )
//                )
//            ),
//            false to LeafNode("CLOSED")
//        )
//    )
//
//    fun traverseTree(inputs: Map<String, Boolean>): String {
//        return tree.evaluate(inputs)
//    }
//}
//
//fun main() {
//    val inputs = mapOf(
//        "IsOpen" to true,
//        "IsDelivery" to false,
//        "IsPickup" to false
//    )
// //tener posibilidad de mapear inputs/ param de la oofer a variables para armar el arbol de decisiones
//    val decisionTree = DecisionTree()
//    val result = decisionTree.traverseTree(inputs)
//    println(result)
//}
