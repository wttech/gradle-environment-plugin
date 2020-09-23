import java.io.File

fun main(args: Array<String>) = try {
    val sourceName = args.getOrNull(0) ?: "mysite"
    val sourceFile = File(args.getOrNull(1) ?: ".gradle/environment/hosts.txt")
    val sourceSection = Section(sourceName, sourceFile.readText().lines().map { it.trim() })
    val targetFile = File(args.getOrNull(2) ?: "/etc/hosts")

    val text = targetFile.readText()
    val sections = Section.parseAll(text)

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
}

data class Section(val name: String, val entries: List<String>) {
    fun render() = mutableListOf<String>().apply {
        add("#environment-start")
        add("#name=$name")
        addAll(entries)
        add("#environment-end")
    }.joinToString(System.lineSeparator())

    companion object {
        fun parseAll(text: String): List<Section> {
            val sections = mutableListOf<Section>()

            var section = false
            var sectionName = ""
            val sectionLines = mutableListOf<String>()

            text.lineSequence().forEach { line ->
                when (val l = line.trim()) {
                    "#environment-start" -> {
                        section = true
                    }
                    "#environment-end" -> {
                        sections.add(Section(sectionName, sectionLines.toList()))
                        section = false
                        sectionLines.clear()
                    }
                    else -> {
                        if (section) {
                            if (l.startsWith("#name=")) {
                                sectionName = l.substringAfter("#name=")
                            } else {
                                sectionLines.add(l)
                            }
                        }
                    }
                }
            }

            return sections
        }
    }
}