package compiler.ir.optimization.inline

import compiler.frontend.FrontendFunction
import compiler.ir.*
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ssa.SSAControlFlowGraph
import compiler.ir.cfg.utils.advanceAfterAllLabels
import compiler.ir.cfg.utils.advanceAfterAllVars
import compiler.utils.NameAllocator

internal class InlineTransformer(
    private val inlinableFunctions: Map<String, FrontendFunction<SSAControlFlowGraph>>,
    private val cfg: SSAControlFlowGraph,
    private val functionName: String
) {
    private val varAllocator = NameAllocator("x")
    private val labelAllocator = NameAllocator("L")
    private val newBlocks = mutableMapOf<IRLabel, CFGBlock>()

    // Track blocks that were split due to inlining: originalLabel -> continuationLabel
    // This is needed to update phi-node sources in successor blocks
    private val splitBlocks = mutableMapOf<IRLabel, IRLabel>()

    init {
        varAllocator.advanceAfterAllVars(cfg)
        labelAllocator.advanceAfterAllLabels(cfg)
    }

    fun transform(): SSAControlFlowGraph {
        cfg.blocks.forEach { (label, block) ->
            transformBlock(label, block)
        }

        updatePhiNodeSources()
        return cfg.new(cfg.root, newBlocks)
    }

    private fun transformBlock(startLabel: IRLabel, block: CFGBlock) {
        var currentLabel = startLabel
        var currentBlock = mutableListOf<IRNode>()

        block.irNodes.forEach { originalNode ->
            if (originalNode !is IRFunctionCall) {
                currentBlock.add(originalNode)
                return@forEach
            }

            val fn = inlinableFunctions[originalNode.name] ?: run {
                currentBlock.add(originalNode)
                return@forEach
            }

            // Internal annotation for tests: do not inline `fn` to `functionName`
            if (fn.hasAnnotation("noinline_$functionName")) {
                currentBlock.add(originalNode)
                return@forEach
            }

            val inlinedReturnLabel = IRLabel(labelAllocator.newName("${fn.name}_inlined_return"))
            val namesCollector = InlineNamesCollector(fn, labelAllocator, varAllocator)
            val returnValueSources = mutableListOf<IRSource>()

            // Rename all labels and variables in the inlined function
            val renamedFn = fn.value.transform(InlineRenamer(
                namesCollector = namesCollector,
                originalCallNode = originalNode,
                inlinedReturnLabel = inlinedReturnLabel,
                returnValueSources = returnValueSources
            ))

            // Rename blocks themselves
            renamedFn.blocks.forEach { (label, block) ->
                val newLabel = namesCollector.getNewIRLabel(label)
                check(newLabel !in newBlocks) { "Block $newLabel already exists" }
                newBlocks[newLabel] = block
            }

            // Assign renamed inlined parameters
            val newParameters = fn.parameters.map { namesCollector.getNewIRVar(it) }
            newParameters.forEachIndexed { index, param ->
                currentBlock.add(IRAssign(param, originalNode.arguments[index]))
            }

            // Insert jump to the start of the inlined function
            currentBlock.add(IRJump(namesCollector.getNewIRLabel(fn.value.root)))

            // End the current block and start a continuation
            newBlocks[currentLabel] = CFGBlock(currentBlock)
            currentLabel = inlinedReturnLabel
            currentBlock = mutableListOf()

            // Insert a phi-node for merging return values if needed
            if (originalNode.lvalue != null) {
                check(returnValueSources.isNotEmpty())
                currentBlock.add(IRPhi(originalNode.lvalue!!, returnValueSources))
            }
        }

        newBlocks[currentLabel] = CFGBlock(currentBlock)

        // If this block was split due to inlining, record the mapping
        // from the original label to the last continuation label (after all inlines)
        if (currentLabel != startLabel) {
            splitBlocks[startLabel] = currentLabel
        }
    }

    private fun updatePhiNodeSources() {
        // Update phi-node sources to account for split blocks
        // If block A was split into A -> inlined code -> A', then phi-nodes
        // that referenced A should now reference A'
        newBlocks.entries.forEach { (label, block) ->
            var needsUpdate = false
            val updatedNodes = block.irNodes.map { node ->
                if (node !is IRPhi) return@map node

                val updatedSources = node.sources.map { source ->
                    val actualFrom = splitBlocks[source.from] ?: source.from
                    if (actualFrom != source.from) {
                        needsUpdate = true
                    }
                    IRSource(actualFrom, source.value)
                }
                IRPhi(node.result, updatedSources)
            }

            if (needsUpdate) {
                newBlocks[label] = CFGBlock(updatedNodes)
            }
        }
    }
}