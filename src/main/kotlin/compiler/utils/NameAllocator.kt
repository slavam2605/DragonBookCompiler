package compiler.utils

class NameAllocator(private val prefix: String) {
    private var nextIndex = 0

    fun newName(marker: String? = null) = "$prefix${marker ?: ""}_${nextIndex++}"

    fun advanceAfter(name: String) {
        val regex = "$prefix.*_([0-9]+)".toRegex()
        regex.matchEntire(name)?.let { match ->
            nextIndex = maxOf(nextIndex, match.groupValues[1].toInt() + 1)
        }
    }
}