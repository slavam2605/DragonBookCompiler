package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar

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

    fun findColoring(): Map<IRVar, Color> {
        val unallocatedVars = graph.edges.keys.sortedBy { graph.edges[it]!!.size }.toMutableSet()
        while (unallocatedVars.isNotEmpty()) {
            val irVar = unallocatedVars.pickVar()
            if (irVar in coloring) {
                continue
            }

            val adjacent = graph.edges[irVar]!!
            val forbiddenColors = adjacent.mapNotNull { coloring[it] }.toSet()
            val allNonExtraColors = colors.filterNot(isExtraColor).toSet()
            val allowedAllColors = colors - forbiddenColors
            val allowedNonExtraColors = allNonExtraColors - forbiddenColors
            val allowedUsedColors = allowedNonExtraColors - unusedColors

            // 1. Try to pick an already used non-extra color
            if (allowedUsedColors.isNotEmpty()) {
                coloring[irVar] = allowedUsedColors.minBy(colorScore)
                continue
            }

            // 2. Try to recolor neighbors with used colors to free up a color
            tryRecolorNeighbors(irVar, colors - unusedColors)?.let { color ->
                coloring[irVar] = color
                continue
            }

            // 3. Try to pick an unused non-extra color
            if (allowedNonExtraColors.isNotEmpty()) {
                coloring[irVar] = allowedNonExtraColors.minBy(colorScore)
                continue
            }

            // 4. Try to recolor neighbors to free up a color
            tryRecolorNeighbors(irVar, allowedNonExtraColors)?.let { color ->
                coloring[irVar] = color
                continue
            }

            // 5. Try to pick a used extra color
            if (allowedAllColors.isNotEmpty()) {
                coloring[irVar] = allowedAllColors.minBy(colorScore)
                continue
            }

            // 6. Create a new extra color and use it
            val newColor = extraColorProvider(extraColorCounter++).also { colors.add(it) }
            coloring[irVar] = newColor
        }
        return coloring
    }

    private fun MutableSet<IRVar>.pickVar(): IRVar {
        return maxBy { candidate ->
            graph.edges[candidate]!!.count { it !in coloring }
        }.also {
            remove(it)
        }
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