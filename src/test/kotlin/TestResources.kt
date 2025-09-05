import java.io.File

object TestResources {
    fun getFile(name: String): File {
        val url = javaClass.getResource(name) ?: error("Resource not found: $name")
        if (url.protocol != "file") error("Unsupported protocol: ${url.protocol}")
        return File(url.toURI())
    }
}