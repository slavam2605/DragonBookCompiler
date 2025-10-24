package compiler.frontend

import MainGrammar
import MainGrammarBaseVisitor
import compiler.ir.Intrinsics
import org.antlr.v4.runtime.ParserRuleContext

// TODO store symbol tables in nodes as a tree property and reuse in IR generation
class SemanticAnalysisVisitor : MainGrammarBaseVisitor<FrontendType>() {
    private var loopDepth: Int = 0
    private val symbolTable = SymbolTable<FrontendType>()
    private val functionTable = mutableMapOf<String, FunctionDescriptor>()
    private val errors = mutableListOf<CompilationException>()
    private val typeChecker = TypeChecker(this)

    fun analyze(tree: MainGrammar.ProgramContext) {
        visit(tree)
        if (errors.isNotEmpty()) {
            throw CompilationFailed(errors)
        }
    }

    fun addError(error: CompilationException) {
        errors.add(error)
    }

    private fun ParserRuleContext.defaultVisitChildren(): Nothing? {
        children?.forEach { visit(it) }
        return null
    }

    override fun visitProgram(ctx: MainGrammar.ProgramContext): Nothing? {
        // Build function table before visiting functions
        ctx.function().forEach { functionContext ->
            val name = functionContext.ID().text
            val returnType = functionContext.type()?.let { visit(it) }
            val arguments = functionContext
                .functionParameters()
                ?.functionParameter()
                ?.map { parameterContext ->
                    ArgumentDescriptor(
                        name = parameterContext.ID().text,
                        type = visit(parameterContext.type())
                    )
                } ?: emptyList()
            val descriptor = FunctionDescriptor(name, arguments, returnType)
            functionTable.put(name, descriptor)?.let { oldValue ->
                errors.add(FunctionRedeclarationException(functionContext.ID().asLocation(), name))
            }
        }

        return ctx.defaultVisitChildren()
    }

    override fun visitFunction(ctx: MainGrammar.FunctionContext): Nothing? {
        symbolTable.withScope {
            val descriptor = functionTable[ctx.ID().text]!!
            descriptor.arguments.forEach { arg ->
                symbolTable.define(arg.name, arg.type)
            }

            visit(ctx.block())
        }
        return null
    }

    // --------------- Types ---------------

    override fun visitType(ctx: MainGrammar.TypeContext): FrontendType {
        val baseTypeName = ctx.ID().text
        val baseType = when (baseTypeName) {
            "int" -> FrontendType.Int
            "bool" -> FrontendType.Bool
            "float" -> FrontendType.Float
            else -> {
                errors.add(UnknownTypeException(ctx.asLocation(), ctx.text))
                return FrontendType.ErrorType
            }
        }

        // Count the number of STAR tokens to build the pointer type
        val pointerDepth = ctx.STAR().size
        var resultType = baseType
        repeat(pointerDepth) {
            resultType = FrontendType.Pointer(resultType)
        }
        return resultType
    }

    // --------------- Statements ---------------

    override fun visitStatement(ctx: MainGrammar.StatementContext): Nothing? {
        return ctx.defaultVisitChildren()
    }

    override fun visitBreakStatement(ctx: MainGrammar.BreakStatementContext): Nothing? {
        if (loopDepth == 0) {
            errors.add(SyntaxErrorException(ctx.asLocation(), "'break' is not allowed outside loops"))
        }
        return null
    }

    override fun visitContinueStatement(ctx: MainGrammar.ContinueStatementContext): Nothing? {
        if (loopDepth == 0) {
            errors.add(SyntaxErrorException(ctx.asLocation(), "'continue' is not allowed outside loops"))
        }
        return null
    }

    override fun visitReturnStatement(ctx: MainGrammar.ReturnStatementContext): Nothing? {
        // For now, 'return' can appear anywhere and simply terminates execution.
        // When functions are added, this should enforce placement and type rules.
        ctx.expression()?.let { visit(it) }
        return null
    }

    override fun visitFunctionCall(ctx: MainGrammar.FunctionCallContext): Nothing? {
        visitCall(ctx, isStatement = true)
        return null
    }

