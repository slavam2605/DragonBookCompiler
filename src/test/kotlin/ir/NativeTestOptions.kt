package ir

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class NativeTestOptions(
    val customNativeRunner: String? = null,
    val linkFiles: List<String> = emptyList(),
    val useCppCompiler: Boolean = false,
    val compilerFlags: List<String> = emptyList(),
    val testRunTimeout: Duration = 3.seconds
)