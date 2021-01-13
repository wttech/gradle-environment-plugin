package com.cognifide.gradle.environment.hosts

import com.cognifide.gradle.common.CommonExtension
import com.cognifide.gradle.environment.EnvironmentException
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream

class HostUpdater(val common: CommonExtension) {

    private val project = common.project

    private val logger = project.logger

    val enabled = common.obj.boolean {
        convention(true)
        common.prop.boolean("hostUpdater.enabled")?.let { set(it) }
    }

    val interactive = common.obj.boolean {
        convention(true)
        common.prop.boolean("hostUpdater.interactive")?.let { set(it) }
    }

    val force = common.obj.boolean {
        set(common.prop.flag("hostUpdater.force"))
    }

    val workDir = common.obj.dir {
        convention(project.layout.buildDirectory.dir("hosts"))
        common.prop.file("hostUpdater.workDir")?.let { set(it) }
    }

    val targetFile = common.obj.file {
        fileProvider(common.obj.provider {
            project.file(when {
                OperatingSystem.current().isWindows -> """C:\Windows\System32\drivers\etc\hosts"""
                else -> "/etc/hosts"
            })
        })
        common.prop.file("hostUpdater.targetFile")?.let { set(it) }
    }

    val section = common.obj.string {
        convention(project.rootProject.name)
        common.prop.string("hostUpdater.section")?.let { set(it) }
    }

    fun update(hosts: Collection<Host>) = update { hosts }

    @Suppress("MaxLineLength", "LongMethod", "ComplexMethod")
    fun update(hostsProvider: () -> Collection<Host>) {
        if (!enabled.get()) {
            logger.info("Hosts file updater is disabled!")
            return
        }

        val os = OperatingSystem.current()
        val osFile = targetFile.get()
        val sectionName = section.get()
        val hosts = hostsProvider()

        val sectionOld = HostSection.parseAll(osFile.asFile.readText()).firstOrNull { it.name == sectionName }
        if (hosts.isEmpty() && sectionOld == null) {
            logger.info("Hosts file update is not needed!\n" +
                    "No hosts defined and no existing contents in file '$osFile'"
            )
            return
        }

        val sectionNew = HostSection(sectionName, hosts.map { it.text })
        if (!force.get() && sectionOld != null && sectionNew.render() == sectionOld.render()) {
            logger.info("Hosts file update is not needed!\n" +
                    "Existing contents in file '$osFile' are up-to-date':\n$sectionOld"
            )
            return
        }

        val cwd = workDir.get().asFile.apply { mkdirs() }
        val sectionFile = cwd.resolve("hosts.txt").apply {
            logger.info("Generating hosts file '$this' with contents:\n$sectionNew")
            writeText(sectionNew.render())
        }
        val updaterJar = cwd.resolve("hosts.jar").apply {
            logger.info("Providing hosts updater program: $this")
            outputStream().use { output ->
                this@HostUpdater.javaClass.getResourceAsStream("/hosts.jar").use { input ->
                    input.copyTo(output)
                }
            }
        }

        if (os.isWindows && interactive.get()) {
            val scriptFile = cwd.resolve("hosts.bat")
            logger.info("Generating hosts updating script: $scriptFile")

            scriptFile.writeText("""
                powershell -command "Start-Process cmd -ArgumentList '/C cd %CD% && java -jar $updaterJar $sectionFile $osFile' -Verb runas"
            """.trimIndent())
            execAndHandleErrors(listOf("cmd", "/C", scriptFile.toString()))
            return
        }

        val scriptFile = cwd.resolve("hosts.sh")
        logger.info("Generating hosts updating script: $scriptFile")

        if (os.isMacOsX && interactive.get()) {
            scriptFile.writeText("""
                    #!/bin/sh
                    osascript -e "do shell script \"java -jar $updaterJar $sectionFile $osFile\" with prompt \"Gradle Environment Hosts\" with administrator privileges" 
                """.trimIndent())
            execOnMacAndHandleErrors(listOf("sh", scriptFile.toString()))
            return
        }

        scriptFile.writeText("""
                    #!/bin/sh
                    java -jar $updaterJar $sectionFile $osFile
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
                add("    * hostUpdater.workDir=${System.getProperty("user.home")}/.gap/hosts")

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
