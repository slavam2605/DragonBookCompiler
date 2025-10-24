package compiler.frontend

import MainGrammar
import MainGrammarBaseVisitor
import compiler.ir.*
import compiler.ir.cfg.extensions.SourceLocationMap
import compiler.utils.NameAllocator
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

class CompileToIRVisitor : MainGrammarBaseVisitor<IRValue>() {
    private val result = FrontendFunctions<List<IRProtoNode>>()
    private val resultIR = mutableListOf<IRProtoNode>()
    private val sourceMap = SourceLocationMap()
    private val symbolTable = SymbolTable<IRVar>()
    private val functionReturnType = mutableMapOf<String, IRType>()
    private val loopStack = mutableListOf<LoopContext>()
    private val varAllocator = NameAllocator("x")
    private val labelAllocator = NameAllocator("L")

    private data class LoopContext(val continueLabel: IRLabel, val breakLabel: IRLabel)

    fun compileToIR(tree: MainGrammar.ProgramContext): Pair<FrontendFunctions<List<IRProtoNode>>, SourceLocationMap> {
        visit(tree)
        return result to sourceMap
    }

    private fun ParserRuleContext.defaultVisitChildren(): Nothing? {
        children?.forEach { visit(it) }
        return null
    }

    override fun visitProgram(ctx: MainGrammar.ProgramContext): Nothing? {
        ctx.function().forEach { functionContext ->
            val name = functionContext.ID().text
            val returnType = functionContext.type()?.irType()
            returnType?.let { functionReturnType[name] = it }
        }

        return ctx.defaultVisitChildren()
    }

    override fun visitFunction(ctx: MainGrammar.FunctionContext): Nothing? {
        symbolTable.withScope {
            val parameters = mutableListOf<IRVar>()
            ctx.functionParameters()?.functionParameter()?.forEach { parameter ->
                val name = parameter.ID().text
                val type = parameter.type().irType()
                val irVar = IRVar(varAllocator.newName(name), type, name)
                parameters.add(irVar)
                symbolTable.define(name, irVar)
            }

            check(resultIR.isEmpty())
            visit(ctx.block())

            // Parse annotations
            val annotations = ctx.annotations()?.annotationList()?.ID()?.map { it.text }?.toSet() ?: emptySet()

            result.addFunction(FrontendFunction(
                name = ctx.ID().text,
                parameters = parameters,
                hasReturnType = ctx.type() != null,
                endLocation = ctx.block().RBRACE().asLocation(),
                annotations = annotations,
                value = resultIR.toList()
            ))
            resultIR.clear()
        }
        return null
    }

    private fun IRNode.withLocation(ctx: ParserRuleContext?): IRNode {
        sourceMap[this] = ctx?.asLocation() ?: return this
        return this
    }

    private fun IRNode.withLocation(ctx: TerminalNode?): IRNode {
        sourceMap[this] = ctx?.asLocation() ?: return this
        return this
    }

    private fun IRNode.withLocation(ctx: Token?): IRNode {
        sourceMap[this] = ctx?.asLocation() ?: return this
        return this
    }

    // --------------- Statements ---------------

    override fun visitStatement(ctx: MainGrammar.StatementContext): Nothing? {
        return ctx.defaultVisitChildren()
    }

    override fun visitBreakStatement(ctx: MainGrammar.BreakStatementContext): Nothing? {
        val loop = loopStack.lastOrNull() ?: error("'break' used outside of a loop during IR generation")
        resultIR.add(IRJump(loop.breakLabel).withLocation(ctx))
        resultIR.add(IRLabel(labelAllocator.newName()))
        return null
    }

    override fun visitContinueStatement(ctx: MainGrammar.ContinueStatementContext): Nothing? {
        val loop = loopStack.lastOrNull() ?: error("'continue' used outside of a loop during IR generation")
        resultIR.add(IRJump(loop.continueLabel).withLocation(ctx))
        resultIR.add(IRLabel(labelAllocator.newName()))
        return null
    }

    override fun visitReturnStatement(ctx: MainGrammar.ReturnStatementContext): Nothing? {
        val value = ctx.expression()?.let { visit(it) }
        resultIR.add(IRReturn(value).withLocation(ctx))
        resultIR.add(IRLabel(labelAllocator.newName()))
        return null
    }

    override fun visitFunctionCall(ctx: MainGrammar.FunctionCallContext): Nothing? {
        val arguments = ctx.callArguments()?.expression()?.map { visit(it) } ?: emptyList()
        resultIR.add(IRFunctionCall(ctx.ID().text, null, arguments).withLocation(ctx))
        return null
    }

