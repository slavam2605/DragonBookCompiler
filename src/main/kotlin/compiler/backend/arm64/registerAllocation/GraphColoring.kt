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
    private val forbiddenColors = mutableMapOf<IRVar, MutableMap<Color, Int>>()
    private val colors = colors.toMutableSet()
    private val unusedColors = colors.toMutableSet()
    private var extraColorCounter = 0

    private data class WeightedNode(val irVar: IRVar, val uncoloredNeighbors: Int) : Comparable<WeightedNode> {
        override fun compareTo(other: WeightedNode): Int {
            if (uncoloredNeighbors != other.uncoloredNeighbors) {
                // Reversed order for max-priority queue
                return other.uncoloredNeighbors.compareTo(uncoloredNeighbors)
            }
            if (irVar.name != other.irVar.name) {
                return irVar.name.compareTo(other.irVar.name)
            }
            return irVar.ssaVer.compareTo(other.irVar.ssaVer)
        }
    }

    fun findColoring(): Map<IRVar, Color> {
        // Initialize forbidden colors
        graph.edges.forEach { (irVar, adj) ->
            val forbidden = mutableMapOf<Color, Int>()
            adj.forEach { otherVar ->
                coloring[otherVar]?.let {
                    forbidden.compute(it) { _, count -> (count ?: 0) + 1 }
                }
            }
            forbiddenColors[irVar] = forbidden
        }

        // Initialize priority queue
        val unallocatedVars = PriorityQueue<WeightedNode>()
        val queueEntries = mutableMapOf<IRVar, WeightedNode>()
        graph.edges.forEach { (irVar, adj) ->
            if (irVar in coloring) return@forEach
            val node = WeightedNode(irVar, adj.count { it !in coloring })
            unallocatedVars.add(node)
            queueEntries[irVar] = node
        }

        while (unallocatedVars.isNotEmpty()) {
            val irVar = unallocatedVars.poll().irVar
            queueEntries.remove(irVar)

            coloring[irVar] = chooseColor(irVar)
            updateForbiddenColors(irVar)

            // Update priority queue
            graph.edges[irVar]!!.forEach { otherVar ->
                val oldNode = queueEntries[otherVar] ?: return@forEach
                val newNode = WeightedNode(otherVar, oldNode.uncoloredNeighbors - 1)
                unallocatedVars.remove( oldNode)
                unallocatedVars.add(newNode)
                queueEntries[otherVar] = newNode
            }
        }
        return coloring
    }

    private fun updateForbiddenColors(irVar: IRVar, removedColor: Color? = null) {
        val color = removedColor ?: coloring[irVar]!!
        val delta = if (removedColor == null) 1 else -1
        graph.edges[irVar]!!.forEach { otherVar ->
            forbiddenColors[otherVar]!!.compute(color) { _, count ->
                val newValue = (count ?: 0) + delta
                if (newValue == 0) null else newValue
            }
        }
    }

    private fun chooseColor(irVar: IRVar): Color {
        val forbiddenColors = forbiddenColors[irVar]!!
        val allowedNonExtraColors = colors.filterTo(mutableSetOf()) {
            !isExtraColor(it) && it !in forbiddenColors
        }
        val allowedUsedColors = allowedNonExtraColors - unusedColors

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
        val allowedAllColors = colors.filterTo(mutableSetOf()) { it !in forbiddenColors }
        if (allowedAllColors.isNotEmpty()) {
            return allowedAllColors.minBy(colorScore)
        }

        // 6. Create a new extra color
        return extraColorProvider(extraColorCounter++).also { colors.add(it) }
    }

    private fun tryRecolorNeighbors(irVar: IRVar, allColorsSet: Set<Color>): Color? {
        val forbiddenOnce = forbiddenColors[irVar]!!
            .filter { (color, count) -> count == 1 && !isExtraColor(color) }.keys
        if (forbiddenOnce.isEmpty()) return null

        for (neighbor in graph.edges[irVar]!!) {
            if (neighbor in initialColoring) continue // Do not change initial coloring
            val oldColor = coloring[neighbor] ?: continue
            if (oldColor !in forbiddenOnce) continue

            val forbiddenRecolors = forbiddenColors[neighbor]!!.keys
            val allowedRecolors = allColorsSet.toMutableSet()
            allowedRecolors.removeAll(forbiddenRecolors)
            allowedRecolors.remove(oldColor)

            if (allowedRecolors.isNotEmpty()) {
                val oldColor = coloring[neighbor]!!
                val newColor = allowedRecolors.minBy(colorScore)
                unusedColors.remove(newColor)
                coloring[neighbor] = newColor
                updateForbiddenColors(neighbor, oldColor)
                updateForbiddenColors(neighbor)
                return oldColor
            }
        }

        return null
    }
}