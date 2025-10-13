package compiler.ir.optimization

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ssa.SSAControlFlowGraph

class EqualityPropagation(private val cfg: SSAControlFlowGraph) {
    private val assignMap = mutableMapOf<IRVar, IRVar>()

    fun invoke(): SSAControlFlowGraph {
        cfg.blocks.forEach { (_, block) ->
            block.irNodes.forEach { node ->
                when (node) {
                    is IRAssign if node.right is IRVar -> {
                        assignMap[node.lvalue] = node.right
                    }
                    is IRPhi if node.sources.size == 1 -> {
                        val sourceValue = node.sources[0].value
                        if (sourceValue is IRVar) {
                            assignMap[node.lvalue] = sourceValue
                        }
                    }
                    else -> { /* do nothing */ }
                }
            }
        }
        makeTransitiveClosure(assignMap)

        if (assignMap.isEmpty()) return cfg
        return cfg.transform(object : BaseIRTransformer() {
            override fun transformNode(node: IRNode): IRNode? {
                if (node.lvalue in assignMap) {
                    return null
                }
                return node
            }

            override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
                assignMap[value]?.let { return it }
                return value
            }
        }) as SSAControlFlowGraph
    }

    private fun makeTransitiveClosure(assignMap: MutableMap<IRVar, IRVar>) {
        assignMap.keys.forEach { fromVar ->
            var runner = assignMap[fromVar]!!
            while (runner in assignMap) {
                runner = assignMap[runner]!!
            }
            assignMap[fromVar] = runner
        }
    }
}