    override fun visitBlock(ctx: MainGrammar.BlockContext): Nothing? {
        return symbolTable.withScope {
            ctx.defaultVisitChildren()
        }
    }

    override fun visitDeclaration(ctx: MainGrammar.DeclarationContext): Nothing? {
        val name = ctx.ID().text
        val type = ctx.type().irType()
        val declName = IRVar(varAllocator.newName(name), type, name)
        if (ctx.ASSIGN() != null) {
            resultIR.add(IRAssign(declName, visit(ctx.expression())).withLocation(ctx))
        }

        // Modify the symbol table _after_ visiting the expression, so it uses the old table (in case of shadowing)
        symbolTable.define(ctx.ID().text, declName)
        return null
    }

    private fun visitVarAssignment(
        ctx: MainGrammar.AssignmentContext,
        lvalue: MainGrammar.IdLValueContext,
        binOpKind: IRBinOpKind?,
        rightValue: IRValue
    ) {
        val leftVar = symbolTable.lookup(lvalue.ID().text) ?: error("Undefined variable ${lvalue.ID().text}")
        if (binOpKind == null) {
            resultIR.add(IRAssign(leftVar, rightValue).withLocation(ctx))
            return
        }

        val (unifiedLeft, unifiedRight, resultType) = unifyTypes(leftVar, rightValue, ctx)
        val tmp = IRVar(varAllocator.newName(), resultType, null)
        resultIR.add(IRBinOp(binOpKind, tmp, unifiedLeft, unifiedRight).withLocation(ctx))
        resultIR.add(IRAssign(leftVar, tmp).withLocation(ctx))
    }

    private fun visitPointerAssignment(
        ctx: MainGrammar.AssignmentContext,
        lvalue: MainGrammar.DerefLValueContext,
        binOpKind: IRBinOpKind?,
        rightValue: IRValue
    ) {
        val pointerValue = visit(lvalue.expression())
        if (binOpKind == null) {
            resultIR.add(IRStore(pointerValue, rightValue).withLocation(ctx))
            return
        }

        // First, load the pointed value
        val pointeeType = (pointerValue.type as IRType.PTR).pointeeType
        val loadedValue = IRVar(varAllocator.newName(), pointeeType, null)
        resultIR.add(IRLoad(loadedValue, pointerValue).withLocation(ctx))

        // Second, do the binary operation
        val (unifiedLeft, unifiedRight, resultType) = unifyTypes(loadedValue, rightValue, ctx)
        val tmp = IRVar(varAllocator.newName(), resultType, null)
        resultIR.add(IRBinOp(binOpKind, tmp, unifiedLeft, unifiedRight).withLocation(ctx))

        // Last, store the result back to the pointer
        resultIR.add(IRStore(pointerValue, tmp).withLocation(ctx))
    }

    override fun visitAssignment(ctx: MainGrammar.AssignmentContext): Nothing? {
        val lvalue = ctx.lvalue()
        val rightValue = visit(ctx.expression())
        val binOpKind = when (val opText = ctx.op.text) {
            "=" -> null
            "+=" -> IRBinOpKind.ADD
            "-=" -> IRBinOpKind.SUB
            "*=" -> IRBinOpKind.MUL
            "/=" -> IRBinOpKind.DIV
            "%=" -> IRBinOpKind.MOD
            else -> error("Unsupported assignment operator $opText")
        }

        when (lvalue) {
            is MainGrammar.IdLValueContext -> visitVarAssignment(ctx, lvalue, binOpKind, rightValue)
            is MainGrammar.DerefLValueContext -> visitPointerAssignment(ctx, lvalue, binOpKind, rightValue)
            else -> error("Unknown lvalue type: ${lvalue::class}")
        }
        return null
    }

    override fun visitIfStatement(ctx: MainGrammar.IfStatementContext): Nothing? {
        val labelTrue = IRLabel(labelAllocator.newName())
        val labelFalse = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfTrue(condVar, labelTrue, labelFalse).withLocation(ctx.expression()))

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
        resultIR.add(IRJumpIfTrue(condVar, labelBody, labelAfter).withLocation(ctx.expression()))
        
        // Loop body with its own scope
        resultIR.add(labelBody)
        withLoop(continueLabel = labelStart, breakLabel = labelAfter) {
            symbolTable.withScope {
                visit(ctx.statement())
            }
        }
        
