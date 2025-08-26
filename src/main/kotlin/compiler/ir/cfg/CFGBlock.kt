package compiler.ir.cfg

import compiler.ir.BaseIRTransformer
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRTransformer
import compiler.ir.cfg.extensions.SourceLocationMap

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CFGBlock
        return irNodes == other.irNodes
    }

    override fun hashCode(): Int {
        return irNodes.hashCode()
    }
}

fun CFGBlock.transformLabel(
    sourceMap: SourceLocationMap? = null,
    transformer: (IRLabel) -> IRLabel
): CFGBlock {
    return transform(object : BaseIRTransformer() {
        override fun transformLabel(label: IRLabel): IRLabel {
            return transformer(label)
        }
    }, sourceMap)
}