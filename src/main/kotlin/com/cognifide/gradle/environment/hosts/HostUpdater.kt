package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.environment.EnvironmentException
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

class HostUpdater(val common: CommonExtension) {

    private val project = common.project

    private val logger = project.logger

    val interactive = common.obj.boolean {
        convention(true)
        common.prop.boolean("hosts.updater.interactive")?.let { set(it) }
    }

    val workDir = common.obj.dir {
        convention(project.layout.buildDirectory.dir("hosts"))
        common.prop.file("hosts.updater.workDir")?.let { set(it) }
    }

    val targetFile = common.obj.file {
        fileProvider(common.obj.provider {
            project.file(when {
                OperatingSystem.current().isWindows -> """C:\Windows\System32\drivers\etc\hosts"""
                else -> "/etc/hosts"
            })
        })
        common.prop.file("hosts.updater.targetFile")?.let { set(it) }
    }

    val section = common.obj.string {
        convention(project.rootProject.name)
        common.prop.string("hosts.updater.section")?.let { set(it) }
    }

    @Suppress("MaxLineLength")
    fun update(hosts: Iterable<Host>) {
        val os = OperatingSystem.current()
        val osFile = targetFile.get()

        val dir = workDir.get().asFile.apply { mkdirs() }

        val entriesFile = dir.resolve("hosts.txt").apply {
            logger.info("Generating hosts entries file: $this")
            writeText(hosts.joinToString(System.lineSeparator()) { it.text })
        }
        val updaterJar = dir.resolve("hosts.jar").apply {
            logger.info("Providing hosts updater program: $this")
            outputStream().use { output ->
                this@HostUpdater.javaClass.getResourceAsStream("/hosts.jar").use { input ->
                    input.copyTo(output)
                }
            }
        }
        val sectionName = section.get()

        if (os.isWindows && interactive.get()) {
            val scriptFile = dir.resolve("hosts.bat")
            logger.info("Generating hosts updating script: $scriptFile")

            scriptFile.writeText("""
                powershell -command "Start-Process cmd -ArgumentList '/C cd %CD% && java -jar $updaterJar $sectionName $entriesFile $osFile' -Verb runas"
            """.trimIndent())
            execAndHandleErrors(listOf("cmd", "/C", scriptFile.toString()))
            return
        }

        val scriptFile = dir.resolve("hosts.sh")
        logger.info("Generating hosts updating script: $scriptFile")

        if (os.isMacOsX && interactive.get()) {
            scriptFile.writeText("""
                    #!/bin/sh
                    osascript -e "do shell script \"java -jar $updaterJar $sectionName $entriesFile $osFile\" with prompt \"Gradle Environment Hosts\" with administrator privileges" 
                """.trimIndent())
            execOnMacAndHandleErrors(listOf("sh", scriptFile.toString()))
            return
        }

        scriptFile.writeText("""
                    #!/bin/sh
                    java -jar $updaterJar $sectionName $entriesFile $osFile
                """.trimIndent())
        logger.lifecycle("To update environment hosts, run script below as administrator/super-user:\n$scriptFile")
    }

    private fun execOnMacAndHandleErrors(commandLine: List<String>) = execAndHandleErrors(commandLine) { errorText ->
        if (errorText.contains("Unable to access jarfile")) {
            mutableListOf<String>().apply {
                add("Failed to update environment hosts. Unable to access executable. Probably project source files are placed under")
                add("the 'Documents', 'Desktop' or 'Downloads' directory. This may cause errors related to accessing files in the future.")

                add("Consider troubleshooting:")
                add("* move project files outside of 'Documents', 'Desktop' or 'Downloads' directories to avoid problems")
                add("* or set the host updater work directory to a path directly under your files home in your gradle.properties file as a workaround:")
                add("    * environment.hosts.updater.workDir=${System.getProperty("user.home")}/.gap/hosts")

                logger.error(joinToString("\n"))
            }
        }
        throw HostException(errorText)
    }

    private fun execAndHandleErrors(commandLine: List<String>, errorHandler: (String) -> Unit = { throw EnvironmentException(it) }) {
        val errorOutput = ByteArrayOutputStream()
        val execResult = project.exec { spec ->
            spec.errorOutput = errorOutput
            spec.isIgnoreExitValue = true
            spec.commandLine(commandLine)
        }
        if (execResult.exitValue == 0) {
            logger.lifecycle("Environment hosts successfully updated.")
            return
        }
        errorHandler(errorOutput.toString("UTF8"))
    }
}