        resultIR.add(IRJump(labelStart))
        resultIR.add(labelAfter)
        return null
    }

    override fun visitDoWhileStatement(ctx: MainGrammar.DoWhileStatementContext): Nothing? {
        val labelBody = IRLabel(labelAllocator.newName())
        val labelCheck = IRLabel(labelAllocator.newName())
        val labelAfter = IRLabel(labelAllocator.newName())

        resultIR.add(labelBody)
        withLoop(continueLabel = labelCheck, breakLabel = labelAfter) {
            symbolTable.withScope {
                visit(ctx.statement())
            }
        }

        resultIR.add(labelCheck)
        val condVar = visit(ctx.expression())
        resultIR.add(IRJumpIfTrue(condVar, labelBody, labelAfter).withLocation(ctx.expression()))
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
            val labelInc = IRLabel(labelAllocator.newName())
            resultIR.add(labelStart)
            val condVar = ctx.cond?.let { visit(it) } ?: IRInt(1)
            resultIR.add(IRJumpIfTrue(condVar, labelBody, labelAfter).withLocation(ctx.cond))

            // Push another scope for the loop body
            resultIR.add(labelBody)
            withLoop(continueLabel = labelInc, breakLabel = labelAfter) {
                symbolTable.withScope {
                    visit(ctx.statement())
                }
            }

            resultIR.add(IRJump(labelInc))
            resultIR.add(labelInc)
            ctx.inc?.let { visit(it) }
            resultIR.add(IRJump(labelStart))
            resultIR.add(labelAfter)
        }
        return null
    }

    // --------------- Expressions ---------------

    private fun withNewVar(type: IRType, block: (IRVar) -> IRNode): IRValue {
        return IRVar(varAllocator.newName(), type, null).also {
            resultIR.add(block(it))
        }
    }

    override fun visitTrueExpr(ctx: MainGrammar.TrueExprContext): IRValue {
        return withNewVar(IRType.INT64) { IRAssign(it, IRInt(1)).withLocation(ctx) }
    }

    override fun visitFalseExpr(ctx: MainGrammar.FalseExprContext): IRValue {
        return withNewVar(IRType.INT64) { IRAssign(it, IRInt(0)).withLocation(ctx) }
    }

    override fun visitCallExpr(ctx: MainGrammar.CallExprContext): IRValue {
        val call = ctx.functionCall()
        val arguments = call.callArguments()?.expression()?.map { visit(it) } ?: emptyList()
        val name = call.ID().text
        val returnType = functionReturnType[name]
            ?: getIntrinsicReturnType(ctx, name, arguments)
            ?: error("Unknown function $name (or it doesn't have a return type)")

        return withNewVar(returnType) {
            IRFunctionCall(name, it, arguments).withLocation(ctx)
        }
    }

    private fun getIntrinsicReturnType(ctx: MainGrammar.CallExprContext, functionName: String,
                                       arguments: List<IRValue>): IRType? {
        return when (functionName) {
            Intrinsics.MALLOC -> (ctx.parent as MainGrammar.CastExprContext).type().irType()
            Intrinsics.UNDEF -> arguments.single().type
            else -> null
        }
    }

    override fun visitMulDivExpr(ctx: MainGrammar.MulDivExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "*" -> IRBinOpKind.MUL
            "/" -> IRBinOpKind.DIV
            "%" -> IRBinOpKind.MOD
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        val left = visit(ctx.left)
        val right = visit(ctx.right)
        val (unifiedLeft, unifiedRight, resultType) = unifyTypes(left, right, ctx)
        return withNewVar(resultType) {
            IRBinOp(opKind, it, unifiedLeft, unifiedRight).withLocation(ctx)
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
        val left = visit(ctx.left)
        val right = visit(ctx.right)
        val (unifiedLeft, unifiedRight, _) = unifyTypes(left, right, ctx)
        return withNewVar(IRType.INT64) {
            IRBinOp(opKind, it, unifiedLeft, unifiedRight).withLocation(ctx)
        }
    }

    override fun visitNotExpr(ctx: MainGrammar.NotExprContext): IRValue {
        return withNewVar(IRType.INT64) { IRNot(it, visit(ctx.expression())).withLocation(ctx) }
    }

    override fun visitNegExpr(ctx: MainGrammar.NegExprContext): IRValue {
        // Implement unary minus as 0 - expr
        val value = visit(ctx.expression())
        val zero = when (value.type) {
            IRType.INT64 -> IRInt(0)
            IRType.FLOAT64 -> IRFloat(0.0)
            is IRType.PTR -> error("Cannot negate pointer type")
        }
        return withNewVar(value.type) {
            IRBinOp(IRBinOpKind.SUB, it, zero, value).withLocation(ctx)
        }
    }

    override fun visitDerefExpr(ctx: MainGrammar.DerefExprContext): IRValue {
        val pointerValue = visit(ctx.expression())
        val pointeeType = (pointerValue.type as IRType.PTR).pointeeType
        return withNewVar(pointeeType) {
            IRLoad(it, pointerValue).withLocation(ctx)
        }
    }

    override fun visitIntExpr(ctx: MainGrammar.IntExprContext): IRValue {
        return IRInt(ctx.text.toLong())
    }

    override fun visitFloatExpr(ctx: MainGrammar.FloatExprContext): IRValue {
        return IRFloat(ctx.text.toDouble())
    }

    override fun visitParenExpr(ctx: MainGrammar.ParenExprContext): IRValue {
        return visit(ctx.expression())
    }

    override fun visitCastExpr(ctx: MainGrammar.CastExprContext): IRValue {
        val targetType = ctx.type().irType()
        val sourceValue = visit(ctx.expression())

        // If types are the same, no conversion needed
        if (sourceValue.type == targetType) {
            return sourceValue
        }

        // Create conversion instruction
        return withNewVar(targetType) {
            IRConvert(it, sourceValue).withLocation(ctx)
        }
    }

    override fun visitAddSubExpr(ctx: MainGrammar.AddSubExprContext): IRValue {
        val opKind = when (ctx.op.text) {
            "+" -> IRBinOpKind.ADD
            "-" -> IRBinOpKind.SUB
            else -> throw IllegalStateException("Unknown operator ${ctx.op.text}")
        }
        val left = visit(ctx.left)
        val right = visit(ctx.right)
        val (unifiedLeft, unifiedRight, resultType) = unifyTypes(left, right, ctx)
        return withNewVar(resultType) {
            IRBinOp(opKind, it, unifiedLeft, unifiedRight).withLocation(ctx)
        }
    }

    private fun processShortCircuitLogic(left: ParserRuleContext, right: ParserRuleContext, isAnd: Boolean): IRValue {
        val newVar = IRVar(varAllocator.newName(), IRType.INT64, null)
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

    /**
     * Unifies types of two operands by inserting IRConvert nodes where needed.
     * If types differ, converts int to float (float has precedence).
     * @return Triple of (unified left, unified right, result type)
     */
    private fun unifyTypes(left: IRValue, right: IRValue, ctx: ParserRuleContext): Triple<IRValue, IRValue, IRType> {
        if (left.type is IRType.PTR) {
            check(right.type is IRType.INT64)
            return Triple(left, right, left.type)
        }

        if (left.type == right.type) {
            return Triple(left, right, left.type)
        }

        // One is INT64, the other is FLOAT64 - convert INT64 to FLOAT64
        val resultType = IRType.FLOAT64
        val unifiedLeft = if (left.type == IRType.INT64) {
            withNewVar(IRType.FLOAT64) {
                IRConvert(it, left).withLocation(ctx)
            }
        } else {
            check(left.type == IRType.FLOAT64)
            left
        }

        val unifiedRight = if (right.type == IRType.INT64) {
            withNewVar(IRType.FLOAT64) {
                IRConvert(it, right).withLocation(ctx)
            }
        } else {
            check(right.type == IRType.FLOAT64)
            right
        }

        return Triple(unifiedLeft, unifiedRight, resultType)
    }

    private fun MainGrammar.TypeContext.irType(): IRType {
        val baseType = when (val baseTypeName = ID().text) {
            "int" -> IRType.INT64
            "bool" -> IRType.INT64
            "float" -> IRType.FLOAT64
            else -> error("Unknown type '$baseTypeName'")
        }

        // Count the number of STAR tokens to build the pointer type
        val pointerDepth = STAR().size
        var resultType = baseType
        repeat(pointerDepth) {
            resultType = IRType.PTR(resultType)
        }
        return resultType
    }

    private fun <T> withLoop(continueLabel: IRLabel, breakLabel: IRLabel, block: () -> T): T {
        loopStack.add(LoopContext(continueLabel, breakLabel))
        try {
            return block()
        } finally {
            loopStack.removeLast()
        }
    }
}