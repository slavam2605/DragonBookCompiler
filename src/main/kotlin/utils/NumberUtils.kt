package utils

import kotlin.math.absoluteValue

fun Long.isPowerOfTwo(): Boolean {
    if (this == 0L) return false
    val abs = absoluteValue
    // Works for Long.MIN_VALUE as well
    return (abs and abs - 1) == 0L
}