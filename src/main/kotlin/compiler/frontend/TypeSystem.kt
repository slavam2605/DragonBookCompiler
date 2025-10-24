package compiler.frontend

sealed interface FrontendType {
    data object Int : FrontendType {
        override fun toString(): String = "int"
    }

    data object Bool : FrontendType {
        override fun toString(): String = "bool"
    }

    data object Float : FrontendType {
        override fun toString(): String = "float"
    }

    data object Void : FrontendType {
        override fun toString(): String = "void"
    }

    data object ErrorType : FrontendType {
        override fun toString(): String = "<unknown type>"
    }

    data class Pointer(val pointeeType: FrontendType) : FrontendType {
        override fun toString(): String = "$pointeeType*"
    }
}

class TypeChecker(private val errorHolder: SemanticAnalysisVisitor) {
    /**
     * Checks types of `result = left op right` where `op` is an operator on numbers (int/float).
     * @return [result] if not null, or a deduced result type otherwise.
     */
    fun checkNumberBinOpTypes(leftLocation: SourceLocation, rightLocation: SourceLocation,
                              left: FrontendType, right: FrontendType, op: String,
                              result: FrontendType? = null): FrontendType {
        if (left is FrontendType.Pointer && (op == "+" || op == "-" || op == "+=" || op == "-=")) {
            return checkPointerBinOpTypes(rightLocation, left, right)
        }

        val leftCheck = checkType(leftLocation, left, FrontendType.Int, FrontendType.Float)
        val rightCheck = checkType(rightLocation, right, FrontendType.Int, FrontendType.Float)
        val deducedType = when {
            !leftCheck || !rightCheck -> FrontendType.ErrorType
            left == FrontendType.Float || right == FrontendType.Float -> FrontendType.Float
            else -> FrontendType.Int
        }
        if (result != null) {
            checkType(leftLocation, deducedType, result)
            return result
        }
        return deducedType
    }

    fun checkPointerBinOpTypes(rightLocation: SourceLocation, left: FrontendType, right: FrontendType): FrontendType {
        val rightCheck = checkType(rightLocation, right, FrontendType.Int)
        if (!rightCheck) return FrontendType.ErrorType
        return left
    }

    fun checkType(location: SourceLocation, type: FrontendType, vararg expected: FrontendType): Boolean {
        if (type == FrontendType.ErrorType || expected.singleOrNull() == FrontendType.ErrorType) return true
        if (type !in expected) {
            errorHolder.addError(MismatchedTypeException(location, expected.toList(), type))
            return false
        }
        return true
    }
}