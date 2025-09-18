package compiler.ir.optimization.constant

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRFloat
import compiler.ir.IRInt
import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRJumpNode
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Substitutes known constants to the CFG and folds jumps with constant conditions.
 */
object FoldConstantExpressions {
    fun run(cfg: SSAControlFlowGraph, cpValues: Map<IRVar, SSCPValue>): SSAControlFlowGraph {
        val removedJumps = mutableMapOf<IRLabel, MutableSet<IRLabel>>() // to -> setOf(from)
        val transformedJumps = mutableMapOf<IRJumpNode, IRJumpNode>()
        var changed = false

        cfg.blocks.forEach { (fromLabel, fromBlock) ->
            fromBlock.irNodes.filterIsInstance<IRJumpIfTrue>().forEach { jumpNode ->
                val constantCond = when (jumpNode.cond) {
                    is IRInt -> jumpNode.cond.value
                    is IRVar -> (cpValues[jumpNode.cond] as? SSCPValue.IntValue)?.value
                    else -> error("Floats are not supported")
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
                    // Remove assignments for known constants
                    node.lvalue?.let { lVar ->
                        if (cpValues[lVar] is SSCPValue.Value) {
                            changed = true
                            return null
                        }
                    }

                    // Simplify expressions, e.g. x + 0 => x
                    ArithmeticRules.simplifyNode(node)?.let {
                        changed = true
                        return it
                    }

                    // Replace transformed jumps
                    if (node is IRJumpNode) {
                        transformedJumps[node]?.let {
                            changed = true
                            return it
                        }
                    }

                    // Fix phi-nodes after modifying jumps
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
                        (cpValues[value] as? SSCPValue.Value)?.let { cpValue ->
                            changed = true
                            return when (cpValue) {
                                is SSCPValue.IntValue -> IRInt(cpValue.value)
                                is SSCPValue.FloatValue -> IRFloat(cpValue.value)
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