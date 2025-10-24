package compiler.ir.cfg.ssa

import compiler.ir.BaseIRTransformer
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRValue
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.DominatorTree
import compiler.ir.cfg.extensions.SourceLocationMap
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.get
import kotlin.collections.last

private class BlockRecord(val label: IRLabel, var index: Int)

internal class RenameVariablesForSSA(
    private val cfg: ControlFlowGraph,
    private val mutableBlocks: MutableMap<IRLabel, MutableList<IRNode>>,
    private val sourceMap: SourceLocationMap
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
        return IRVar(x.name, index, x.type, x.sourceName)
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
                        IRVar(it.name, ssaIndex, it.type, it.sourceName)
                    }
                val newLVar = node.lvalue?.let { newName(label, it) }
                block[index] = node.transform(object : BaseIRTransformer() {
                    override fun transformLValue(value: IRVar) = newLVar!!
                    override fun transformRValue(node: IRNode, index: Int, value: IRValue) = renamedVars[value] ?: value
                })
            }

            // Keep track of source mapping
            sourceMap.replace(node, block[index])
        }

        // Step 2. Fill in phi-nodes in **successor blocks of CFG**
        cfg.edges(label).forEach { successor ->
            val nextBlock = mutableBlocks[successor]!!
            nextBlock.forEachIndexed { index, node ->
                if (node !is IRPhi) return@forEachIndexed
                val sourceVar = node.getSourceValue(label)
                check(sourceVar is IRVar) { "Source of phi-node must be a variable during conversion to SSA" }
                val lastSSAVer = peekName(sourceVar.name)
                nextBlock[index] = node.replaceSourceValue(label, IRVar(sourceVar.name, lastSSAVer, sourceVar.type, sourceVar.sourceName))
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