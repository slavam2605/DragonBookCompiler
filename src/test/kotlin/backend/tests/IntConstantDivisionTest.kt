package backend.tests

import compiler.backend.arm64.ops.integers.MagicIntConstantDivision
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import utils.absIsPowerOfTwo
import kotlin.math.absoluteValue
import kotlin.random.Random

class IntConstantDivisionTest {
    @Test
    fun testCenterDivisors() {
        testDivisorSet(-1000L..1000L)
    }

    @Test
    fun testMediumDivisors() {
        testDivisorSet(
            1001L..100000L step 97,
            -100000L..-1001L step 97
        )
    }

    @Test
    fun testLargeDivisors() {
        // Focus on divisors that stress the magic algorithm
        val divisors = mutableSetOf<Long>()

        // Powers of 2 ± 1 (critical edge cases)
        for (bitPos in 20..62) {
            val pow = 1L shl bitPos
            divisors.add(pow - 1)
            divisors.add(pow + 1)
            divisors.add(-(pow - 1))
            divisors.add(-(pow + 1))
        }

        // Large primes (test with primes up to ~10^15)
        divisors.addAll(listOf(
            1000000007L, 1000000009L, 1000000021L,
            10000000019L, 10000000033L, 10000000061L,
            100000000003L, 100000000019L, 100000000057L,
            1000000000039L, 1000000000061L, 1000000000063L,
            10000000000037L, 10000000000051L,
            100000000000031L, 100000000000067L,
            1000000000000037L, 1000000000000091L
        ))

        // Add negative versions
        val withNegatives = (divisors + divisors.map { -it }).toSet()

        testDivisorSet(withNegatives)
    }

    @Test
    fun testVeryLargeDivisors() {
        // Divisors near Long.MAX_VALUE
        // Use reduced dividend range since these are expensive
        val divisors = mutableSetOf<Long>()

        // Divisors near MAX_VALUE
        for (offset in 1L..10000L step 100) {
            divisors.add(Long.MAX_VALUE - offset)
            divisors.add(Long.MIN_VALUE + offset)
        }

        // Divisors that are fractions of MAX_VALUE
        for (denominator in 2L..100L) {
            divisors.add(Long.MAX_VALUE / denominator)
            divisors.add(-(Long.MAX_VALUE / denominator))
        }

        testDivisorSet(divisors)
    }

    @Test
    fun testExtremeDivisors() {
        testDivisorSet(
            Long.MIN_VALUE..Long.MIN_VALUE + 1000,
            Long.MAX_VALUE - 1000..Long.MAX_VALUE
        )
    }

    @Test
    fun testCloseToPowerTwoDivisors() {
        val divisors = mutableSetOf<Long>()

        for (bitPos in 10..30) {
            val pow = 1L shl bitPos
            // Test range around each power of 2
            for (offset in -30L..30L) {
                val d = pow + offset
                if (d > 0 && !d.absIsPowerOfTwo()) {
                    divisors.add(d)
                    divisors.add(-d)
                }
            }
        }

        testDivisorSet(divisors)
    }

    @Test
    fun testSpecialDivisors() {
        // Divisors of 2^63 + 1, m(-d) != -m(d)
        testDivisorSet(setOf(3L, 19L, 43L, 5419L, 77158673929L))
    }

    private fun testDivisorSet(vararg divisorRanges: Iterable<Long>) {
        val wholeSet = divisorRanges.flatMapTo(mutableSetOf()) { it }
        for (d in wholeSet) {
            if (d == 0L || d.absIsPowerOfTwo()) continue

            val magic = MagicIntConstantDivision.getMagic(d)

            // 1. Edge cases
            testValue(Long.MIN_VALUE, d, magic)
            testValue(Long.MIN_VALUE + 1, d, magic)
            testValue(Long.MAX_VALUE, d, magic)
            testValue(Long.MAX_VALUE - 1, d, magic)

            // 2. Dense center range
            for (i in -100000 until 100000) {
                testValue(i.toLong(), d, magic)
            }

            // 3. Powers of 2 and adjacent values
            for (bitPos in 20..62) {
                val pow = 1L shl bitPos
                testValue(pow, d, magic)
                testValue(-pow, d, magic)
                testValue(pow - 1, d, magic)
                testValue(pow + 1, d, magic)
                testValue(-pow - 1, d, magic)
                testValue(-pow + 1, d, magic)
            }

            // 4. Divisor multiples
            testDivisorMultiples(d, magic)

            // 5. Random sampling across the full range
            val random = Random(d.hashCode())
            repeat(10000) {
                testValue(random.nextLong(), d, magic)
            }
        }
    }

    private fun testDivisorMultiples(d: Long, magic: MagicIntConstantDivision.Magic) {
        val maxMultiplier = Long.MAX_VALUE / d.absoluteValue

        // Small multipliers (exhaustive)
        for (k in -100L..100L) {
            val n = k * d
            testValue(n, d, magic)
            testValue(n - 1, d, magic)
            testValue(n + 1, d, magic)
        }

        // Large multipliers (logarithmic sampling)
        for (bitPos in 7..62) {
            val k = (1L shl bitPos).let { if (it > maxMultiplier) return else it }
            val n = k * d
            testValue(n, d, magic)
            testValue(n - 1, d, magic)
            testValue(n + 1, d, magic)
            testValue(-n, d, magic)
            testValue(-n - 1, d, magic)
            testValue(-n + 1, d, magic)
        }
    }

    private fun testValue(value: Long, d: Long, magic: MagicIntConstantDivision.Magic) {
        val expectedDiv = value / d
        val expectedMod = value % d
        magicDiv(value, d, magic)
        if (expectedDiv != DivResult) fail { "Wrong div for $value / $d: expected $expectedDiv, actual: $DivResult" }
        if (expectedMod != ModResult) fail { "Wrong mod for $value % $d: expected $expectedMod, actual: $ModResult" }
    }

    companion object {
        private var DivResult = 0L
        private var ModResult = 0L

        private fun magicDiv(n: Long, d: Long, m: MagicIntConstantDivision.Magic) {
            var q = Math.multiplyHigh(m.magic, n)
            if (d > 0 && m.magic < 0) {
                q += n
            }
            if (d < 0 && m.magic > 0) {
                q -= n
            }
            q = q shr m.shift // if m.shift > 0
            var t = q ushr 63
            q += t
            t = q * d
            val r = n - t
            DivResult = q
            ModResult = r
        }
    }
}