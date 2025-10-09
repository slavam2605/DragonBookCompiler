package backend.tests

import compiler.backend.arm64.ops.floats.FloatConstantDivision
import org.junit.jupiter.api.Test
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.assertEquals

class FloatConstantDivisionTest {
    @Test
    fun test() {
        val random = Random(181601433)
        val testValues = specialTestValues() + generateRandomNumbers(random) + generatePowersOfTwo()
        val totalCount = testValues.size
        var successCount = 0

        testValues.forEach { (power, value) ->
            val r = FloatConstantDivision.getReciprocal(value) ?: return@forEach
            successCount++

            repeat(1000) {
                val numerator = randomDouble(random)
                val expected = numerator / value
                val actual = numerator * r

                // Test exact equality, the optimization must preserve IEEE 754 behavior
                assertEquals(expected, actual, "Wrong value for $numerator / $value (2^$power):\n" +
                        "bits: ${value.toBitString64()}\n" +
                        "denormalized: ${value.isDenormalized()}")
            }
        }

        assertEquals(104207, totalCount, "Wrong total count")
        assertEquals(4096, successCount, "Wrong success count")
    }

    private fun randomDouble(random: Random): Double {
        val exponent = random.nextInt(-323, 309)
        val maxBound = 10.0.pow(exponent)
        return random.nextDouble(-maxBound, maxBound)
    }

    private fun Double.toBitString64() =
        toBits().toString(2).padStart(64, '0')

    private fun Double.isDenormalized(): Boolean {
        return this != 0.0 && this.absoluteValue < java.lang.Double.MIN_NORMAL
    }

    private fun specialTestValues(): List<Pair<Int?, Double>> {
        return listOf(
            0.0, -0.0, 1.0, -1.0,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
            Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE
        ).map { null to it }
    }

    private fun generateRandomNumbers(random: Random): List<Pair<Int?, Double>> {
        return (0 until 100000).map { null to randomDouble(random) }
    }

    private fun generatePowersOfTwo(): List<Pair<Int, Double>> {
        val powersOfTwo = mutableListOf<Pair<Int, Double>>()
        var current = 1.0
        var currentPower = 0
        while (current.isFinite()) {
            powersOfTwo.add(currentPower to current)
            current *= 2.0
            currentPower++
        }
        current = 0.5
        currentPower = -1
        while (current != 0.0) {
            powersOfTwo.add(currentPower to current)
            current /= 2.0
            currentPower--
        }
        powersOfTwo.sortBy { it.first }
        return powersOfTwo.flatMap { (power, value) ->
            // Test both positive and negative values
            listOf(power to value, power to -value)
        }
    }
}