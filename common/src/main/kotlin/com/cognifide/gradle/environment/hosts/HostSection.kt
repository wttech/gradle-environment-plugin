package com.cognifide.gradle.environment.hosts

data class HostSection(val name: String, val entries: List<String>) {
    fun render() = mutableListOf<String>().apply {
        add("#environment-start")
        add("#name=$name")
        addAll(entries)
        add("#environment-end")
    }.joinToString(System.lineSeparator())

    override fun toString() = render()

    companion object {
        fun parseAll(text: String): List<HostSection> {
            val sections = mutableListOf<HostSection>()

            var section = false
            var sectionName = ""
            val sectionLines = mutableListOf<String>()

            text.lineSequence().forEach { line ->
                when (val l = line.trim()) {
                    "#environment-start" -> {
                        section = true
                    }
                    "#environment-end" -> {
                        sections.add(HostSection(sectionName, sectionLines.toList()))
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