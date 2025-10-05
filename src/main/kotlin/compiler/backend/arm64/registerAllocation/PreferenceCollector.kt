package compiler.backend.arm64.registerAllocation

import compiler.backend.arm64.Register
import compiler.ir.*
import compiler.ir.cfg.ControlFlowGraph

/**
 * Collects register allocation preferences from a control flow graph.
 *
 * This includes:
 * - Function call argument preferences (arguments should prefer their target parameter registers)
 * - Function call return value preferences (results should prefer x0 for int, d0 for float)
 * - Copy preferences (for x = y, both should prefer the same register)
 */
object PreferenceCollector {
    fun <Reg : Register> collect(
        cfg: ControlFlowGraph,
        typeFilter: (IRValue) -> Boolean,
        parameterRegs: List<Reg>
    ): ColoringPreferences<Reg> {
        val hardRequirements = mutableListOf<Pair<IRVar, Reg>>()
        val explicitPreferences = mutableMapOf<IRVar, MutableList<Reg>>()
        val copyRelations = mutableMapOf<IRVar, MutableSet<IRVar>>()

        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { node ->
                when (node) {
                    is IRFunctionCall -> collectCallPreferences(node, typeFilter, parameterRegs,
                        explicitPreferences, hardRequirements)
                    is IRAssign -> collectCopyPreferences(node, typeFilter, copyRelations)
                    else -> {}
                }
            }
        }

        return ColoringPreferences(
            hardRequirements = hardRequirements,
            explicitPreferences = explicitPreferences,
            copyRelations = copyRelations
        )
    }

    private fun <Reg : Register> collectCallPreferences(
        call: IRFunctionCall,
        typeFilter: (IRValue) -> Boolean,
        parameterRegs: List<Reg>,
        explicitPreferences: MutableMap<IRVar, MutableList<Reg>>,
        hardRequirements: MutableList<Pair<IRVar, Reg>>
    ) {
        // Add preferences for function call arguments
        call.arguments.filter(typeFilter).forEachIndexed { index, arg ->
            if (arg !is IRVar) return@forEachIndexed

            val targetReg = parameterRegs[index]
            hardRequirements.add(arg to targetReg)
        }

        // Add preference for a function call return value
        call.result?.let { result ->
            if (!typeFilter(result)) return@let

            val returnReg = when (result.type) {
                IRType.INT64, IRType.FLOAT64 -> parameterRegs[0]  // x0 or d0
            }

            explicitPreferences
                .getOrPut(result) { mutableListOf() }
                .add(returnReg)
        }
    }

    private fun collectCopyPreferences(
        assign: IRAssign,
        typeFilter: (IRVar) -> Boolean,
        copyRelations: MutableMap<IRVar, MutableSet<IRVar>>
    ) {
        val right = assign.right
        if (right !is IRVar) return
        if (!typeFilter(assign.result)) return
        check(typeFilter(right))

        // Add symmetric copy relation
        copyRelations.getOrPut(assign.result) { mutableSetOf() }.add(right)
        copyRelations.getOrPut(right) { mutableSetOf() }.add(assign.result)
    }
}
