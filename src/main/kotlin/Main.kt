import compiler.frontend.CompileToIRVisitor
import compiler.ir.cfg.ControlFlowGraph
import compiler.ir.printToString
import org.antlr.v4.runtime.*
import org.example.parser.UnderlineErrorListener
import java.io.File

fun main(args: Array<String>) {
    val input = File(args[0]).readText()
    val lexer = MainLexer(CharStreams.fromString(input))
    val parser = MainGrammar(CommonTokenStream(lexer)).apply {
        removeErrorListeners()
        addErrorListener(UnderlineErrorListener())
    }
    val tree = parser.program()
    val ir = CompileToIRVisitor().compileToIR(tree)
    val cfg = ControlFlowGraph.build(ir)

    println("${cfg.root.printToString()}:")
    cfg.nodes[cfg.root]!!.irNodes.forEach {
        println("\t${it.printToString()}")
    }
    cfg.nodes.forEach { (label, block) ->
        if (label == cfg.root) return@forEach
        println("${label.printToString()}:")
        block.irNodes.forEach {
            println("\t${it.printToString()}")
        }
    }
}