package parser

import MainGrammar
import MainLexer
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.util.function.Supplier

object ParserFlow {
    fun parseFile(file: File): Pair<CommonTokenStream, Supplier<MainGrammar.ProgramContext>> {
        val stream = CharStreams.fromPath(file.toPath())
        return parse(stream)
    }

    fun parseString(input: String): Pair<CommonTokenStream, Supplier<MainGrammar.ProgramContext>> {
        val stream = CharStreams.fromString(input)
        return parse(stream)
    }

    private fun parse(charStream: CharStream): Pair<CommonTokenStream, Supplier<MainGrammar.ProgramContext>> {
        val lexer = MainLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val errorListener = UnderlineErrorListener()
        val parser = MainGrammar(tokens).apply {
            removeErrorListeners()
            addErrorListener(errorListener)
        }
        return tokens to Supplier {
            parser.program().also {
                errorListener.throwIfAnyErrors()
            }
        }
    }
}