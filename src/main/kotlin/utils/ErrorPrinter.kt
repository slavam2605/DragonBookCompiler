package utils

import org.antlr.v4.runtime.CommonTokenStream
import java.io.PrintStream

object ErrorPrinter {
    private const val LEFT_INDENT = 4

    private fun getInputLine(tokens: CommonTokenStream, line: Int): String {
        val input = tokens.getTokenSource().inputStream.toString()
        val lines = input.split("\n").dropLastWhile { it.isEmpty() }
        return lines[line - 1]
    }

    private fun countLeadingSpaces(line: String): Int = line.takeWhile { it == ' ' }.count()

    private fun getGutter(line: Int): Pair<String, String> {
        val lineNumber = "$line "
        return lineNumber to " ".repeat(lineNumber.length)
    }

    fun printError(tokens: CommonTokenStream, line: Int, start: Int, end: Int, out: PrintStream = System.err) {
        val errorLine = getInputLine(tokens, line)
        val leadingSpaces = countLeadingSpaces(errorLine)
        val (gutter, gutterFiller) = getGutter(line)
        val indent = " ".repeat(LEFT_INDENT)

        out.println("$gutterFiller|")
        out.println("$gutter|$indent${errorLine.substring(leadingSpaces)}")
        out.print("$gutterFiller|")
        if (start >= 0 && end >= 0) {
            out.print(indent)
            out.print(" ".repeat(start - leadingSpaces))
            out.print("^".repeat(end - start + 1))
        }
        out.println()
        out.println()
    }
}