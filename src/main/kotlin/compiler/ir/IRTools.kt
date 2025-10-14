package compiler.ir

interface IRTransformer {
    val transformPhiSourceLabels: Boolean

    fun startBlock(label: IRLabel)

    fun transformNode(node: IRNode): List<IRNode>

    fun transformLValue(value: IRVar): IRVar

    fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue

    fun transformLabel(label: IRLabel): IRLabel
}

abstract class BaseIRTransformer : IRTransformer {
    override val transformPhiSourceLabels: Boolean = false

    override fun startBlock(label: IRLabel) {}

    override fun transformNode(node: IRNode): List<IRNode> = listOf(node)

    override fun transformLValue(value: IRVar): IRVar = value

    override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue = value

    override fun transformLabel(label: IRLabel): IRLabel = label
}

abstract class SimpleIRTransformer : BaseIRTransformer() {
    abstract fun transformNodeSimple(node: IRNode): IRNode?

    override fun transformNode(node: IRNode): List<IRNode> {
        return transformNodeSimple(node)?.let { listOf(it) } ?: emptyList()
    }
}