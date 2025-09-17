package parser

import MainGrammar
import MainLexer
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

object ParserFlow {
    fun parseFile(file: File): Triple<MainGrammar, CommonTokenStream, MainGrammar.ProgramContext> {
        val stream = CharStreams.fromPath(file.toPath())
        return parse(stream)
    }

    fun parseString(input: String): Triple<MainGrammar, CommonTokenStream, MainGrammar.ProgramContext> {
        val stream = CharStreams.fromString(input)
        return parse(stream)
    }

    private fun parse(charStream: CharStream): Triple<MainGrammar, CommonTokenStream, MainGrammar.ProgramContext> {
        val lexer = MainLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = MainGrammar(tokens).apply {
            removeErrorListeners()
            addErrorListener(UnderlineErrorListener())
        }
        val tree = parser.program()
        return Triple(parser, tokens, tree)
    }
}