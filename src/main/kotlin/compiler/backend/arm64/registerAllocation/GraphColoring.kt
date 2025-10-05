package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar

/**
 * Holds register allocation preferences for variables.
 *
 * @param hardRequirements Map from variable to the mandatory color
 * @param explicitPreferences Map from variable to list of preferred colors (in priority order)
 * @param copyRelations Set of copy relations (entries should prefer to have the same color)
 */
data class ColoringPreferences<out Color>(
    val hardRequirements: List<Pair<IRVar, Color>> = emptyList(),
    val explicitPreferences: Map<IRVar, List<Color>> = emptyMap(),
    val copyRelations: Map<IRVar, Set<IRVar>> = emptyMap()
)

class GraphColoring<Color>(
    colors: Set<Color>,
    private val initialColoring: Map<IRVar, Color>,
    private val graph: InterferenceGraph,
    private val allocationScorer: (IRVar, Color) -> AllocationScore,
    private val isExtraColor: (Color) -> Boolean,
    private val extraColorProvider: (Int) -> Color,
    private val preferences: ColoringPreferences<Color> = ColoringPreferences()
) {
    private val coloring = initialColoring.toMutableMap()
    private val forbiddenColors = mutableMapOf<IRVar, MutableMap<Color, Int>>()
    private val colors = colors.toMutableSet()
    private val unusedColors = colors.toMutableSet()
    private var extraColorCounter = 0

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

        // Initialize bucket queue
        val bucketQueue = GraphColoringBucketQueue(graph.edges.size)
        graph.edges.forEach { (irVar, adj) ->
            if (irVar in coloring) return@forEach
            val weight = adj.count { it !in coloring }
            bucketQueue.preInitAdd(irVar, weight)
        }
        bucketQueue.init()

        while (bucketQueue.isNotEmpty()) {
            val irVar = bucketQueue.maxPoll()

            val newColor = chooseColor(irVar)
            coloring[irVar] = newColor
            val adjacent = graph.edges[irVar]!!
            updateForbiddenColors(adjacent, newColor)

            // Update priority queue
            adjacent.forEach { otherVar ->
                bucketQueue.decrementWeight(otherVar)
            }
        }
        return coloring
    }

    private fun updateForbiddenColors(adjacent: Set<IRVar>, newColor: Color, delta: Int = 1) {
        adjacent.forEach { otherVar ->
            forbiddenColors[otherVar]!!.compute(newColor) { _, count ->
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

        // 1. Try explicit preferences first
        preferences.explicitPreferences[irVar]?.let { preferredColors ->
            for (preferredColor in preferredColors) {
                // Skip avoided colors in preference selection
                val score = allocationScorer(irVar, preferredColor)
                if (score.isAvoided) {
                    continue
                }
                if (preferredColor in allowedNonExtraColors) {
                    unusedColors.remove(preferredColor)
                    return preferredColor
                }
            }
        }

        // 2. Try to match the color of copy-related variables
        preferences.copyRelations[irVar]
            ?.flatMap { otherVar ->
                coloring[otherVar]?.let { listOf(it) }
                    ?: preferences.explicitPreferences[otherVar]
                    ?: emptyList()
            }
            ?.filter { color ->
                if (color !in allowedNonExtraColors) return@filter false
                val score = allocationScorer(irVar, color)
                !score.isAvoided
            }
            ?.minByOrNull { color -> allocationScorer(irVar, color).score }
            ?.let { chosen ->
                unusedColors.remove(chosen)
                return chosen
            }

        // 3. Try to pick a non-extra color (sorted by score)
        if (allowedNonExtraColors.isNotEmpty()) {
            return allowedNonExtraColors.minBy { color -> allocationScorer(irVar, color).score }
        }

        // 4. Try to recolor neighbors to free up a color
        tryRecolorNeighbors(irVar, allowedNonExtraColors)?.let { color ->
            return color
        }

        // 5. Try to pick a used extra color
        val allowedAllColors = colors.filterTo(mutableSetOf()) { it !in forbiddenColors }
        if (allowedAllColors.isNotEmpty()) {
            return allowedAllColors.minBy { color -> allocationScorer(irVar, color).score }
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
                val newColor = allowedRecolors.minBy { color -> allocationScorer(neighbor, color).score }
                unusedColors.remove(newColor)
                coloring[neighbor] = newColor
                val adjacent = graph.edges[neighbor]!!
                updateForbiddenColors(adjacent, oldColor, delta = -1)
                updateForbiddenColors(adjacent, newColor)
                return oldColor
            }
        }

        return null
    }
}