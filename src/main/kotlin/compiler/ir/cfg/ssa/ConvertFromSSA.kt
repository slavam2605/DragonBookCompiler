package compiler.ir.cfg.ssa

import compiler.ir.BaseIRTransformer
import compiler.ir.IRAssign
import compiler.ir.IRJump
import compiler.ir.IRLabel
import compiler.ir.IRNode
import compiler.ir.IRPhi
import compiler.ir.IRSource
import compiler.ir.IRVar
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.advanceAfterAllLabels
import compiler.utils.NameAllocator

class ConvertFromSSA(private val ssa: SSAControlFlowGraph) {
    private val newBlocks = mutableMapOf<IRLabel, MutableList<IRNode>>()

    fun run(): ControlFlowGraph {
        splitCriticalEdges()

        newBlocks.forEach { (label, irNodes) ->
            val phiNodes = irNodes
                .filterIsInstance<IRPhi>()
                .ifEmpty { return@forEach }

            val fromLabels = phiNodes[0].sources.map { it.from }
            check(phiNodes.all {
                it.sources.map { source -> source.from }.toSet() == fromLabels.toSet()
            })

            fromLabels.forEach { fromLabel ->
                insertPhiAssignments(phiNodes, fromLabel)
            }
            newBlocks[label]!!.removeIf { it is IRPhi }
        }

        return SSAControlFlowGraph(ssa.root, newBlocks.mapValues { (_, irNodes) ->
            CFGBlock(irNodes)
        })
    }

    private fun insertPhiAssignments(phiNodes: List<IRPhi>, fromLabel: IRLabel) {
        val resultIR = mutableListOf<IRNode>()

        // Generate move graph
        val moves = mutableMapOf<IRVar, IRVar>()
        phiNodes.forEach { node ->
            val value = node.getSourceValue(fromLabel)
            if (node.result == value) {
                // Skip trivial moves
                return@forEach
            }

            if (value is IRVar) {
                moves[node.result] = value
            } else {
                // Insert constant assignments first
                resultIR.add(IRAssign(node.result, value))
            }
        }

        // Find all loops
        val visited = mutableSetOf<IRVar>()
        val loops = mutableListOf<List<IRVar>>()
        moves.keys.forEach { irVar ->
            if (irVar in visited) return@forEach
            val localVisited = mutableSetOf<IRVar>()
            val loop = mutableListOf<IRVar>()

            var current = irVar
            while (current in moves) {
                if (current in localVisited) {
                    val indexOfCurrent = loop.indexOf(current)
                    loops.add(loop.drop(indexOfCurrent))
                    break
                }

                localVisited.add(current)
                loop.add(current)
                current = moves[current]!!
            }

            visited.addAll(localVisited)
        }

        // Initialize temp variables allocator to avoid duplicated var names
        val tempVarAllocator = NameAllocator("x")
        if (loops.isNotEmpty()) {
            ssa.blocks.forEach { (_, block) ->
                block.irNodes.mapNotNull { it.lvalue }.forEach {
                    tempVarAllocator.advanceAfter(it.name)
                }
            }
        }

        // Process all loops
        loops.forEach { loop ->
            check(loop.size >= 2)
            check(loop.all { it.type == loop[0].type })

            // t = loop[0]; { loop[0] = loop[1]; ...; loop[n-2] = loop[n-1]; } loop[n-1] = t;
            val tempVar = IRVar(tempVarAllocator.newName(), loop[0].type, null)
            resultIR.add(IRAssign(tempVar, loop[0]))
            loop.zipWithNext().forEach { (to, from) ->
                resultIR.add(IRAssign(to, from))
            }
            resultIR.add(IRAssign(loop.last(), tempVar))

            // Remove all vars from the processed loop
            loop.forEach { moves.remove(it) }
        }

        // Insert remaining moves in topological order
        topoOrderNodes(moves).forEach {
            val from = moves[it]!!
            resultIR.add(IRAssign(it, from))
        }

        // Insert generated moves
        val fromNodes = newBlocks[fromLabel]!!
        fromNodes.addAll(fromNodes.lastIndex, resultIR)
    }

    fun topoOrderNodes(edges: Map<IRVar, IRVar>): List<IRVar> {
        // Compute indegree for each node (number of incoming edges)
        val indegree = HashMap<IRVar, Int>().apply {
            for (dst in edges.values) {
                compute(dst) { _, d -> (d ?: 0) + 1 }
            }
        }

        // Initialize the worklist with nodes that have indegree 0
        val worklist = mutableListOf<IRVar>()
        for (irVar in edges.keys) {
            if (indegree.getOrDefault(irVar, 0) == 0) {
                worklist.addLast(irVar)
            }
        }

        val order = ArrayList<IRVar>(edges.size)
        while (worklist.isNotEmpty()) {
            val from = worklist.removeLast()
            val to = edges[from] ?: continue

            order.add(from)
            val newDegree = indegree.compute(to) { _, d -> (d ?: 0) - 1 }
            if (newDegree == 0) worklist.add(to)
        }

        // If we didn't emit all nodes, there was a cycle
        require(order.size == edges.size) { "Graph contains a cycle; topological order does not exist." }
        return order
    }

    private fun splitCriticalEdges(): MutableMap<IRLabel, MutableList<IRNode>> {
        val criticalEdges = mutableSetOf<Pair<IRLabel, IRLabel>>()
        ssa.blocks.forEach { (fromLabel, _) ->
            val edges = ssa.edges(fromLabel)
            if (edges.size <= 1) return@forEach

            edges.forEach { toLabel ->
                if (ssa.backEdges(toLabel).size > 1) {
                    criticalEdges.add(fromLabel to toLabel)
                }
            }
        }

        val labelAllocator = uniqueLabelAllocator(ssa)
        val changedSources = mutableMapOf<Pair<IRLabel, IRLabel>, IRLabel>()
        ssa.blocks.forEach { (fromLabel, block) ->
            newBlocks[fromLabel] = block.transform(object : BaseIRTransformer() {
                override fun transformLabel(label: IRLabel): IRLabel {
                    if ((fromLabel to label) in criticalEdges) {
                        val newLabel = IRLabel(labelAllocator.newName())
                        newBlocks[newLabel] = mutableListOf(IRJump(label))
                        changedSources[fromLabel to label] = newLabel
                        return newLabel
                    }
                    return label
                }
            }).irNodes.toMutableList()
        }

        newBlocks.forEach { (label, block) ->
            block.forEachIndexed { index, node ->
                if (node !is IRPhi) return@forEachIndexed
                val newSources = node.sources.map { oldSource ->
                    changedSources[oldSource.from to label]
                        ?.let { newFrom -> IRSource(newFrom, oldSource.value) }
                        ?: oldSource
                }
                block[index] = IRPhi(node.result, newSources)
            }
        }

        return newBlocks
    }

    private fun uniqueLabelAllocator(cfg: ControlFlowGraph): NameAllocator {
        return NameAllocator("L").also {
            it.advanceAfterAllLabels(cfg)
        }
    }
}