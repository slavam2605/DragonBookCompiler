package compiler.ir.analysis

import compiler.frontend.CompilationException
import compiler.frontend.CompilationFailed
import compiler.frontend.UninitializedVariableException
import compiler.ir.IRPhi
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.ir.printToString

class DefiniteAssignmentAnalysis(private val cfg: ControlFlowGraph) {
    private val errors = mutableListOf<CompilationException>()

    fun run() {
        val allVars = cfg.blocks.flatMap { (_, block) ->
            block.irNodes.mapNotNull { node -> node.lvalue }
        }.toSet()

        val dfa = DataFlowFramework(
            cfg = cfg,
            identity = allVars,
            direction = DataFlowFramework.Direction.FORWARD,
            meet = { a, b -> a.intersect(b) },
            transfer = { label, inSet ->
                val outSet = inSet.toMutableSet()
                cfg.blocks[label]!!.irNodes.forEach { node ->
                    check(node !is IRPhi)
                    node.lvalue?.let {
                        outSet.add(it)
                    }
                }
                outSet
            },
            boundaryIn = { emptySet() },
            initialOut = { label ->
                if (label == cfg.root) emptySet() else allVars
            }
        )
        dfa.run()

        cfg.blocks.forEach { (label, block) ->
            val defSet = dfa.inValues[label]!!.toMutableSet()
            block.irNodes.forEach { node ->
                node.rvalues().filterIsInstance<IRVar>().forEach { irVar ->
                    if (irVar !in defSet) {
                        val location = SourceLocationMap.get(cfg, node)
                        val varName = irVar.sourceName ?: irVar.printToString()
                        errors.add(UninitializedVariableException(location, varName))
                    }
                }
                node.lvalue?.let {
                    defSet.add(it)
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw CompilationFailed(errors)
        }
    }
}