package compiler.frontend

import MainGrammar
import MainGrammarBaseVisitor
import org.antlr.v4.runtime.ParserRuleContext

// TODO store symbol tables in nodes as a tree property and reuse in IR generation
class SemanticAnalysisVisitor : MainGrammarBaseVisitor<FrontendType>() {
    private var loopDepth: Int = 0
    private val symbolTable = SymbolTable<FrontendType>()
    private val functionTable = mutableMapOf<String, FunctionDescriptor>()
    private val errors = mutableListOf<CompilationException>()

    fun analyze(tree: MainGrammar.ProgramContext) {
        visit(tree)
        if (errors.isNotEmpty()) {
            throw CompilationFailed(errors)
        }
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
        return when (ctx.text) {
            "int" -> FrontendType.INT
            "bool" -> FrontendType.BOOL
            else -> {
                errors.add(UnknownTypeException(ctx.asLocation(), ctx.text))
                FrontendType.ERROR_TYPE
            }
        }
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
        val type = symbolTable.lookup(ctx.ID().text)
        if (type == null) {
            errors.add(UndefinedVariableException(ctx.ID().symbol.asLocation(), ctx.ID().text))
        }
        val right = visit(ctx.expression())
        if (type != null) {
            right.checkType(ctx.expression(), type)
        }
        return null
    }

    override fun visitIfStatement(ctx: MainGrammar.IfStatementContext): Nothing? {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.BOOL)
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
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.BOOL)
        withLoopLevel {
            symbolTable.withScope {
                visit(ctx.statement())
            }
        }
        return null
    }

    override fun visitDoWhileStatement(ctx: MainGrammar.DoWhileStatementContext): Nothing? {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.BOOL)
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
            ctx.cond?.let { visit(it).checkType(it, FrontendType.BOOL) }
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
        return FrontendType.BOOL
    }

    override fun visitFalseExpr(ctx: MainGrammar.FalseExprContext): FrontendType {
        return FrontendType.BOOL
    }

    override fun visitCallExpr(ctx: MainGrammar.CallExprContext): FrontendType {
        val call = ctx.functionCall()
        return visitCall(call, isStatement = false)!!
    }

    override fun visitMulDivExpr(ctx: MainGrammar.MulDivExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.INT)
        visit(ctx.right).checkType(ctx.right, FrontendType.INT)
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "*", "/", "%")
        return FrontendType.INT
    }

    override fun visitIdExpr(ctx: MainGrammar.IdExprContext): FrontendType {
        val type = symbolTable.lookup(ctx.ID().text)
        if (type == null) {
            errors.add(UndefinedVariableException(ctx.ID().symbol.asLocation(), ctx.ID().text))
            return FrontendType.ERROR_TYPE
        }
        return type
    }

    override fun visitComparisonExpr(ctx: MainGrammar.ComparisonExprContext): FrontendType {
        val left = visit(ctx.left)
        val right = visit(ctx.right)
        when (left) {
            FrontendType.INT -> right.checkType(ctx.right, FrontendType.INT)
            FrontendType.BOOL if (ctx.op.text == "==" || ctx.op.text == "!=") ->
                right.checkType(ctx.right, FrontendType.BOOL)
            FrontendType.ERROR_TYPE -> { /* ignore */ }
            else -> throw MismatchedTypeException(
                location = ctx.left.asLocation(),
                expectedTypes = listOf(FrontendType.INT, FrontendType.BOOL),
                actualType = left
            )
        }
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "<", ">", "<=", ">=", "==", "!=")
        return FrontendType.BOOL
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): FrontendType {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.BOOL)
        return FrontendType.BOOL
    }

    override fun visitNegExpr(ctx: MainGrammar.NegExprContext): FrontendType {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.INT)
        return FrontendType.INT
    }

    override fun visitIntExpr(ctx: MainGrammar.IntExprContext): FrontendType {
        return FrontendType.INT
    }

    override fun visitParenExpr(ctx: MainGrammar.ParenExprContext): FrontendType {
        return visit(ctx.expression())
    }

    override fun visitAddSubExpr(ctx: MainGrammar.AddSubExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.INT)
        visit(ctx.right).checkType(ctx.right, FrontendType.INT)
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "+", "-")
        return FrontendType.INT
    }

    override fun visitAndExpr(ctx: MainGrammar.AndExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.BOOL)
        visit(ctx.right).checkType(ctx.right, FrontendType.BOOL)
        return FrontendType.BOOL
    }

    override fun visitOrExpr(ctx: MainGrammar.OrExprContext): FrontendType {
        visit(ctx.left).checkType(ctx.left, FrontendType.BOOL)
        visit(ctx.right).checkType(ctx.right, FrontendType.BOOL)
        return FrontendType.BOOL
    }

    private fun visitCall(ctx: MainGrammar.FunctionCallContext, isStatement: Boolean): FrontendType? {
        val arguments = ctx.callArguments()?.expression()?.map { visit(it) } ?: emptyList()
        val functionName = ctx.ID().text
        if (functionName == "undef" && arguments.size == 1) {
            return arguments.single()
        }

        functionTable[functionName]?.let { descriptor ->
            if (descriptor.returnType == null && !isStatement) {
                errors.add(SyntaxErrorException(ctx.asLocation(),
                    "Can't call '$functionName' in expression, it has no return type"))
                return FrontendType.ERROR_TYPE
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

        if (isStatement) return null
        errors.add(SyntaxErrorException(ctx.asLocation(), "Unknown function $functionName"))
        return FrontendType.ERROR_TYPE
    }

    private fun <T> withLoopLevel(block: () -> T): T {
        loopDepth++
        try {
            return block()
        } finally {
            loopDepth--
        }
    }

    private fun FrontendType.checkType(ctx: ParserRuleContext, expected: FrontendType) {
        if (this == FrontendType.ERROR_TYPE || expected == FrontendType.ERROR_TYPE) return
        if (this == FrontendType.NOTHING) return // Nothing is a subtype of every type
        if (this != expected) {
            errors.add(MismatchedTypeException(ctx.asLocation(), expected, this))
        }
        return
    }

    private fun checkOperatorSyntax(ctx: SourceLocation, op: String, vararg expectedOps: String) {
        if (op in expectedOps) return
        errors.add(SyntaxErrorException(ctx, "Unknown operator $op"))
    }
}