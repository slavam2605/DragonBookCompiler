package compiler.frontend

import MainGrammarBaseVisitor
import compiler.ir.*
import compiler.utils.NameAllocator
import org.antlr.v4.runtime.ParserRuleContext

class CompileToIRVisitor : MainGrammarBaseVisitor<IRValue>() {
    val resultIR = mutableListOf<IRProtoNode>()
    val symbolTable = SymbolTable<IRVar>()
    val varAllocator = NameAllocator("x")
    val labelAllocator = NameAllocator("L")

    fun compileToIR(tree: MainGrammar.ProgramContext): List<IRProtoNode> {
        visit(tree)
        return resultIR.toList()
    }

    private fun ParserRuleContext.defaultVisitChildren(): Nothing? {
        children?.forEach { visit(it) }
        return null
    }

    override fun visitProgram(ctx: MainGrammar.ProgramContext): Nothing? {
        return ctx.defaultVisitChildren()
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
        val declName = IRVar(varAllocator.newName(ctx.ID().text))
        symbolTable.define(ctx.ID().text, declName)
        if (ctx.ASSIGN() != null) {
            resultIR.add(IRAssign(declName, visit(ctx.expression())))
        }
        return null
    }

    override fun visitAssignment(ctx: MainGrammar.AssignmentContext): Nothing? {
        val leftVar = symbolTable.lookup(ctx.ID().text) ?: error("Undefined variable ${ctx.ID().text}")
        resultIR.add(IRAssign(leftVar, visit(ctx.expression())))
        return null
    }

    override fun visitIfStatement(ctx: MainGrammar.IfStatementContext): Nothing? {
        val labelFalse = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfFalse(condVar, labelFalse))

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
        val labelStart = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        resultIR.add(labelStart)
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfFalse(condVar, labelAfter))
        
        // Loop body with its own scope
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
            visit(ctx.initAssign ?: ctx.initDecl)

            val labelStart = IRLabel(labelAllocator.newName())
            val labelAfter = IRLabel(labelAllocator.newName())
            resultIR.add(labelStart)
            val condVar = visit(ctx.cond)
            resultIR.add(IRJumpIfFalse(condVar, labelAfter))

            // Push another scope for the loop body
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
        return IRVar(varAllocator.newName()).also {
            resultIR.add(block(it))
        }
    }

    override fun visitTrueExpr(ctx: MainGrammar.TrueExprContext): IRValue {
        return withNewVar { IRAssign(it, IRInt(1)) }
    }

    override fun visitFalseExpr(ctx: MainGrammar.FalseExprContext): IRValue {
        return withNewVar { IRAssign(it, IRInt(0)) }
    }

    override fun visitMulDivExpr(ctx: MainGrammar.MulDivExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "*" -> IRBinOpKind.MUL
            "/" -> IRBinOpKind.DIV
            "%" -> IRBinOpKind.MOD
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        return withNewVar {
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right))
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
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right))
        }
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): IRValue {
        return withNewVar { IRNot(it, visit(ctx.expression())) }
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
            IRBinOp(opKind, it, visit(ctx.left), visit(ctx.right))
        }
    }

    override fun visitAndExpr(ctx: MainGrammar.AndExprContext): IRValue {
        val newVar = IRVar(varAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val left = visit(ctx.left)
        resultIR.add(IRAssign(newVar, left))
        resultIR.add(IRJumpIfFalse(newVar, labelAfter))
        val right = visit(ctx.right)
        resultIR.add(IRAssign(newVar, right))
        resultIR.add(labelAfter)
        return newVar
    }

    override fun visitOrExpr(ctx: MainGrammar.OrExprContext): IRValue {
        val newVar = IRVar(varAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val left = visit(ctx.left)
        resultIR.add(IRAssign(newVar, left))
        resultIR.add(IRJumpIfTrue(newVar, labelAfter))
        val right = visit(ctx.right)
        resultIR.add(IRAssign(newVar, right))
        resultIR.add(labelAfter)
        return newVar
    }
}