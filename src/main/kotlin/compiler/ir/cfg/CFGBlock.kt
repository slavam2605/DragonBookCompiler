package compiler.ir.cfg

import compiler.ir.IRNode
import compiler.ir.IRTransformer

class CFGBlock(val irNodes: List<IRNode>) {
    /**
     * Transforms the block by applying the given [transformer] to each of its nodes.
     *
     * Keeps the source location of each node, if [sourceMap] is provided.
     */
    fun transform(transformer: IRTransformer, sourceMap: SourceLocationMap? = null) =
        CFGBlock(irNodes.mapNotNull { oldNode ->
            val transformNode = transformer.transformNode(oldNode) ?: return@mapNotNull null
            transformNode.transform(transformer).also { newNode ->
                if (sourceMap != null) {
                    sourceMap[oldNode]?.let { sourceMap[newNode] = it }
                }
            }
        })
}