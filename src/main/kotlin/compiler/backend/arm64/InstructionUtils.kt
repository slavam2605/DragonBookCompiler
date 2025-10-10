package compiler.backend.arm64

internal object InstructionUtils {
    private val AllowedShiftValues = setOf(0, 16, 32, 48)
    private val LabelNameRegex = "^[a-zA-Z0-9_.]+$".toRegex()

    fun checkLabelName(name: String) {
        require(name.matches(LabelNameRegex)) { "Invalid label name: $name" }
    }

    fun checkImm12Value(value: Int) {
        require(value in 0..4095) { "Invalid value: $value, must be in range 0..4095" }
    }

    fun checkUShortValue(value: Long) {
        require(value in 0..0xFFFF) { "Invalid value: $value" }
    }

    fun checkShiftValue(value: Int) {
        require(value in AllowedShiftValues) { "Invalid shift value: $value" }
    }

    fun stpModeAddress(address: IntRegister, offset: Any, mode: StpMode): String {
        val offsetString = when (offset) {
            is Int -> "#$offset"
            is String -> offset
            else -> error("Unsupported offset type: ${offset::class.simpleName}")
        }
        return "[$address" + when (mode) {
            StpMode.SIGNED_OFFSET -> ", $offsetString]"
            StpMode.PRE_INDEXED -> ", $offsetString]!"
            StpMode.POST_INDEXED -> "], $offsetString"
        }
    }

    fun isBitPattern(value: Long): Boolean {
        // Reject all-zeros and all-ones
        if (value == 0L || value == -1L) return false

        // Try each possible element size: 2, 4, 8, 16, 32, 64
        for (elementSizeLog2 in 1..6) {
            val elementSize = 1 shl elementSizeLog2
            if (isValidPatternWithElementSize(value, elementSize)) {
                return true
            }
        }

        return false
    }

    private fun isValidPatternWithElementSize(value: Long, elementSize: Int): Boolean {
        // Extract the element mask (e.g., for size 8, mask is 0xFF)
        val elementMask = if (elementSize == 64) -1L else (1L shl elementSize) - 1

        // Check if all elements are identical
        val numElements = 64 / elementSize
        val firstElement = value and elementMask

        for (i in 1 until numElements) {
            val element = (value ushr (i * elementSize)) and elementMask
            if (element != firstElement) {
                return false
            }
        }

        // Now check if the element contains a single run of 1s (possibly rotated)
        return hasOneBitRun(firstElement, elementSize)
    }

    private fun hasOneBitRun(element: Long, elementSize: Int): Boolean {
        // An element is valid if it contains exactly one contiguous run of 1s
        // when viewed as a circular buffer (rotation allowed)

        // Special case: element must not be all-zeros or all-ones
        val elementMask = if (elementSize == 64) -1L else (1L shl elementSize) - 1
        val maskedElement = element and elementMask

        if (maskedElement == 0L || maskedElement == elementMask) {
            return false
        }

        // Try all possible rotations
        for (rotation in 0 until elementSize) {
            val rotated = rotateRight(maskedElement, rotation, elementSize)

            // Check if this rotation has all 1s in a contiguous block starting from LSB
            // Pattern should be: 0...01...1 (optional leading zeros, then contiguous ones)
            if (isContiguousOnesFromLSB(rotated, elementSize)) {
                return true
            }
        }

        return false
    }

    private fun rotateRight(value: Long, rotation: Int, elementSize: Int): Long {
        val elementMask = if (elementSize == 64) -1L else (1L shl elementSize) - 1
        val maskedValue = value and elementMask
        val normalizedRotation = rotation % elementSize

        if (normalizedRotation == 0) return maskedValue

        val rightPart = maskedValue ushr normalizedRotation
        val leftPart = (maskedValue shl (elementSize - normalizedRotation)) and elementMask

        return rightPart or leftPart
    }

    private fun isContiguousOnesFromLSB(value: Long, elementSize: Int): Boolean {
        // Check if value has the pattern: 0...01...1
        // This means: find the position of the highest 1 bit,
        // and check that all bits from LSB to that position are 1s

        if (value == 0L) return false

        val elementMask = if (elementSize == 64) -1L else (1L shl elementSize) - 1
        val maskedValue = value and elementMask

        // Find the position of the highest set bit
        var highestBit = -1
        for (i in 0 until elementSize) {
            if ((maskedValue and (1L shl i)) != 0L) {
                highestBit = i
            }
        }

        if (highestBit == -1) return false

        // Check that all bits from 0 to highestBit are set
        val expectedPattern = (1L shl (highestBit + 1)) - 1
        return maskedValue == expectedPattern
    }
}