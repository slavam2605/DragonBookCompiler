package compiler.ir

import compiler.backend.arm64.registerAllocation.InterferenceGraph
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.cfg.utils.toGraphviz
import kotlin.collections.component1
import kotlin.collections.component2

fun IRLabel.printToString(): String = name

fun IRValue.printToString(): String = when (this) {
    is IRVar -> "$name.${ssaVer}"
    is IRInt -> value.toString()
    is IRFloat -> value.toString()
}

fun IRBinOpKind.printToString(): String = when (this) {
    IRBinOpKind.ADD -> "+"
    IRBinOpKind.SUB -> "-"
    IRBinOpKind.MUL -> "*"
    IRBinOpKind.DIV -> "/"
    IRBinOpKind.MOD -> "%"
    IRBinOpKind.EQ -> "=="
    IRBinOpKind.NEQ -> "!="
    IRBinOpKind.GT -> ">"
    IRBinOpKind.GE -> ">="
    IRBinOpKind.LT -> "<"
    IRBinOpKind.LE -> "<="
}

fun IRSource.printToString(): String = "${from.printToString()}: ${value.printToString()}"

fun IRProtoNode.printToString(): String = when (this) {
    is IRLabel -> "$name:"
    is IRPhi -> "${result.printToString()} = phi(${sources.joinToString(", ") { it.printToString() }})"
    is IRAssign -> "${result.printToString()} = ${right.printToString()}"
    is IRBinOp -> "${result.printToString()} = ${left.printToString()} ${op.printToString()} ${right.printToString()}"
    is IRNot -> "${result.printToString()} = ! ${value.printToString()}"
    is IRFunctionCall -> (if (result != null) "${result.printToString()} = " else "") + "$name(${arguments.joinToString(", ") { it.printToString() }})"
    is IRJump -> "jump ${target.printToString()}"
    is IRJumpIfTrue -> "jump-if-true ${cond.printToString()} ${target.printToString()} else ${elseTarget.printToString()}"
    is IRReturn -> "return${if (value != null) " ${value.printToString()}" else ""}"
}

fun List<IRProtoNode>.print() {
    forEach { println(it.printToString()) }
}

fun ControlFlowGraph.print(useGraphviz: Boolean = false) {
    if (useGraphviz) {
        println(toGraphviz())
        println()
        return
    }

    println("${root.printToString()}:")
    blocks[root]!!.irNodes.forEach {
        println("\t${it.printToString()}")
    }
    blocks.forEach { (label, block) ->
        if (label == root) return@forEach
        println("${label.printToString()}:")
        block.irNodes.forEach {
            println("\t${it.printToString()}")
        }
    }
    println()
}

fun InterferenceGraph.print(useGraphviz: Boolean = true) {
    println("Interference graph:")

    if (useGraphviz) {
        println("graph G {")
        println("\tlayout=neato;")
        println("\toverlap=false;")
        println("\tedge [len=3];")
        edges.forEach { (from, tos) ->
            tos.forEach {
                if (it.printToString() < from.printToString()) return@forEach
                println("\t\"${from.printToString()}\" -- \"${it.printToString()}\"")
            }
        }
        println("}")
        println()
        return
    }

    edges.forEach { (from, tos) ->
        println("\t${from.printToString()} -> ${tos.joinToString(", ") { it.printToString() }}")
    }
    if (edges.isEmpty()) println("\tEmpty")
    println()
}