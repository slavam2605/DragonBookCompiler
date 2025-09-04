package utils

import java.util.concurrent.TimeUnit

fun runProcess(
    vararg command: String,
    redirectErrorStream: Boolean = true,
    timeoutInSeconds: Long = 3,
    exitCodeHandler: (Int, String) -> Unit = { code, output ->
        check(code == 0) { "Failed to execute command: ${command.joinToString(" ")}\n$output" }
    }
): String {
    val proc = ProcessBuilder(*command)
        .redirectErrorStream(redirectErrorStream)
        .start()

    if (!proc.waitFor(timeoutInSeconds, TimeUnit.SECONDS)) {
        proc.destroyForcibly().waitFor()
        error("Command haven't finished in $timeoutInSeconds seconds: ${command.joinToString()}")
    }

    val output = proc.inputStream.bufferedReader().readText()
    exitCodeHandler(proc.exitValue(), output)
    return output
}