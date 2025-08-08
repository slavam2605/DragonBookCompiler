package compiler.ir.cfg

import compiler.frontend.SourceLocation
import compiler.ir.IRNode

class SourceLocationMap {
    private val map = mutableMapOf<IRNode, SourceLocation>()

    operator fun get(irNode: IRNode): SourceLocation? = map[irNode]

    operator fun set(irNode: IRNode, sourceLocation: SourceLocation) {
        map[irNode] = sourceLocation
    }

    companion object {
        private val Key = ExtensionKey<SourceLocationMap>("SourceLocationMap")

        fun empty() = SourceLocationMap()

        fun get(cfg: ControlFlowGraph, irNode: IRNode): SourceLocation? {
            val sourceMap = cfg.getExtension(Key) ?: return null
            return sourceMap[irNode]
        }

        fun storeMap(sourceMap: SourceLocationMap, cfg: ControlFlowGraph) {
            cfg.putExtension(Key, sourceMap)
        }

        fun extractMap(cfg: ControlFlowGraph): SourceLocationMap? {
            return cfg.removeExtension(Key)
        }
    }
}