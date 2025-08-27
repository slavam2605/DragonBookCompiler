package compiler.ir.analysis

import compiler.ir.IRLabel
import compiler.ir.IRVar
import compiler.ir.cfg.ControlFlowGraph

class LiveVarAnalysis(private val cfg: ControlFlowGraph) {
    fun run(): DataFlowFramework<Set<IRVar>> {
        val ueVars = mutableMapOf<IRLabel, MutableSet<IRVar>>()
        val varKills = mutableMapOf<IRLabel, MutableSet<IRVar>>()
        cfg.blocks.forEach { (label, block) ->
            val ueVar = ueVars.getOrPut(label) { mutableSetOf() }
            val varKill = varKills.getOrPut(label) { mutableSetOf() }
            block.irNodes.forEach { node ->
                node.rvalues().filterIsInstance<IRVar>().forEach { rVar ->
                    if (rVar !in varKill) {
                        ueVar.add(rVar)
                    }
                }
                node.lvalue?.let { lVar ->
                    varKill.add(lVar)
                }
            }
        }

        return DataFlowFramework<Set<IRVar>>(
            cfg = cfg,
            direction = DataFlowFramework.Direction.BACKWARD,
            identity = emptySet(),
            meet = { a, b -> a.union(b) },
            transfer = { label, outSet -> ueVars[label]!! + (outSet - varKills[label]!!) },
            initialOut = { _ -> emptySet() }
        ).run()
    }
}