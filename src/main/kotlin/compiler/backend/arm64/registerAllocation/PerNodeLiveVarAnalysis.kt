package compiler.backend.arm64.registerAllocation

import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRVar
import compiler.ir.analysis.LiveVarAnalysis
import compiler.ir.cfg.ControlFlowGraph

class PerNodeLiveVarAnalysis(val cfg: ControlFlowGraph) {
    fun run(handler: (IRNode, liveIn: Set<IRVar>, liveOut: Set<IRVar>) -> Unit) {
        val dfa = LiveVarAnalysis(cfg).run()

        cfg.blocks.forEach { (label, block) ->
            val live = dfa.outValues[label]!!.toMutableSet()
            block.irNodes.asReversed().forEach { irNode ->
                check(irNode !is IRPhi)

                val nodeLiveOut = live.toSet()
                irNode.lvalue?.let { live.remove(it) }
                live.addAll(irNode.rvalues().filterIsInstance<IRVar>())
                val nodeLiveIn = live.toSet()

                handler(irNode, nodeLiveIn, nodeLiveOut)
            }
        }
    }
}