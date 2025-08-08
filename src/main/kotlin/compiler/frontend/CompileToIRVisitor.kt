package compiler.frontend

import MainGrammarBaseVisitor
import compiler.ir.*
import compiler.ir.cfg.SourceLocationMap
import compiler.utils.NameAllocator
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

class CompileToIRVisitor : MainGrammarBaseVisitor<IRValue>() {
    val resultIR = mutableListOf<IRProtoNode>()
    val sourceMap = SourceLocationMap()
    val symbolTable = SymbolTable<IRVar>()
    val varAllocator = NameAllocator("x")
    val labelAllocator = NameAllocator("L")

    fun compileToIR(tree: MainGrammar.ProgramContext): Pair<List<IRProtoNode>, SourceLocationMap> {
        visit(tree)
        return resultIR.toList() to sourceMap
    }

    private fun ParserRuleContext.defaultVisitChildren(): Nothing? {
        children?.forEach { visit(it) }
        return null
    }

    override fun visitProgram(ctx: MainGrammar.ProgramContext): Nothing? {
        return ctx.defaultVisitChildren()
    }

    private fun IRNode.withLocation(ctx: ParserRuleContext): IRNode {
        sourceMap[this] = ctx.asLocation()
        return this
    }

    private fun IRNode.withLocation(ctx: TerminalNode): IRNode {
        sourceMap[this] = ctx.asLocation()
        return this
    }

    private fun IRNode.withLocation(ctx: Token): IRNode {
        sourceMap[this] = ctx.asLocation()
        return this
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
        val declName = IRVar(varAllocator.newName(ctx.ID().text), ctx.ID().text)
        if (ctx.ASSIGN() != null) {
            resultIR.add(IRAssign(declName, visit(ctx.expression())).withLocation(ctx))
        }

        // Modify the symbol table _after_ visiting the expression, so it uses the old table (in case of shadowing)
        symbolTable.define(ctx.ID().text, declName)
        return null
    }

    override fun visitAssignment(ctx: MainGrammar.AssignmentContext): Nothing? {
        val leftVar = symbolTable.lookup(ctx.ID().text) ?: error("Undefined variable ${ctx.ID().text}")
        resultIR.add(IRAssign(leftVar, visit(ctx.expression())).withLocation(ctx))
        return null
    }

    override fun visitIfStatement(ctx: MainGrammar.IfStatementContext): Nothing? {
        val labelTrue = IRLabel(labelAllocator.newName())
        val labelFalse = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfTrue(condVar, labelTrue, labelFalse))

        resultIR.add(labelTrue)
        symbolTable.withScope {
            visit(ctx.ifTrue)
        }
        
        resultIR.add(IRJump(labelAfter))
        resultIR.add(labelFalse)
        
        if (ctx.ifFalse != null) {
            symbolTable.withScope {
                visit(ctx.ifFalse)
            }
        }
        
        resultIR.add(labelAfter)
        return null
    }

    override fun visitWhileStatement(ctx: MainGrammar.WhileStatementContext): Nothing? {
        val labelBody = IRLabel(labelAllocator.newName())
        val labelStart = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        resultIR.add(labelStart)
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfTrue(condVar, labelBody, labelAfter))
        
        // Loop body with its own scope
        resultIR.add(labelBody)
        symbolTable.withScope {
            visit(ctx.statement())
        }
        
        resultIR.add(IRJump(labelStart))
        resultIR.add(labelAfter)
        return null
    }

    override fun visitForStatement(ctx: MainGrammar.ForStatementContext): Nothing? {
        // Push scope for the entire for loop (including init)
        symbolTable.withScope {
            (ctx.initAssign ?: ctx.initDecl)?.let { visit(it) }

            val labelBody = IRLabel(labelAllocator.newName())
            val labelStart = IRLabel(labelAllocator.newName())
            val labelAfter = IRLabel(labelAllocator.newName())
            resultIR.add(labelStart)
            val condVar = visit(ctx.cond)
            resultIR.add(IRJumpIfTrue(condVar, labelBody, labelAfter))

            // Push another scope for the loop body
            resultIR.add(labelBody)
            symbolTable.withScope {
                visit(ctx.statement())
            }

            visit(ctx.inc)
            resultIR.add(IRJump(labelStart))
            resultIR.add(labelAfter)
        }
        return null
    }

    // --------------- Expressions ---------------

    private fun withNewVar(block: (IRVar) -> IRNode): IRValue {
        return IRVar(varAllocator.newName(), null).also {
            resultIR.add(block(it))
        }
    }

    override fun visitTrueExpr(ctx: MainGrammar.TrueExprContext): IRValue {
        return withNewVar { IRAssign(it, IRInt(1)).withLocation(ctx) }
    }

    override fun visitFalseExpr(ctx: MainGrammar.FalseExprContext): IRValue {
        return withNewVar { IRAssign(it, IRInt(0)).withLocation(ctx) }
    }

    override fun visitMulDivExpr(ctx: MainGrammar.MulDivExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "*" -> IRBinOpKind.MUL
            "/" -> IRBinOpKind.DIV
            "%" -> IRBinOpKind.MOD
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        return withNewVar {
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right)).withLocation(ctx)
        }
    }

    override fun visitIdExpr(ctx: MainGrammar.IdExprContext): IRValue {
        val idVar = symbolTable.lookup(ctx.ID().text) ?: error("Undefined variable ${ctx.ID().text}")
        return idVar
    }

    override fun visitComparisonExpr(ctx: MainGrammar.ComparisonExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "<" -> IRBinOpKind.LT
            ">" -> IRBinOpKind.GT
            "<=" -> IRBinOpKind.LE
            ">=" -> IRBinOpKind.GE
            "==" -> IRBinOpKind.EQ
            "!=" -> IRBinOpKind.NEQ
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        return withNewVar {
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right)).withLocation(ctx)
        }
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): IRValue {
        return withNewVar { IRNot(it, visit(ctx.expression())).withLocation(ctx) }
    }

    override fun visitIntExpr(ctx: MainGrammar.IntExprContext): IRValue {
        return IRInt(ctx.INT_LITERAL().text.toLong())
    }

    override fun visitParenExpr(ctx: MainGrammar.ParenExprContext): IRValue {
        return visit(ctx.expression())
    }

    override fun visitAddSubExpr(ctx: MainGrammar.AddSubExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "+" -> IRBinOpKind.ADD
            "-" -> IRBinOpKind.SUB
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        return withNewVar {
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right)).withLocation(ctx)
        }
    }

    private fun processShortCircuitLogic(left: ParserRuleContext, right: ParserRuleContext, isAnd: Boolean): IRValue {
        val newVar = IRVar(varAllocator.newName(), null)
        val labelRight = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val leftVal = visit(left)
        resultIR.add(IRAssign(newVar, leftVal).withLocation(left))
        if (isAnd) {
            resultIR.add(IRJumpIfTrue(newVar, labelRight, labelAfter))
        } else {
            resultIR.add(IRJumpIfTrue(newVar, labelAfter, labelRight))
        }
        resultIR.add(labelRight)
        val rightVal = visit(right)
        resultIR.add(IRAssign(newVar, rightVal).withLocation(right))
        resultIR.add(labelAfter)
        return newVar
    }

    override fun visitAndExpr(ctx: MainGrammar.AndExprContext): IRValue {
        return processShortCircuitLogic(ctx.left, ctx.right, true)
    }

    override fun visitOrExpr(ctx: MainGrammar.OrExprContext): IRValue {
        return processShortCircuitLogic(ctx.left, ctx.right, false)
    }
}