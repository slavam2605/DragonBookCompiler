package compiler.ir.cfg

abstract class ExtensionHolder {
    private val extensions = mutableMapOf<ExtensionKey<*>, Any>()

    fun <T: Any> putExtension(key: ExtensionKey<T>, extension: T) {
        extensions[key] = extension
    }

    fun <T: Any> getExtension(key: ExtensionKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return extensions[key]?.let { it as T }
    }

    fun <T: Any> removeExtension(key: ExtensionKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return extensions.remove(key)?.let { it as T }
    }

    fun <T: Any> getOrCompute(key: ExtensionKey<T>, compute: () -> T): T {
        return getExtension(key) ?: compute().also {
            putExtension(key, it)
        }
    }
}

data class ExtensionKey<T: Any>(private val name: String)