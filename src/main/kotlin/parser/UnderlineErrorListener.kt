package parser

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import utils.ErrorPrinter

class UnderlineErrorListener : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any?, line: Int,
                             charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        System.err.println("line $line:$charPositionInLine $msg")
        val token = offendingSymbol as Token
        val inputStream = recognizer.inputStream as CommonTokenStream
        val endPositionInLine = token.charPositionInLine + token.stopIndex - token.startIndex
        ErrorPrinter.printError(inputStream, line, token.charPositionInLine, endPositionInLine)
    }
}