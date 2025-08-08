package compiler.ir.analysis

import compiler.frontend.CompilationException
import compiler.frontend.CompilationFailed
import compiler.frontend.UninitializedVariableException
import compiler.ir.IRLabel
import compiler.ir.IRPhi
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ReversedPostOrderTraversal
import compiler.ir.cfg.SourceLocationMap
import compiler.ir.printToString

class DefiniteAssignmentAnalysis(private val cfg: ControlFlowGraph) {
    private val inMap = mutableMapOf<IRLabel, Set<IRVar>>()
    private val outMap = mutableMapOf<IRLabel, Set<IRVar>>()
    private val errors = mutableListOf<CompilationException>()

    init {
        val allVars = cfg.blocks.flatMap { (_, block) ->
            block.irNodes.flatMap { node -> node.lvalues() }
        }.toSet()
        cfg.blocks.keys.forEach { label ->
            outMap[label] = allVars
        }
        outMap[cfg.root] = emptySet()
    }

    fun run() {
        var changed = true
        while (changed) {
            changed = false
            ReversedPostOrderTraversal.traverse(cfg) { label, block ->
                val predOuts = cfg.backEdges(label)
                    .map { outMap.getOrPut(it) { emptySet()} }
                    .ifEmpty { listOf(emptySet()) }
                val inSet = predOuts
                    .reduce { acc, set -> acc.intersect(set) }
                val outSet = inSet.toMutableSet()

                block.irNodes.forEach { node ->
                    check(node !is IRPhi)
                    node.lvalues().forEach {
                        outSet.add(it)
                    }
                }

                val oldInSet = inMap.getOrPut(label) { emptySet() }
                val oldOutSet = outMap.getOrPut(label) { emptySet() }
                if (inSet != oldInSet || outSet != oldOutSet) {
                    inMap[label] = inSet
                    outMap[label] = outSet
                    changed = true
                }
            }
        }

        cfg.blocks.forEach { (label, block) ->
            val liveSet = inMap[label]!!.toMutableSet()
            block.irNodes.forEach { node ->
                node.rvalues().filterIsInstance<IRVar>().forEach { irVar ->
                    if (irVar !in liveSet) {
                        val location = SourceLocationMap.get(cfg, node)
                        val varName = irVar.sourceName ?: irVar.printToString()
                        errors.add(UninitializedVariableException(location, varName))
                    }
                }
                node.lvalues().forEach {
                    liveSet.add(it)
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw CompilationFailed(errors)
        }
    }
}