package compiler.ir.optimization.inline

import compiler.frontend.FrontendFunction
import compiler.ir.IRLabel
import compiler.ir.IRVar
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.utils.NameAllocator

internal class InlineNamesCollector(
    fn: FrontendFunction<SSAControlFlowGraph>,
    labelAllocator: NameAllocator,
    varAllocator: NameAllocator
) {
    private val newInlinedLabels: Map<IRLabel, IRLabel>
    private val newInlinedVarNames: Map<String, String>

    fun getNewIRLabel(irLabel: IRLabel): IRLabel {
        return newInlinedLabels[irLabel] ?: error("No inlined label for $irLabel")
    }

    fun getNewIRVar(irVar: IRVar): IRVar {
        val newName = newInlinedVarNames[irVar.name] ?: error("No inlined var name for $irVar")
        return IRVar(newName, irVar.ssaVer, irVar.type, irVar.sourceName)
    }

    init {
        val cfg = fn.value

        // Allocate new names for all labels
        newInlinedLabels = cfg.blocks.keys.associateWith {
            val newName = labelAllocator.newName("${it.name}_inlined")
            IRLabel(newName)
        }

        // Collect all defined variable names
        val allFnVarNames = mutableSetOf<String>()
        cfg.blocks.values.forEach { block ->
            block.irNodes.forEach { irNode ->
                irNode.lvalue?.let { allFnVarNames.add(it.name) }
            }
        }
        // Collect function parameters names
        fn.parameters.forEach { allFnVarNames.add(it.name) }

        // Allocate new names for all variables
        newInlinedVarNames = allFnVarNames.associateWith {
            varAllocator.newName("${it}_inlined")
        }
    }
}