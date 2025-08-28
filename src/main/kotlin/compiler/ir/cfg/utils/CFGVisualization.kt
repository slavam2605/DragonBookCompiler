package compiler.ir.cfg.utils

import compiler.ir.IRJump
import compiler.ir.IRJumpIfTrue
import compiler.ir.IRLabel
import compiler.ir.printToString
import compiler.ir.cfg.CFGBlock
import compiler.ir.cfg.ControlFlowGraph

// Shared helpers for CFG visualization
private data class PreparedGraph(
    val labels: List<IRLabel>,
    val idMap: Map<IRLabel, String>,
    val headerAndBody: Map<IRLabel, List<String>>
)

private fun ControlFlowGraph.prepareCommon(
    esc: (String) -> String,
    nb: (String) -> String
): PreparedGraph {
    val labels = buildList {
        add(root)
        addAll(blocks.keys.filter { it != root }.sortedBy { it.name })
    }
    val idMap = labels.withIndex().associate { (idx, lbl) ->
        val base = lbl.name.replace("[^A-Za-z0-9_]".toRegex(), "_")
        val safe = if (base.isEmpty() || base.first().isDigit()) "n_${idx}_$base" else base
        lbl to safe
    }
    val headerAndBody = labels.associateWith { label ->
        val block = blocks[label]
        val header = label.printToString().trimEnd(':')
        buildList {
            add(nb(esc(header)))
            block?.irNodes?.forEach { ir ->
                add(nb(esc(ir.printToString())))
            }
        }
    }
    return PreparedGraph(labels, idMap, headerAndBody)
}

// Extension: Mermaid representation extracted from ControlFlowGraph
fun ControlFlowGraph.toMermaid(): String {
    fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("[", "\\[")
        .replace("]", "\\]")
    fun nb(s: String): String = s
        .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
        .replace(" ", "&nbsp;")

    val (labels, idMap, headerAndBody) = prepareCommon(::esc, ::nb)
    val sb = StringBuilder()
    sb.appendLine("flowchart TD")
    // Nodes
    for (label in labels) {
        val nodeId = idMap[label]!!
        val content = headerAndBody[label]!!.joinToString("<br/>")
        sb.appendLine("    $nodeId[\"$content\"]")
    }
    // Edges
    for (label in labels) {
        val block: CFGBlock = blocks[label] ?: continue
        if (block.irNodes.isEmpty()) continue
        val last = block.irNodes.last()
        val fromId = idMap[label]!!
        when (last) {
            is IRJumpIfTrue -> {
                sb.appendLine("    $fromId -->|${nb(esc(last.cond.printToString()))}| ${idMap[last.target]}")
                sb.appendLine("    $fromId -->|${nb(esc("!" + last.cond.printToString()))}| ${idMap[last.elseTarget]}")
            }
            is IRJump -> sb.appendLine("    $fromId --> ${idMap[last.target]}")
            else -> {}
        }
    }
    return sb.toString()
}

// Extension: Graphviz DOT representation extracted from ControlFlowGraph
fun ControlFlowGraph.toGraphviz(): String {
    fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    fun nb(s: String): String = s
        .replace("\t", "&#160;&#160;&#160;&#160;")
        .replace(" ", "&#160;")

    val (labels, idMap, headerAndBody) = prepareCommon(::esc, ::nb)
    val sb = StringBuilder()
    sb.appendLine("digraph CFG {")
    sb.appendLine("    rankdir=TB;")
    sb.appendLine("    node [shape=box];")
    // Nodes
    for (label in labels) {
        val nodeId = idMap[label]!!
        val content = headerAndBody[label]!!.joinToString("<BR/>")
        sb.appendLine("    $nodeId [label=<$content>];")
    }
    // Edges
    for (label in labels) {
        val block: CFGBlock = blocks[label] ?: continue
        if (block.irNodes.isEmpty()) continue
        val last = block.irNodes.last()
        val fromId = idMap[label]!!
        when (last) {
            is IRJumpIfTrue -> {
                val trueLbl = nb(esc(last.cond.printToString()))
                val falseLbl = nb(esc("!" + last.cond.printToString()))
                sb.appendLine("    $fromId -> ${idMap[last.target]} [label=\"$trueLbl\"];")
                sb.appendLine("    $fromId -> ${idMap[last.elseTarget]} [label=\"$falseLbl\"];")
            }
            is IRJump -> sb.appendLine("    $fromId -> ${idMap[last.target]};")
            else -> {}
        }
    }
    sb.appendLine("}")
    return sb.toString()
}
