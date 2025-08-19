package compiler.ir.optimization

import compiler.ir.*
import compiler.ir.cfg.ssa.SSAControlFlowGraph

class ConstantPropagation {
    sealed interface SSCPValue {
        data class Value(val value: Long) : SSCPValue
        object Any : SSCPValue
    }

    val values = mutableMapOf<IRVar, SSCPValue>()

    fun run(cfg: SSAControlFlowGraph): SSAControlFlowGraph {
        val worklist = mutableListOf<IRVar>()
        val usages = mutableMapOf<IRVar, MutableSet<IRNode>>()

        fun withIntValues(vararg values: SSCPValue, block: (List<Long>) -> Long): SSCPValue {
            return SSCPValue.Value(block(values.map { (it as SSCPValue.Value).value }))
        }

        fun withBoolValues(vararg values: SSCPValue, block: (List<Boolean>) -> Boolean): SSCPValue.Value {
            val args = values.map {
                val intValue = (it as SSCPValue.Value).value
                check(intValue == 0L || intValue == 1L) {
                    "Boolean values must be either 0 or 1, got $intValue"
                }
                intValue == 1L
            }
            val result = block(args)
            return SSCPValue.Value(if (result) 1L else 0L)
        }

        fun withIntValuesBoolResult(vararg values: SSCPValue, block: (List<Long>) -> Boolean): SSCPValue.Value {
            val intResult = block(values.map { (it as SSCPValue.Value).value })
            return SSCPValue.Value(if (intResult) 1L else 0L)
        }

        fun IRNode.evaluate(): SSCPValue {
            val rValues = rvalues().map {
                when (it) {
                    is IRVar -> values[it] ?: SSCPValue.Any
                    is IRInt -> SSCPValue.Value(it.value)
                }
            }
            return when (this) {
                is IRAssign -> rValues[0]
                is IRBinOp -> {
                    if (rValues.any { it == SSCPValue.Any }) {
                        return when {
                            op == IRBinOpKind.SUB && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(0)
                            op == IRBinOpKind.MUL && rValues.any { it == SSCPValue.Value(0) } -> SSCPValue.Value(0)
                            op == IRBinOpKind.MOD && rValues[1] == SSCPValue.Value(1) -> SSCPValue.Value(0)
                            op in equalComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(1)
                            op in notEqualComparisonOps && rvalues()[0] == rvalues()[1] -> SSCPValue.Value(0)
                            else -> SSCPValue.Any
                        }
                    }
                    when (op) {
                        IRBinOpKind.ADD -> withIntValues(rValues[0], rValues[1]) { it[0] + it[1] }
                        IRBinOpKind.SUB -> withIntValues(rValues[0], rValues[1]) { it[0] - it[1] }
                        IRBinOpKind.MUL -> withIntValues(rValues[0], rValues[1]) { it[0] * it[1] }
                        IRBinOpKind.DIV -> withIntValues(rValues[0], rValues[1]) { it[0] / it[1] }
                        IRBinOpKind.MOD -> withIntValues(rValues[0], rValues[1]) { it[0] % it[1] }
                        IRBinOpKind.EQ  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] == it[1] }
                        IRBinOpKind.NEQ -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] != it[1] }
                        IRBinOpKind.GT  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] > it[1] }
                        IRBinOpKind.GE  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] >= it[1] }
                        IRBinOpKind.LT  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] < it[1] }
                        IRBinOpKind.LE  -> withIntValuesBoolResult(rValues[0], rValues[1]) { it[0] <= it[1] }
                    }
                }
                is IRNot -> {
                    if (rValues[0] == SSCPValue.Any) return SSCPValue.Any
                    withBoolValues(rValues[0]) { !it[0] }
                }
                is IRPhi -> {
                    if (rValues.any { it == SSCPValue.Any }) return SSCPValue.Any
                    val constants = rValues.map { (it as SSCPValue.Value).value }
                    if (constants.all { it == constants[0] })
                        SSCPValue.Value(constants[0])
                    else
                        SSCPValue.Any
                }
                is IRJump, is IRJumpIfTrue -> {
                    error("Cannot evaluate node without lvalues: $this")
                }
            }
        }

        fun IRNode.evaluateSafe(): SSCPValue {
            return try {
                evaluate()
            } catch (_: ArithmeticException) {
                SSCPValue.Any
            }
        }

        cfg.blocks.forEach { (_, block) ->
            block.irNodes.forEach { irNode ->
                // Initialize all known constant values and form the initial worklist
                irNode.lvalue?.let { lVar ->
                    val value = irNode.evaluateSafe()
                    if (value is SSCPValue.Value) {
                        values[lVar] = value
                        worklist.add(lVar)
                    }
                }

                // Fill in the usages map
                irNode.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                    val rVarUsages = usages.getOrPut(rVar) { mutableSetOf() }
                    rVarUsages.add(irNode)
                }
            }
        }

        while (worklist.isNotEmpty()) {
            val irVar = worklist.removeLast()
            usages[irVar]?.forEach { irNode ->
                irNode.lvalue?.let { nodeResult ->
                    val oldValue = values[nodeResult] ?: SSCPValue.Any
                    val newValue = irNode.evaluateSafe()
                    if (oldValue != newValue) {
                        check(oldValue is SSCPValue.Any)
                        values[nodeResult] = newValue
                        worklist.add(nodeResult)
                    }
                }
            }
        }

        // to -> setOf(from)
        val removedJumps = mutableMapOf<IRLabel, MutableSet<IRLabel>>()
        val transformedJumps = mutableMapOf<IRJumpNode, IRJumpNode>()
        cfg.blocks.forEach { (fromLabel, fromBlock) ->
            fromBlock.irNodes.filterIsInstance<IRJumpIfTrue>().forEach { jumpNode ->
                val constantCond = when (jumpNode.cond) {
                    is IRInt -> jumpNode.cond.value
                    is IRVar -> (values[jumpNode.cond] as? SSCPValue.Value)?.value
                }
                if (constantCond == null) {
                    return@forEach
                }

                check(constantCond == 0L || constantCond == 1L) {
                    "Boolean values must be either 0 or 1, got $constantCond"
                }
                val target = if (constantCond == 1L) jumpNode.target else jumpNode.elseTarget
                val removedTarget = if (constantCond == 1L) jumpNode.elseTarget else jumpNode.target
                transformedJumps[jumpNode] = IRJump(target)
                removedJumps.getOrPut(removedTarget) { mutableSetOf() }.add(fromLabel)
            }
        }

        val newBlocks = cfg.blocks.mapValues { (currentLabel, block) ->
            block.transform(object : BaseIRTransformer() {
                override fun transformNode(node: IRNode): IRNode? {
                    node.lvalue?.let { lVar ->
                        if (values[lVar] is SSCPValue.Value) {
                            // Remove assignments for known constants
                            return null
                        }
                    }
                    if (node is IRJumpNode) {
                        transformedJumps[node]?.let {
                            return it
                        }
                    }
                    if (node is IRPhi) {
                        val removedFromLabels = removedJumps[currentLabel] ?: emptySet()
                        if (removedFromLabels.isNotEmpty()) {
                            val filteredNode = IRPhi(node.result, node.sources.filter { source ->
                                source.from !in removedFromLabels
                            })
                            return when (filteredNode.sources.size) {
                                0 -> error("Phi node in $currentLabel has no sources")
                                1 -> IRAssign(node.result, filteredNode.sources.single().value)
                                else -> filteredNode
                            }
                        }
                    }
                    return node
                }

                override fun transformRValue(value: IRValue): IRValue {
                    if (value is IRVar) {
                        (values[value] as? SSCPValue.Value)?.let {
                            return IRInt(it.value)
                        }
                    }
                    return value
                }
            })
        }

        return SSAControlFlowGraph(cfg.root, newBlocks)
    }

    companion object {
        private val equalComparisonOps = setOf(
            IRBinOpKind.EQ, IRBinOpKind.GE, IRBinOpKind.LE
        )
        private val notEqualComparisonOps = setOf(
            IRBinOpKind.NEQ, IRBinOpKind.GT, IRBinOpKind.LT
        )
    }
}