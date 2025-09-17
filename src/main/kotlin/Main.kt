import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.*
import java.io.File

class MainCommand : CliktCommand(name = "DragonBookCompiler") {
    private val inputFile: String by argument("input file")
        .help("input file")

    private val outputFile: String by option("-o", "--out", metavar = "file")
        .required()
        .help("Output file")

    private val optimize: Boolean by option("-O", "--optimize")
        .flag()
        .help("Enable optimizations")

    private val arch: String by option("--arch", metavar = "architecture")
        .default(TargetArchitecture.getCurrentArchitecture())
        .help("Target architecture. Supported values: ${TargetArchitecture.supportedArchitectures.joinToString()}")
        .check({ "Unsupported architecture: $it" }) { it in TargetArchitecture.supportedArchitectures }

    override fun run() {
        val inputFile = File(inputFile)
        val outputFile = File(outputFile)
        val arch = TargetArchitecture.get(arch)
        CompilationFlow.compile(inputFile, outputFile, arch, optimize)
    }
}

fun main(args: Array<String>) = MainCommand().main(args)