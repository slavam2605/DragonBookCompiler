package compiler.ir.cfg

import compiler.ir.IRNode
import compiler.ir.IRTransformer

class CFGBlock(val irNodes: List<IRNode>) {
    /**
     * Transforms the block by applying the given [transformer] to each of its nodes.
     * Keeps the source location of each node.
     */
    fun transformKeepSource(sourceMap: SourceLocationMap, transformer: IRTransformer) =
        CFGBlock(irNodes.map { oldNode ->
            val newNode = oldNode.transform(transformer)
            // Keep the source location after transformation
            sourceMap[oldNode]?.let { sourceMap[newNode] = it }
            newNode
        })
}