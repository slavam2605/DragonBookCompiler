package compiler.ir.cfg.ssa

import compiler.ir.BaseIRTransformer
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.DominatorTree
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.get
import kotlin.collections.last

private class BlockRecord(val label: IRLabel, var index: Int)

internal class RenameVariablesForSSA(
    private val cfg: ControlFlowGraph,
    private val mutableBlocks: MutableMap<IRLabel, MutableList<IRNode>>
) {
    private val counter = mutableMapOf<String, Int>()
    private val stack = mutableMapOf<String, MutableList<BlockRecord>>()
    private val dominatorTree = DominatorTree.get(cfg)

    internal fun renameVariables() = renameBlock(cfg.root)

    private fun peekName(x: String): Int {
        val xStack = stack[x] ?: return 0
        return xStack.last().index
    }

    private fun newName(label: IRLabel, x: IRVar): IRVar {
        val index = counter.compute(x.name) { _, i -> (i ?: 0) + 1 }!!
        val xStack = stack.getOrPut(x.name) { mutableListOf() }
        if (xStack.isEmpty() || xStack.last().label != label) {
            xStack.add(BlockRecord(label, index))
        } else {
            xStack.last().index = index
        }
        return IRVar(x.name, index, x.sourceName)
    }

    private fun renameBlock(label: IRLabel) {
        // Step 1. Rename all variables in the block
        val block = mutableBlocks[label]!!
        var isInPhiPrefix = true
        block.forEachIndexed { index, node ->
            if (node !is IRPhi) isInPhiPrefix = false
            check(isInPhiPrefix || node !is IRPhi)

            if (node is IRPhi) {
                block[index] = IRPhi(newName(label, node.result), node.sources)
            } else {
                val renamedVars = node.rvalues()
                    .filterIsInstance<IRVar>()
                    .associateWith {
                        val ssaIndex = peekName(it.name)
                        IRVar(it.name, ssaIndex, it.sourceName)
                    }
                val lVars = node.lvalues()
                check(lVars.size <= 2 || lVars.distinct().size == lVars.size)
                val newLVars = lVars.associateWith { newName(label, it) }
                block[index] = node.transform(object : BaseIRTransformer() {
                    override fun transformLValue(value: IRVar) = newLVars[value]!!
                    override fun transformRValue(value: IRValue) = renamedVars[value] ?: value
                })
            }
        }

        // Step 2. Fill in phi-nodes in **successor blocks of CFG**
        cfg.edges(label).forEach { successor ->
            val nextBlock = mutableBlocks[successor]!!
            val selfIndex = cfg.getBlockIndex(label, successor)
            nextBlock.forEachIndexed { index, node ->
                if (node !is IRPhi) return@forEachIndexed
                val sourceVar = node.sources[selfIndex]
                if (sourceVar !is IRVar) return@forEachIndexed
                val lastSSAVer = peekName(sourceVar.name)
                nextBlock[index] = node.replaceSourceAt(selfIndex, IRVar(sourceVar.name, lastSSAVer, sourceVar.sourceName))
            }
        }

        // Step 3. Recursively handle all **successor blocks in the dominator tree**
        dominatorTree.edges(label).forEach { successor ->
            renameBlock(successor)
        }

        // Step 4. Unwind stacks modified in this block
        stack.forEach { (_, stack) ->
            if (stack.lastOrNull()?.label == label) {
                stack.removeLast()
            }
        }
    }
}