    override fun visitBlock(ctx: MainGrammar.BlockContext): Nothing? {
        return symbolTable.withScope {
            ctx.defaultVisitChildren()
        }
    }

    override fun visitDeclaration(ctx: MainGrammar.DeclarationContext): Nothing? {
        val declType = visit(ctx.type())
        if (symbolTable.define(ctx.ID().text, declType) != null) {
            errors.add(VariableRedeclarationException(ctx.ID().asLocation(), ctx.ID().text))
        }
        if (ctx.ASSIGN() != null) {
            visit(ctx.expression()).checkType(ctx.expression(), declType)
        }
        return null
    }

    override fun visitAssignment(ctx: MainGrammar.AssignmentContext): Nothing? {
        val leftType = visit(ctx.lvalue())
        val opText = ctx.op.text
        val rightType = visit(ctx.expression())
        if (leftType == FrontendType.ErrorType) return null

        if (opText == "=") {
            rightType.checkType(ctx.expression(), leftType)
        } else {
            check(opText in setOf("+=", "-=", "*=", "/=", "%="))
            typeChecker.checkNumberBinOpTypes(
                leftLocation = ctx.lvalue().asLocation(),
                rightLocation = ctx.expression().asLocation(),
                left = leftType,
                right = rightType,
                op = opText,
                result = leftType
            )
        }
        return null
    }

    override fun visitIdLValue(ctx: MainGrammar.IdLValueContext): FrontendType {
        return visitId(ctx.ID().text, ctx.ID().asLocation())
    }

    override fun visitDerefLValue(ctx: MainGrammar.DerefLValueContext): FrontendType {
        return visitDeref(ctx.expression())
    }

