package compiler.ir.cfg.extensions

import compiler.frontend.SourceLocation
import compiler.ir.IRNode
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.ExtensionKey
import java.util.IdentityHashMap

class SourceLocationMap {
    private val map = IdentityHashMap<IRNode, SourceLocation>()

    operator fun get(irNode: IRNode): SourceLocation? = map[irNode]

    operator fun set(irNode: IRNode, sourceLocation: SourceLocation) {
        map[irNode] = sourceLocation
    }

    fun remove(irNode: IRNode): SourceLocation? {
        return map.remove(irNode)
    }

    fun replace(oldNode: IRNode, newNode: IRNode) {
        remove(oldNode)?.let {
            map[newNode] = it
        }
    }

    fun copyTo(other: SourceLocationMap) = other.map.putAll(map)

    private fun copy(): SourceLocationMap {
        return SourceLocationMap().also {
            it.map.putAll(map)
        }
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

        fun extractMap(cfg: ControlFlowGraph): SourceLocationMap {
            return cfg.removeExtension(Key) ?: empty()
        }

        fun copyMap(cfg: ControlFlowGraph): SourceLocationMap {
            return cfg.getExtension(Key)?.copy() ?: empty()
        }
    }
}