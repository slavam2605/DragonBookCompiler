package org.example.parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token

class UnderlineErrorListener : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any?, line: Int,
                             charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        System.err.println("line $line:$charPositionInLine $msg")
        underlineError(recognizer, offendingSymbol as Token, line, charPositionInLine)
    }

    private fun underlineError(recognizer: Recognizer<*, *>, offendingToken: Token,
                               line: Int, charPositionInLine: Int) {
        val tokens = recognizer.inputStream as CommonTokenStream
        val input = tokens.getTokenSource().inputStream.toString()
        val lines = input.split("\n").dropLastWhile { it.isEmpty() }
        val errorLine = lines[line - 1]
        System.err.println(errorLine)
        repeat(charPositionInLine) {
            System.err.print(" ")
        }
        val start = offendingToken.startIndex
        val stop = offendingToken.stopIndex
        if (start >= 0 && stop >= 0) {
            repeat(stop - start + 1) {
                System.err.print("^")
            }
        }
        System.err.println()
    }
}