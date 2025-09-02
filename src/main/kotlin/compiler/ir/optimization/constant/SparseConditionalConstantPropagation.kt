package compiler.ir.optimization.constant

import compiler.ir.*
import compiler.ir.cfg.extensions.IRUse
import compiler.ir.cfg.extensions.SSAGraph
import compiler.ir.cfg.ssa.SSAControlFlowGraph

class SparseConditionalConstantPropagation(private val cfg: SSAControlFlowGraph) {
    private val defValue = mutableMapOf<IRVar, SSCPValue>()
    private val useValue = mutableMapOf<IRUse, SSCPValue>()
    private val cfgWorklist = mutableListOf<CFGEdge>()
    private val ssaWorklist = mutableListOf<SSAEdge>()
    private val isExecuted = mutableSetOf<CFGEdge>()
    private val ssaGraph = SSAGraph.get(cfg)

    private data class CFGEdge(val from: IRLabel, val to: IRLabel)
    private data class SSAEdge(val def: IRNode, val use: IRUse)

    private fun defValue(irVar: IRVar) = defValue[irVar] ?: SSCPValue.Top
    private fun useValue(irUse: IRUse) = useValue[irUse] ?: SSCPValue.Top

    val staticValues: Map<IRVar, SSCPValue>
        get() = defValue.toMap()

    fun run(): SSAControlFlowGraph {
        cfgWorklist.add(CFGEdge(IRLabel("<fake-entry>"), cfg.root))

        while (cfgWorklist.isNotEmpty() || ssaWorklist.isNotEmpty()) {
            cfgWorklist.removeLastOrNull()?.let { cfgEdge ->
                handleCFGEdge(cfgEdge)
            }

            ssaWorklist.removeLastOrNull()?.let { ssaEdge ->
                handleSSAEdge(ssaEdge)
            }
        }

        return FoldConstantExpressions.run(cfg, defValue)
    }

    private fun handleCFGEdge(cfgEdge: CFGEdge) {
        if (cfgEdge in isExecuted) {
            return
        }

        isExecuted.add(cfgEdge)
        evaluateAllPhisInBlock(cfgEdge)
        if (!isBlockFirstVisit(cfgEdge)) {
            return
        }

        val block = cfg.blocks[cfgEdge.to]!!
        for (irNode in block.irNodes) {
            when (irNode) {
                is IRPhi -> continue // Phi nodes are handled separately in `evaluateAllPhisInBlock`
                is IRJumpNode -> when (irNode) {
                    is IRJumpIfTrue -> {
                        evaluateConditional(cfgEdge.to, irNode)
                    }
                    is IRJump -> {
                        cfgWorklist.add(CFGEdge(cfgEdge.to, irNode.target))
                    }
                    is IRReturn -> { /* ignore */ }
                }
                else if irNode.lvalue != null -> evaluateAssign(cfgEdge.to, irNode)
                else -> { /* do nothing */ }
            }
        }
    }

    private fun handleSSAEdge(ssaEdge: SSAEdge) {
        val (_, ssaUse) = ssaEdge
        val isAnyExecuted = cfg.backEdges(ssaUse.blockLabel).any { pred ->
            CFGEdge(pred, ssaUse.blockLabel) in isExecuted
        }
        if (!isAnyExecuted) {
            return
        }

        when {
            ssaUse.node is IRPhi -> {
                evaluatePhi(ssaUse.blockLabel, ssaUse.node)
            }
            ssaUse.node is IRJumpIfTrue -> {
                evaluateConditional(ssaUse.blockLabel, ssaUse.node)
            }
            ssaUse.node.lvalue != null -> {
                evaluateAssign(ssaUse.blockLabel, ssaUse.node)
            }
            ssaUse.node is IRFunctionCall || ssaUse.node is IRReturn -> { /* do nothing */ }
            else -> error("Unexpected IR node: ${ssaUse.node.printToString()}")
        }
    }

    private fun isBlockFirstVisit(cfgEdge: CFGEdge): Boolean {
        val (cfgFrom, cfgTo) = cfgEdge
        check(isExecuted.contains(cfgEdge))
        cfg.backEdges(cfgTo).forEach { cfgOtherFrom ->
            if (cfgOtherFrom == cfgFrom) return@forEach
            if (CFGEdge(cfgOtherFrom, cfgTo) in isExecuted) return false
        }
        return true
    }

