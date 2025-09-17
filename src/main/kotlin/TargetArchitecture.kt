enum class TargetArchitecture(val archName: String) {
    ARM64("arm64");

    companion object {
        val supportedArchitectures = entries.map { it.archName }

        fun get(archName: String): TargetArchitecture =
            entries.firstOrNull { it.archName == archName }
                ?: error("Unknown architecture: $archName")

        fun getCurrentArchitecture(): String {
            return when (val systemArch = System.getProperty("os.arch")) {
                "arm64", "aarch64" -> ARM64.archName
                else -> systemArch
            }
        }
    }
}