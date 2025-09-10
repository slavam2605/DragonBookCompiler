package compiler.backend.arm64.registerAllocation

import compiler.ir.IRVar

class GraphColoring<Color>(
    private val colors: Set<Color>,
    private val initialColoring: Map<IRVar, Color>,
    private val extraColorProvider: (Int) -> Color
) {
    private val unusedColors = colors.toMutableSet()
    private var extraColorCounter = 0

    fun findColoring(graph: InterferenceGraph): Map<IRVar, Color> {
        val coloring = initialColoring.toMutableMap()
        graph.edges.forEach { (irVar, adjacent) ->
            if (irVar in coloring) {
                return@forEach
            }

            val forbiddenColors = adjacent.mapNotNull { coloring[it] }.toSet()
            val allowedColors = colors - forbiddenColors
            val color = if (allowedColors.isEmpty()) {
                extraColorProvider(extraColorCounter++)
            } else {
                val newColor = allowedColors.firstOrNull { it !in unusedColors }
                    ?: allowedColors.first()
                unusedColors.remove(newColor)
                newColor
            }
            coloring[irVar] = color
        }
        return coloring
    }
}