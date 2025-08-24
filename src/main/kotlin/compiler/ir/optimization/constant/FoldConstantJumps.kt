package compiler.ir.optimization.constant

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRUndef
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import kotlin.collections.component1
import kotlin.collections.component2

object FoldConstantJumps {
    fun run(cfg: SSAControlFlowGraph, cpValues: Map<IRVar, SSCPValue>): SSAControlFlowGraph {
        val removedJumps = mutableMapOf<IRLabel, MutableSet<IRLabel>>() // to -> setOf(from)
        val transformedJumps = mutableMapOf<IRJumpNode, IRJumpNode>()
        val conditionalValues = mutableMapOf<IRLabel, MutableMap<IRLabel, MutableMap<IRVar, SSCPValue>>>()
        var changed = false

        cfg.blocks.forEach { (fromLabel, fromBlock) ->
            fromBlock.irNodes.filterIsInstance<IRJumpIfTrue>().forEach { jumpNode ->
                val constantCond = when (jumpNode.cond) {
                    is IRInt -> jumpNode.cond.value
                    is IRVar -> (cpValues[jumpNode.cond] as? SSCPValue.Value)?.value
                    is IRUndef -> null
                }
                if (constantCond == null) {
                    (jumpNode.cond as? IRVar)?.let { condVar ->
                        if (jumpNode.target == jumpNode.elseTarget) {
                            // Do not push conditional values to the same target,
                            // it would become Bottom anyway
                            return@let
                        }

                        val trueValues = conditionalValues
                            .getOrPut(jumpNode.target) { mutableMapOf() }
                            .getOrPut(fromLabel) { mutableMapOf() }
                        val falseValues = conditionalValues
                            .getOrPut(jumpNode.elseTarget) { mutableMapOf() }
                            .getOrPut(fromLabel) { mutableMapOf() }

                        check(trueValues.put(condVar, SSCPValue.Value(1)) == null)
                        check(falseValues.put(condVar, SSCPValue.Value(0)) == null)
                    }
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
                        if (cpValues[lVar] is SSCPValue.Value) {
                            changed = true
                            // Remove assignments for known constants
                            return null
                        }
                    }
                    if (node is IRJumpNode) {
                        transformedJumps[node]?.let {
                            changed = true
                            return it
                        }
                    }
                    if (node is IRPhi) {
                        val removedFromLabels = removedJumps[currentLabel] ?: emptySet()
                        if (removedFromLabels.isNotEmpty()) {
                            val filteredNode = IRPhi(node.result, node.sources.filter { source ->
                                source.from !in removedFromLabels
                            })
                            changed = changed || (filteredNode.sources.size != node.sources.size)
                            return when (filteredNode.sources.size) {
                                0 -> error("Phi node in $currentLabel has no sources")
                                1 -> IRAssign(node.result, filteredNode.sources.single().value)
                                else -> filteredNode
                            }
                        }
                    }
                    return node
                }

                override fun transformRValue(node: IRNode, index: Int, value: IRValue): IRValue {
                    if (value is IRVar) {
                        (cpValues[value] as? SSCPValue.Value)?.let {
                            changed = true
                            return IRInt(it.value)
                        }

                        conditionalValues[currentLabel]?.let { currentValues ->
                            val condValue = cfg.backEdges(currentLabel)
                                .map { currentValues[it]?.get(value) ?: SSCPValue.Bottom }
                                .reduce(SSCPValue::times)

                            if (condValue is SSCPValue.Value) {
                                changed = true
                                return IRInt(condValue.value)
                            }
                        }
                    }
                    return value
                }
            })
        }

        if (!changed) return cfg
        return SSAControlFlowGraph(cfg.root, newBlocks)
    }
}