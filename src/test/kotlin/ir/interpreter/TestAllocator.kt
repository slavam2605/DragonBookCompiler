package ir.interpreter

import compiler.frontend.FrontendConstantValue
import compiler.frontend.FrontendConstantValue.FloatValue
import compiler.frontend.FrontendConstantValue.IntValue
import compiler.frontend.FrontendConstantValue.PointerValue
import compiler.ir.IRType
import java.util.BitSet

internal class TestAllocator {
    private var nextAddress: Long = 0L
    private val allocatedChunks = mutableMapOf<Long, MemoryChunk>() // startAddress -> MemoryChunk
    private var data = ByteArray(1024)
    private val isAllocated = BitSet(1024)

    fun allocate(type: IRType, sizeBytes: Long): PointerValue {
        val newChunk = MemoryChunk(nextAddress, sizeBytes)
        nextAddress += sizeBytes
        allocatedChunks[newChunk.startAddress] = newChunk
        ensureSize(nextAddress)
        for (i in newChunk.startAddress until newChunk.startAddress + newChunk.size) {
            isAllocated[i.toInt()] = true
        }

        return PointerValue(newChunk.startAddress, type)
    }

    fun free(pointer: PointerValue) {
        val chunk = allocatedChunks.remove(pointer.address)
            ?: error("Trying to free unallocated memory with type ${pointer.targetType} at $${pointer.address}")

        for (i in chunk.startAddress until chunk.startAddress + chunk.size) {
            isAllocated[i.toInt()] = false
        }
    }

    fun load(pointer: PointerValue): FrontendConstantValue {
        check(pointer.address <= Int.MAX_VALUE) { "Long addresses are not supported yet" }
        check(pointer.targetType.typeSize == 8L) { "Only types with size 8 are supported for now" }
        checkIsAllocated(pointer, "load from")

        val intAddress = pointer.address.toInt()
        val longValue = (data[intAddress].toLong() shl 48) +
                (data[intAddress + 1].toLong() shl 32) +
                (data[intAddress + 2].toLong() shl 16) +
                data[intAddress + 3].toLong()
        return when (pointer.targetType) {
            IRType.INT64 -> IntValue(longValue)
            IRType.FLOAT64 -> FloatValue(Double.fromBits(longValue))
            is IRType.PTR -> PointerValue(longValue, pointer.targetType)
        }
    }

    fun store(pointer: PointerValue, value: FrontendConstantValue) {
        check(pointer.address <= Int.MAX_VALUE) { "Long addresses are not supported yet" }
        check(pointer.targetType.typeSize == 8L) { "Only types with size 8 are supported for now" }
        checkIsAllocated(pointer, "store to")

        val intAddress = pointer.address.toInt()
        val longValue = when (pointer.targetType) {
            IRType.INT64 -> (value as IntValue).value
            IRType.FLOAT64 -> (value as FloatValue).value.toBits()
            is IRType.PTR -> (value as PointerValue).address
        }
        data[intAddress] = (longValue shr 48).toByte()
        data[intAddress + 1] = (longValue shr 32).toByte()
        data[intAddress + 2] = (longValue shr 16).toByte()
        data[intAddress + 3] = longValue.toByte()
    }

    fun checkUnfreedMemory() {
        if (allocatedChunks.isEmpty()) return

        val sb = StringBuilder("Unfreed memory after test:\n")
        allocatedChunks.forEach { (address, chunk) ->
            sb.appendLine("\tAddress: $$address, size: ${chunk.size}")
        }
        error(sb.toString())
    }

    private fun checkIsAllocated(pointer: PointerValue, operation: String) {
        val allocatedBytes = mutableListOf<Boolean>()
        for (i in 0 until pointer.targetType.typeSize.toInt()) {
            allocatedBytes.add(isAllocated[pointer.address.toInt() + i])
        }

        check(allocatedBytes.all { it }) {
            if (allocatedBytes.none { it }) {
                return@check "Trying to $operation unallocated memory with type ${pointer.targetType} at $${pointer.address}"
            }
            val stringAllocated = allocatedBytes.joinToString("") { if (it) "_" else "x" }
            "Trying to $operation memory that is not entirely allocated: \n" +
                    "\t${pointer.targetType.typeSize} bytes at $${pointer.address} look like this: $stringAllocated,\n" +
                    "\twhere 'x' means that memory is not allocated"
        }
    }

    private fun ensureSize(targetSize: Long) {
        if (data.size >= targetSize) return
        check(targetSize <= Int.MAX_VALUE)
        val newData = ByteArray(maxOf(data.size * 2, targetSize.toInt()))
        System.arraycopy(data, 0, newData, 0, data.size)
        data = newData
    }

    private class MemoryChunk(val startAddress: Long, val size: Long)
}