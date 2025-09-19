package compiler.backend.arm64

sealed class MemoryLocation

data class StackLocation(val offset: Int) : MemoryLocation() {
    override fun toString() = "[sp, $offset]"
}

sealed class Register : MemoryLocation() {
    data class D(val index: Int) : Register() {
        init {
            require(index in 0..31) { "Invalid D register index: $index" }
        }

        override fun toString() = "d$index"

        companion object {
            val CallerSaved = ((0..7) + (16..31)).map(::D).toSet()
            val CalleeSaved = (8..15).map(::D).toSet()
        }
    }
}

sealed class IntRegister : Register() {
    data class X(val index: Int) : IntRegister() {
        init {
            require(index in 0..30) { "Invalid X register index: $index" }
            require(index != 18) { "x18 is a platform-reserved register" }
        }

        override fun toString() = "x$index"

        companion object {
            // x0-x1: function return value
            // x0-x7: function parameters
            // x18: platform-reserved
            // x29: frame pointer
            // x30: link register
            val CallerSaved = (0..17).map(::X).toSet()
            val CalleeSaved = (19..28).map(::X).toSet()
        }
    }

    object SP : IntRegister() {
        override fun toString() = "sp"
    }
}