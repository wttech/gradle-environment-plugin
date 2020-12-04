import java.io.File
import kotlin.system.exitProcess
import com.cognifide.gradle.environment.hosts.HostSection

fun main(args: Array<String>) = try {
    if (args.size != 2) {
        println("Invalid arguments! Expected: [sectionFile] [hostsFile]")
        exitProcess(1)
    }

    val sourceFile = File(args[0])
    val targetFile = File(args[1])

    val text = targetFile.readText()
    val sections = HostSection.parseAll(text)

    val sourceSection = HostSection.parseAll(sourceFile.readText()).first()
    val targetSection = sections.find { it.name == sourceSection.name }

    if (targetSection != null) {
        targetFile.writeText(text.replace(targetSection.render(), sourceSection.render()))
    } else {
        targetFile.appendText("${System.lineSeparator()}${sourceSection.render()}")
    }
} catch (e: Exception) {
    println("Cannot update hosts file!")
    println("Ensure using administrator/super-user privileges.")
    println("Error details: ${e.message}")
    exitProcess(1)
}