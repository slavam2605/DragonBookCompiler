package compiler.ir.cfg

import compiler.ir.IRNode
import compiler.ir.IRTransformer

class CFGBlock(val irNodes: List<IRNode>) {
    fun transform(transformer: IRTransformer) =
        CFGBlock(irNodes.map { it.transform(transformer) })
}