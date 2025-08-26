package compiler.ir.cfg.utils

import compiler.ir.IRLabel
import compiler.ir.IRPhi
import compiler.ir.cfg.ControlFlowGraph

fun IRLabel.hasPhiNodes(cfg: ControlFlowGraph): Boolean {
    return cfg.blocks[this]!!.irNodes.any { it is IRPhi }
}

fun IRLabel.phiNodes(cfg: ControlFlowGraph): List<IRPhi> {
    return cfg.blocks[this]!!.irNodes.filterIsInstance<IRPhi>()
}