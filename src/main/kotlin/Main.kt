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
    parser.program()
}