    override fun visitIfStatement(ctx: MainGrammar.IfStatementContext): Nothing? {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.Bool)
        symbolTable.withScope {
            visit(ctx.ifTrue)
        }
        if (ctx.ifFalse != null) {
            symbolTable.withScope {
                visit(ctx.ifFalse)
            }
        }
        return null
    }

    override fun visitWhileStatement(ctx: MainGrammar.WhileStatementContext): Nothing? {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.Bool)
        withLoopLevel {
            symbolTable.withScope {
                visit(ctx.statement())
            }
        }
        return null
    }

    override fun visitDoWhileStatement(ctx: MainGrammar.DoWhileStatementContext): Nothing? {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.Bool)
        withLoopLevel {
            symbolTable.withScope {
                visit(ctx.statement())
            }
        }
        return null
    }

    override fun visitForStatement(ctx: MainGrammar.ForStatementContext): Nothing? {
        symbolTable.withScope {
            (ctx.initAssign ?: ctx.initDecl)?.let { visit(it) }
            ctx.cond?.let { visit(it).checkType(it, FrontendType.Bool) }
            ctx.inc?.let { visit(it) }
            withLoopLevel {
                symbolTable.withScope {
                    visit(ctx.statement())
                }
            }
        }
        return null
    }

    // --------------- Expressions ---------------

    override fun visitTrueExpr(ctx: MainGrammar.TrueExprContext): FrontendType {
        return FrontendType.Bool
    }

    override fun visitFalseExpr(ctx: MainGrammar.FalseExprContext): FrontendType {
        return FrontendType.Bool
    }

    override fun visitCallExpr(ctx: MainGrammar.CallExprContext): FrontendType {
        val call = ctx.functionCall()
        return visitCall(call, isStatement = false)!!
    }

    override fun visitMulDivExpr(ctx: MainGrammar.MulDivExprContext): FrontendType {
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "*", "/", "%")
        return visitNumberBinOp(ctx.left, ctx.right, ctx.op.text)
    }

    override fun visitIdExpr(ctx: MainGrammar.IdExprContext): FrontendType {
        return visitId(ctx.ID().text, ctx.ID().asLocation())
    }

    override fun visitComparisonExpr(ctx: MainGrammar.ComparisonExprContext): FrontendType {
        val left = visit(ctx.left)
        val right = visit(ctx.right)
        when (left) {
            FrontendType.Int, FrontendType.Float ->
                right.checkType(ctx.right, FrontendType.Int, FrontendType.Float)
            FrontendType.Bool if (ctx.op.text == "==" || ctx.op.text == "!=") ->
                right.checkType(ctx.right, FrontendType.Bool)
            FrontendType.ErrorType -> { /* ignore */ }
            else -> {
                errors.add(MismatchedTypeException(
                    location = ctx.left.asLocation(),
                    expectedTypes = listOf(FrontendType.Int, FrontendType.Bool),
                    actualType = left
                ))
            }
        }
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "<", ">", "<=", ">=", "==", "!=")
        return FrontendType.Bool
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): FrontendType {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.Bool)
        return FrontendType.Bool
    }

    override fun visitNegExpr(ctx: MainGrammar.NegExprContext): FrontendType {
        val type = visit(ctx.expression())
        return if (type.checkType(ctx.expression(), FrontendType.Int, FrontendType.Float)) {
            type
        } else {
            FrontendType.ErrorType
        }
    }

    override fun visitDerefExpr(ctx: MainGrammar.DerefExprContext): FrontendType {
        return visitDeref(ctx.expression())
    }

    override fun visitIntExpr(ctx: MainGrammar.IntExprContext): FrontendType {
        if (ctx.text.toLongOrNull() == null) {
            errors.add(MalformedNumberException(ctx.asLocation(), "integer", ctx.text))
        }
        return FrontendType.Int
    }

    override fun visitFloatExpr(ctx: MainGrammar.FloatExprContext): FrontendType {
        if (ctx.text.toDoubleOrNull() == null) {
            errors.add(MalformedNumberException(ctx.asLocation(), "float", ctx.text))
        }
        return FrontendType.Float
    }

    override fun visitParenExpr(ctx: MainGrammar.ParenExprContext): FrontendType {
        return visit(ctx.expression())
    }

    override fun visitCastExpr(ctx: MainGrammar.CastExprContext): FrontendType {
        val targetType = visit(ctx.type())
        val sourceType = visit(ctx.expression())

        // Pointers can be cast to each other
        if (targetType is FrontendType.Pointer && sourceType is FrontendType.Pointer) {
            return targetType
        }

        // Verify that the cast is between int and float
        if ((targetType != FrontendType.Int && targetType != FrontendType.Float) ||
            (sourceType != FrontendType.Int && sourceType != FrontendType.Float)) {
            errors.add(SyntaxErrorException(ctx.asLocation(), "Can't cast type $sourceType to $targetType"))
            return targetType
        }

        // Casting to the same type is allowed but redundant (just a warning, not an error)
        // TODO add a warning here
        return targetType
    }

    override fun visitAddSubExpr(ctx: MainGrammar.AddSubExprContext): FrontendType {
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "+", "-")
        return visitNumberBinOp(ctx.left, ctx.right, ctx.op.text)
    }

    override fun visitAndExpr(ctx: MainGrammar.AndExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.Bool)
        visit(ctx.right).checkType(ctx.right, FrontendType.Bool)
        return FrontendType.Bool
    }

    override fun visitOrExpr(ctx: MainGrammar.OrExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.Bool)
        visit(ctx.right).checkType(ctx.right, FrontendType.Bool)
        return FrontendType.Bool
    }

    // -------------- Private helper methods and visitors --------------

    private fun visitDeref(ctx: MainGrammar.ExpressionContext): FrontendType {
        val exprType = visit(ctx)
        if (exprType !is FrontendType.Pointer) {
            errors.add(NonPointerTypeException(ctx.asLocation(), exprType))
            return FrontendType.ErrorType
        }
        return exprType.pointeeType
    }

    private fun visitId(idName: String, location: SourceLocation): FrontendType {
        val type = symbolTable.lookup(idName)
        if (type == null) {
            errors.add(UndefinedVariableException(location, idName))
            return FrontendType.ErrorType
        }
        return type
    }

    private fun visitCall(ctx: MainGrammar.FunctionCallContext, isStatement: Boolean): FrontendType? {
        val arguments = ctx.callArguments()?.expression()?.map { visit(it) } ?: emptyList()
        val functionName = ctx.ID().text

        functionTable[functionName]?.let { descriptor ->
            if (descriptor.returnType == null && !isStatement) {
                errors.add(SyntaxErrorException(ctx.asLocation(),
                    "Can't call '$functionName' in expression, it has no return type"))
                return FrontendType.ErrorType
            }

            if (arguments.size != descriptor.arguments.size) {
                errors.add(SyntaxErrorException(ctx.asLocation(),
                    "Incorrect number of arguments for '$functionName', expected ${descriptor.arguments.size}, got ${arguments.size}"))
            }

            arguments.forEachIndexed { index, arg ->
                if (index >= descriptor.arguments.size) return@forEachIndexed
                arg.checkType(
                    ctx.callArguments().expression(index),
                    descriptor.arguments[index].type
                )
            }

            return descriptor.returnType
        }

        handleIntrinsicCall(ctx, functionName, arguments)?.let {
            return it
        }

        if (isStatement) return null
        errors.add(SyntaxErrorException(ctx.asLocation(), "Unknown function $functionName"))
        return FrontendType.ErrorType
    }

    private fun handleIntrinsicCall(ctx: MainGrammar.FunctionCallContext, functionName: String,
                                    arguments: List<FrontendType>): FrontendType? {
        if (functionName == Intrinsics.UNDEF) {
            if (arguments.size != 1) {
                errors.add(SyntaxErrorException(ctx.asLocation(), "${Intrinsics.UNDEF}() takes exactly one argument, got ${arguments.size}"))
                return FrontendType.ErrorType
            }
            return arguments.single()
        }

        // malloc(size) as T*
        if (functionName == Intrinsics.MALLOC) {
            if (arguments.size != 1) {
                errors.add(SyntaxErrorException(ctx.asLocation(), "${Intrinsics.MALLOC}() takes exactly one argument (size in bytes), got ${arguments.size}"))
                return FrontendType.ErrorType
            }
            val parentCtx = ctx.parent.parent
            if (parentCtx !is MainGrammar.CastExprContext) {
                errors.add(SyntaxErrorException(ctx.asLocation(), "${Intrinsics.MALLOC}() must be cast to a pointer type"))
                return FrontendType.ErrorType
            }
            val castType = visitType(parentCtx.type())
            if (castType !is FrontendType.Pointer) {
                errors.add(SyntaxErrorException(ctx.asLocation(), "${Intrinsics.MALLOC}() must be cast to a pointer type"))
                return FrontendType.ErrorType
            }

            // Check that the argument is an integer (size in bytes)
            arguments[0].checkType(ctx.callArguments().expression(0), FrontendType.Int)
            return FrontendType.Pointer(FrontendType.ErrorType)
        }

        // free() intrinsic - takes a pointer, returns nothing
        if (functionName == Intrinsics.FREE) {
            if (arguments.size != 1) {
                errors.add(SyntaxErrorException(ctx.asLocation(), "${Intrinsics.FREE}() takes exactly one pointer argument, got ${arguments.size}"))
                return FrontendType.ErrorType
            }
            if (arguments[0] !is FrontendType.Pointer) {
                errors.add(NonPointerTypeException(ctx.callArguments().expression(0).asLocation(), arguments[0]))
                return FrontendType.ErrorType
            }
            return FrontendType.Void
        }

        return null
    }

    private fun visitNumberBinOp(leftCtx: MainGrammar.ExpressionContext, rightCtx: MainGrammar.ExpressionContext, op: String): FrontendType {
        val left = visit(leftCtx)
        val right = visit(rightCtx)
        return typeChecker.checkNumberBinOpTypes(leftCtx.asLocation(), rightCtx.asLocation(), left, right, op)
    }

    private fun <T> withLoopLevel(block: () -> T): T {
        loopDepth++
        try {
            return block()
        } finally {
            loopDepth--
        }
    }

    private fun FrontendType.checkType(ctx: ParserRuleContext, vararg expected: FrontendType): Boolean {
        return typeChecker.checkType(ctx.asLocation(), this, *expected)
    }

    private fun checkOperatorSyntax(ctx: SourceLocation, op: String, vararg expectedOps: String) {
        if (op in expectedOps) return
        errors.add(SyntaxErrorException(ctx, "Unknown operator $op"))
    }
}