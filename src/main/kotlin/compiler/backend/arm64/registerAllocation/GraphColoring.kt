package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar
import java.util.PriorityQueue

class GraphColoring<Color>(
    colors: Set<Color>,
    private val initialColoring: Map<IRVar, Color>,
    private val graph: InterferenceGraph,
    private val colorScore: (Color) -> Int,
    private val isExtraColor: (Color) -> Boolean,
    private val extraColorProvider: (Int) -> Color
) {
    private val coloring = initialColoring.toMutableMap()
    private val colors = colors.toMutableSet()
    private val unusedColors = colors.toMutableSet()
    private var extraColorCounter = 0

    private data class WeightedNode(val irVar: IRVar, val uncoloredNeighbors: Int) : Comparable<WeightedNode> {
        override fun compareTo(other: WeightedNode): Int {
            // Reversed order for max-priority queue
            return other.uncoloredNeighbors.compareTo(uncoloredNeighbors)
        }
    }

    fun findColoring(): Map<IRVar, Color> {
        val unallocatedVars = PriorityQueue<WeightedNode>()
        val queueEntries = mutableMapOf<IRVar, WeightedNode>()
        graph.edges.forEach { (irVar, adj) ->
            val node = WeightedNode(irVar, adj.count { it !in coloring })
            unallocatedVars.add(node)
            queueEntries[irVar] = node
        }

        while (unallocatedVars.isNotEmpty()) {
            val irVar = unallocatedVars.poll().irVar
            if (irVar in coloring) {
                continue
            }

            val adjacent = graph.edges[irVar]!!
            val forbiddenColors = adjacent.mapNotNull { coloring[it] }.toSet()
            val allNonExtraColors = colors.filterNot(isExtraColor).toSet()
            val allowedAllColors = colors - forbiddenColors
            val allowedNonExtraColors = allNonExtraColors - forbiddenColors
            val allowedUsedColors = allowedNonExtraColors - unusedColors

            val newColor = chooseColor(irVar, allowedUsedColors, allowedNonExtraColors, allowedAllColors)
            coloring[irVar] = newColor

            // Update priority queue
            graph.edges[irVar]!!.forEach { otherVar ->
                val oldNode = queueEntries[otherVar]!!
                val newNode = WeightedNode(otherVar, oldNode.uncoloredNeighbors + 1)
                unallocatedVars.remove(oldNode)
                unallocatedVars.add(newNode)
                queueEntries[otherVar] = newNode
            }
        }
        return coloring
    }

    private fun chooseColor(
        irVar: IRVar,
        allowedUsedColors: Set<Color>,
        allowedNonExtraColors: Set<Color>,
        allowedAllColors: Set<Color>
    ): Color {
        // 1. Try to pick an already used non-extra color
        if (allowedUsedColors.isNotEmpty()) {
            return allowedUsedColors.minBy(colorScore)
        }

        // 2. Try to recolor neighbors with used colors to free up a color
        tryRecolorNeighbors(irVar, colors - unusedColors)?.let { color ->
            return color
        }

        // 3. Try to pick an unused non-extra color
        if (allowedNonExtraColors.isNotEmpty()) {
            return allowedNonExtraColors.minBy(colorScore)
        }

        // 4. Try to recolor neighbors to free up a color
        tryRecolorNeighbors(irVar, allowedNonExtraColors)?.let { color ->
            return color
        }

        // 5. Try to pick a used extra color
        if (allowedAllColors.isNotEmpty()) {
            return allowedAllColors.minBy(colorScore)
        }

        // 6. Create a new extra color
        return extraColorProvider(extraColorCounter++).also { colors.add(it) }
    }

    private fun tryRecolorNeighbors(irVar: IRVar, allColorsSet: Set<Color>): Color? {
        val adjacent = graph.edges[irVar]!!
        val adjacentColors = adjacent
            .mapNotNull { coloring[it] }
            .filter { !isExtraColor(it) }
        val forbiddenOnce = adjacentColors
            .filter { color -> adjacentColors.count { it == color } == 1 }
            .toSet()

        for (neighbor in adjacent) {
            if (neighbor in initialColoring) continue // Do not change initial coloring
            val oldColor = coloring[neighbor] ?: continue
            if (oldColor !in forbiddenOnce) continue

            val forbiddenRecolors = graph.edges[neighbor]!!.mapNotNull { coloring[it] }.toSet()
            val allowedRecolors = allColorsSet.toMutableSet()
            allowedRecolors.removeAll(forbiddenRecolors)
            allowedRecolors.remove(oldColor)

            if (allowedRecolors.isNotEmpty()) {
                val oldColor = coloring[neighbor]!!
                val newColor = allowedRecolors.minBy(colorScore)
                unusedColors.remove(newColor)
                coloring[neighbor] = newColor
                return oldColor
            }
        }

        return null
    }
}