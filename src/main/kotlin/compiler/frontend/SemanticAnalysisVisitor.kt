package compiler.frontend

import MainGrammar
import MainGrammarBaseVisitor
import org.antlr.v4.runtime.ParserRuleContext

// TODO store symbol tables in nodes as a tree property and reuse in IR generation
class SemanticAnalysisVisitor : MainGrammarBaseVisitor<FrontendType>() {
    val symbolTable = SymbolTable<FrontendType>()
    val errors = mutableListOf<CompilationException>()

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
        return ctx.defaultVisitChildren()
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
        symbolTable.withScope {
            visit(ctx.statement())
        }
        return null
    }

    override fun visitForStatement(ctx: MainGrammar.ForStatementContext): Nothing? {
        symbolTable.withScope {
            (ctx.initAssign ?: ctx.initDecl)?.let { visit(it) }
            visit(ctx.cond).checkType(ctx.cond, FrontendType.BOOL)
            visit(ctx.inc)
            symbolTable.withScope {
                visit(ctx.statement())
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
        visit(ctx.left).checkType(ctx.left, FrontendType.INT)
        visit(ctx.right).checkType(ctx.right, FrontendType.INT)
        checkOperatorSyntax(ctx.op.asLocation(), ctx.op.text, "<", ">", "<=", ">=", "==", "!=")
        return FrontendType.BOOL
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): FrontendType {
        visit(ctx.expression()).checkType(ctx.expression(), FrontendType.BOOL)
        return FrontendType.BOOL
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

    private fun FrontendType.checkType(ctx: ParserRuleContext, expected: FrontendType): FrontendType {
        if (this == FrontendType.ERROR_TYPE || expected == FrontendType.ERROR_TYPE) return this
        if (this != expected) {
            errors.add(MismatchedTypeException(ctx.asLocation(), expected, this))
        }
        return this
    }

    private fun checkOperatorSyntax(ctx: SourceLocation, op: String, vararg expectedOps: String) {
        if (op in expectedOps) return
        errors.add(SyntaxErrorException(ctx, "Unknown operator $op"))
    }
}