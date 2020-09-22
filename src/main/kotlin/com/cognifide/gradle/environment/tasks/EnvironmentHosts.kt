package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.buildobjects.process.ProcBuilder
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

open class EnvironmentHosts : EnvironmentDefaultTask() {

    @TaskAction
    fun appendHosts() {
        val tmpFile = environment.rootDir.get().asFile.resolve("hosts.txt").apply {
            writeText(environment.hosts.appendix)
        }
        val osFile = environment.hosts.osFile.get()

        val sectionName = environment.docker.stack.internalName
        val editorFile = environment.rootDir.get().asFile.resolve("hosts.jar")

        if (OperatingSystem.current().isWindows) {
            val scriptFile = environment.rootDir.get().asFile.resolve("hosts.bat")
            scriptFile.writeText("""
                powershell -command "Start-Process cmd -ArgumentList '/C cd %CD% && java -jar $editorFile $sectionName $tmpFile $osFile' -Verb runas"
            """.trimIndent())
            project.exec {
                it.standardInput = System.`in`
                it.commandLine("cmd", "/C", scriptFile.toString())
            }
        } else {
            val scriptFile = environment.rootDir.get().asFile.resolve("hosts")
            scriptFile.writeText("""
                #!/bin/sh
                osascript -e "do shell script \"java -jar $editorFile $sectionName $tmpFile $osFile\" with prompt \"Gradle Environment Hosts\" with administrator privileges" 
            """.trimIndent())
            project.exec {
                it.standardInput = System.`in`
                it.commandLine("sh", scriptFile.toString())
            }
        }
    }

    init {
        description = "Prints environment hosts entries."
    }

    companion object {
        const val NAME = "environmentHosts"
    }
}
