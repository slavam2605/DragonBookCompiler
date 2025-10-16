package compiler.ir.optimization.inline

import compiler.ir.BaseIRTransformer
import compiler.ir.IRFunctionCall
import compiler.ir.IRJump
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRReturn
import compiler.ir.IRSource
import compiler.ir.IRValue
import compiler.ir.IRVar

internal class InlineRenamer(
    private val namesCollector: InlineNamesCollector,
    private val originalCallNode: IRFunctionCall,
    private val inlinedReturnLabel: IRLabel,
    private val returnValueSources: MutableList<IRSource>
) : BaseIRTransformer() {
    override val transformPhiSourceLabels: Boolean = true
    private lateinit var currentBlock: IRLabel

    override fun startBlock(label: IRLabel) {
        currentBlock = label
    }

    override fun transformLabel(label: IRLabel): IRLabel {
        return namesCollector.getNewIRLabel(label)
    }

    override fun transformLValue(value: IRVar): IRVar {
        return namesCollector.getNewIRVar(value)
    }

    override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
        if (value is IRVar) {
            return namesCollector.getNewIRVar(value)
        }
        return value
    }

    override fun transformNode(node: IRNode): List<IRNode> {
        if (node !is IRReturn) return listOf(node)

        if (originalCallNode.lvalue != null) {
            check(node.value != null)
            val newCurrentLabel = namesCollector.getNewIRLabel(currentBlock)
            returnValueSources.add(IRSource(newCurrentLabel, node.value))
        }

        return listOf(IRJump(inlinedReturnLabel))
    }
}