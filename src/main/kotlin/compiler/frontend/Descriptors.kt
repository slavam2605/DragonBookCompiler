package compiler.frontend

class FunctionDescriptor(
    val name: String,
    val arguments: List<ArgumentDescriptor>,
    val returnType: FrontendType?
)

class ArgumentDescriptor(val name: String, val type: FrontendType)