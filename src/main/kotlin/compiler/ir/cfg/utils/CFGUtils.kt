package compiler.ir.cfg.utils

import compiler.ir.IRFunctionCall
import compiler.ir.IRLabel
import compiler.ir.IRPhi
import compiler.ir.cfg.ControlFlowGraph
import compiler.utils.NameAllocator

fun IRLabel.hasPhiNodes(cfg: ControlFlowGraph): Boolean {
    return cfg.blocks[this]!!.irNodes.any { it is IRPhi }
}

fun IRLabel.phiNodes(cfg: ControlFlowGraph): List<IRPhi> {
    return cfg.blocks[this]!!.irNodes.filterIsInstance<IRPhi>()
}

fun ControlFlowGraph.reachableFrom(start: IRLabel): Set<IRLabel> {
    val visited = mutableSetOf<IRLabel>()
    val queue = mutableListOf(start)
    while (queue.isNotEmpty()) {
        val label = queue.removeLast()
        if (label in visited) {
            continue
        }

        visited.add(label)
        queue.addAll(edges(label))
    }
    return visited
}

fun ControlFlowGraph.hasFunctionCalls(): Boolean {
    return blocks.values.any { block ->
        block.irNodes.any { it is IRFunctionCall }

fun NameAllocator.advanceAfterAllLabels(cfg: ControlFlowGraph) {
    cfg.blocks.keys.forEach {
        advanceAfter(it.name)
    }
}

fun NameAllocator.advanceAfterAllVars(cfg: ControlFlowGraph) {
    cfg.blocks.values.forEach { block ->
        block.irNodes.forEach { node ->
            node.lvalue?.let { advanceAfter(it.name) }
        }
    }
}