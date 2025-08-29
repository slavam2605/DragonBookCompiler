package compiler.ir.optimization.valueNumbering

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRUndef
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.extensions.DominatorTree
import compiler.ir.cfg.ssa.SSAControlFlowGraph

class GlobalValueNumbering(private val cfg: SSAControlFlowGraph) {
    private var currentNumber = 0L
    private val numberMap = mutableListOf<MutableMap<IRValue, Long>>()
    private val valueMap = mutableListOf<MutableMap<RightSideValue, IRVar>>()
    private val newBlocks = mutableMapOf<IRLabel, CFGBlock>()
    private var changed = false

    private fun nextNumber() = ++currentNumber

    private fun <K, V> List<Map<K, V>>.get(key: K): V? {
        for (map in this) {
            map[key]?.let { return it }
        }
        return null
    }

    private fun <K, V> List<MutableMap<K, V>>.put(key: K, value: V) {
        last()[key] = value
    }

    fun run(): SSAControlFlowGraph {
        val domTree = DominatorTree.get(cfg)
        traverse(cfg.root, domTree)

        if (!changed) return cfg
        return SSAControlFlowGraph(cfg.root, newBlocks)
    }

    private fun traverse(label: IRLabel, tree: DominatorTree) {
        numberMap.add(mutableMapOf())
        valueMap.add(mutableMapOf())

        transformBlock(label)
        tree.edges(label).forEach { successor ->
            traverse(successor, tree)
        }

        numberMap.removeLast()
        valueMap.removeLast()
    }

    private fun transformBlock(label: IRLabel) {
        newBlocks[label] = cfg.blocks[label]!!.transform(object : BaseIRTransformer() {
            override fun transformNode(node: IRNode): IRNode {
                val irVar = node.lvalue ?: return node
                val rNumbers = node.rvalues().map { irValue ->
                    // Each "undef" is a new value
                    if (irValue is IRUndef) return@map nextNumber()
                    numberMap.get(irValue)
                        ?: nextNumber().also { numberMap.put(irValue, it) }
                }
                val rhs = RightSideValue.fromIRNode(node, rNumbers) ?: run {
                    numberMap.put(irVar, nextNumber())
                    return node
                }

                val existingVar = valueMap.get(rhs)
                if (existingVar != null) {
                    changed = true
                    numberMap.put(irVar, numberMap.get(existingVar)!!)
                    return IRAssign(irVar, existingVar)
                }

                valueMap.put(rhs, irVar)
                numberMap.put(irVar, nextNumber())
                return node
            }
        })
    }
}