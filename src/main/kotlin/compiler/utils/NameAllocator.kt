package compiler.utils

class NameAllocator(private val prefix: String) {
    private var nextIndex = 0

    fun newName(marker: String? = null) = "$prefix${marker ?: ""}_${nextIndex++}"
}