    private fun evaluateAllPhisInBlock(cfgEdge: CFGEdge) {
        val block = cfg.blocks[cfgEdge.to]!!
        block.irNodes.filterIsInstance<IRPhi>().forEach {
            evaluateOperands(cfgEdge.to, it)
        }
        block.irNodes.filterIsInstance<IRPhi>().forEach {
            evaluateResult(cfgEdge.to, it)
        }
    }

    private fun evaluatePhi(blockLabel: IRLabel, irPhi: IRPhi) {
        evaluateOperands(blockLabel, irPhi)
        evaluateResult(blockLabel, irPhi)
    }

    private fun evaluateOperands(blockLabel: IRLabel, irPhi: IRPhi) {
        val irVar = irPhi.result
        val varValue = defValue(irVar)
        if (varValue is SSCPValue.Bottom) {
            return
        }

        irPhi.sources.forEachIndexed { index, (from, sourceValue) ->
            val cfgEdge = CFGEdge(from, blockLabel)
            val defValue = sourceValue.evaluateOneValue { defValue(it) }
            if (cfgEdge in isExecuted) {
                val irUse = IRUse(blockLabel, irPhi, index)
                useValue[irUse] = defValue.also {
                    check(it <= (useValue[irUse] ?: SSCPValue.Top))
                }
            }
        }
    }

    private fun evaluateResult(blockLabel: IRLabel, irPhi: IRPhi) {
        val irVar = irPhi.result
        val varValue = defValue(irVar)
        if (varValue is SSCPValue.Bottom) {
            return
        }

        val phiValues = irPhi.rvalues().mapIndexed { index, rValue ->
            rValue.evaluateOneValue {
                useValue(IRUse(blockLabel, irPhi, index))
            }
        }
        val newValue = phiValues.reduce(SSCPValue::times)
        if (varValue == newValue) {
            return
        }

        defValue[irVar] = newValue.also {
            check(it <= varValue)
        }
        ssaGraph.uses(irPhi).forEach { irUse ->
            ssaWorklist.add(SSAEdge(irPhi, irUse))
        }
    }

    private fun evaluateAssign(blockLabel: IRLabel, irNode: IRNode) {
        irNode.rvalues().forEachIndexed { index, rValue ->
            val irUse = IRUse(blockLabel, irNode, index)
            val oldValue = useValue(irUse)

            useValue[irUse] = rValue.evaluateOneValue { defValue(it) }
                .also { check(it <= oldValue) }
        }

        val lVar = checkNotNull(irNode.lvalue)
        val oldValue = defValue(lVar)
        if (oldValue is SSCPValue.Bottom) {
            return
        }

        val rValues = irNode.rvalues().mapIndexed { index, rValue ->
            rValue.evaluateOneValue { useValue(IRUse(blockLabel, irNode, index)) }
        }
        val newValue = irNode.evaluateSafe(rValues)
        if (newValue == oldValue) {
            return
        }

        check(newValue <= oldValue)
        defValue[lVar] = newValue
        ssaGraph.uses(irNode).forEach { irUse ->
            ssaWorklist.add(SSAEdge(irNode, irUse))
        }
    }

    private fun evaluateConditional(blockLabel: IRLabel, jump: IRJumpIfTrue) {
        val irUse = IRUse(blockLabel, jump, 0)
        val irUseValue = useValue(irUse)
        if (irUseValue is SSCPValue.Bottom) {
            return
        }

        val condDefValue = jump.cond.evaluateOneValue { defValue(it) }
        if (irUseValue == condDefValue) {
            return
        }

        useValue[irUse] = condDefValue.also {
            check(it <= irUseValue)
        }
        when (condDefValue) {
            is SSCPValue.Bottom -> {
                cfgWorklist.add(CFGEdge(blockLabel, jump.target))
                cfgWorklist.add(CFGEdge(blockLabel, jump.elseTarget))
            }
            is SSCPValue.Value -> {
                val value = condDefValue.value
                check(value == 0L || value == 1L) { "Boolean values must be either 0 or 1, got $value" }
                val target = if (value == 1L) jump.target else jump.elseTarget
                cfgWorklist.add(CFGEdge(blockLabel, target))
            }
            is SSCPValue.Top -> { /* do nothing */ }
        }.let { /* exhaustive check */ }
    }
}