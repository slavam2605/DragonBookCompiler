package ir.interpreter

import java.lang.RuntimeException

sealed class InterpreterException(message: String) : RuntimeException(message)

class ExceededEvaluationStepsException(maxSteps: Int) : InterpreterException(
    "Exceeded maximum number of allowed evaluation steps: $maxSteps. Possible infinite loop."
)