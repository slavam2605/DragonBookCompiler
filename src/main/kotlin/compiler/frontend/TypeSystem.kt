package compiler.frontend

enum class FrontendType {
    INT, BOOL, FLOAT, ERROR_TYPE
}

class TypeChecker(private val errorHolder: SemanticAnalysisVisitor) {
    /**
     * Checks types of `result = left op right` where `op` is an operator on numbers (int/float).
     * @return [result] if not null, or a deduced result type otherwise.
     */
    fun checkNumberBinOpTypes(leftLocation: SourceLocation, rightLocation: SourceLocation,
                              left: FrontendType, right: FrontendType,
                              result: FrontendType? = null): FrontendType {
        val leftCheck = checkType(leftLocation, left, FrontendType.INT, FrontendType.FLOAT)
        val rightCheck = checkType(rightLocation, right, FrontendType.INT, FrontendType.FLOAT)
        val deducedType = when {
            !leftCheck || !rightCheck -> FrontendType.ERROR_TYPE
            left == FrontendType.FLOAT || right == FrontendType.FLOAT -> FrontendType.FLOAT
            else -> FrontendType.INT
        }
        if (result != null) {
            checkType(leftLocation, deducedType, result)
            return result
        }
        return deducedType
    }

    fun checkType(location: SourceLocation, type: FrontendType, vararg expected: FrontendType): Boolean {
        if (type == FrontendType.ERROR_TYPE || expected.singleOrNull() == FrontendType.ERROR_TYPE) return true
        if (type !in expected) {
            errorHolder.addError(MismatchedTypeException(location, expected.toList(), type))
            return false
        }
        return true
    }
}