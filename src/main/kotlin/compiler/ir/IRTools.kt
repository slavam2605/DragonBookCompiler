package compiler.ir

interface IRTransformer {
    fun transformLValue(value: IRVar): IRVar

    fun transformRValue(value: IRValue): IRValue

    fun transformLabel(label: IRLabel): IRLabel
}

abstract class BaseIRTransformer : IRTransformer {
    override fun transformLValue(value: IRVar): IRVar = value

    override fun transformRValue(value: IRValue): IRValue = value

    override fun transformLabel(label: IRLabel): IRLabel = label
}