import java.io.File
import java.net.URL

object TestResources {
    fun getFile(name: String): File {
        val url = getURL(name) ?: error("Resource not found: $name")
        if (url.protocol != "file") error("Unsupported protocol: ${url.protocol}")
        return File(url.toURI())
    }

    fun getURL(name: String): URL? {
        return javaClass.getResource(name)
    }
}