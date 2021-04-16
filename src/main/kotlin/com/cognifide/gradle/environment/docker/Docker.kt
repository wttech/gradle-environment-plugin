package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.environment.EnvironmentExtension
import kotlinx.coroutines.*
import org.apache.commons.io.output.TeeOutputStream
import java.io.FileOutputStream

class Docker(val environment: EnvironmentExtension) {

    private val logger = environment.logger

    private val common = environment.common

    val running: Boolean get() = stack.running && containers.running

    val up: Boolean get() = stack.running && containers.up

    val upToDate get() = composeFile.get().asFile.exists()

    /**
     * Represents Docker stack and provides API for manipulating it.
     */
    val stack by lazy { Stack(environment) }

    /**
     * Configures Docker stack
     */
    fun stack(options: Stack.() -> Unit) = stack.using(options)

    /**
     * Provides API for manipulating Docker containers defined in 'docker-compose.yml'.
     */
    val containers by lazy { ContainerManager(this) }

    /**
     * Configure additional behavior for Docker containers defined in 'docker-compose.yml'.
     */
    fun containers(options: ContainerManager.() -> Unit) = containers.using(options)

    /**
     * Represents Docker runtime specific options.
     * On Windows there could be installed Docker distribution named  'Desktop' or 'Toolbox'.
     */
    val runtime by lazy { Runtime.determine(environment) }

    val composeFile = common.obj.relativeFile(environment.rootDir, "docker-compose.yml")

    val composeTemplateFile = common.obj.relativeFile(environment.sourceDir, "docker-compose.yml.peb")

    val composeProperties = common.obj.map<String, Any?> {
        set(
            mapOf(
                "configPath" to runtime.determinePath(environment.sourceDir.get().asFile),
                "workPath" to runtime.determinePath(environment.rootDir.get().asFile),
                "rootPath" to runtime.determinePath(environment.project.rootProject.projectDir),
                "homePath" to runtime.determinePath(environment.project.file(System.getProperty("user.home")))
            )
        )
    }

    fun init() {
        syncComposeFile()
        containers.resolve()
    }

    private fun syncComposeFile() {
        val templateFile = composeTemplateFile.get().asFile
        val targetFile = composeFile.get().asFile

        logger.info("Generating Docker compose file '$targetFile' from template '$templateFile'")

        if (!templateFile.exists()) {
            throw DockerException("Docker compose file template does not exist: $templateFile")
        }

        targetFile.takeIf { it.exists() }?.delete()
        templateFile.copyTo(targetFile)
        common.prop.expandFile(targetFile, composeProperties.get() + mapOf(
                "docker" to this,
                "project" to common.project
        ))
    }

    fun up() {
        stack.reset()
        containers.up()
    }

    fun reload() {
        containers.reload()
    }

    fun down() {
        stack.undeploy()
    }

    fun run(spec: DockerRunSpec.() -> Unit) = runInteractive(spec)

    fun run(image: String, args: List<String>, exitCode: Int = 0) = run {
        this.image.set(image)
        this.args.set(args)
        this.exitCode(exitCode)
    }

    fun runShell(image: String, command: String, exitCode: Int = 0) = run(image, listOf("sh -c '$command'"), exitCode)

    fun run(operation: String, image: String, args: List<String>, exitCode: Int = 0) = run {
        this.operation.set(operation)
        this.image.set(image)
        this.args.set(args)
        this.exitCode(exitCode)
    }

    fun runShell(operation: String, image: String, command: String, exitCode: Int = 0) = run(operation, image, listOf("sh -c '$command'"), exitCode)

    private fun runInteractive(spec: DockerRunSpec.() -> Unit) = runInteractive(DockerRunSpec(this).apply(spec))

    private fun runInteractive(spec: DockerRunSpec): DockerResult {
        val operation = spec.operation.get()

        lateinit var result: DockerResult
        val action = {
            try {
                result = runInternal(spec)
            } catch (e: DockerException) {
                logger.debug("Run operation '$operation' error", e)
                throw DockerException("Failed to run operation on Docker!\n$operation\n${e.message}")
            }
        }

        when {
            spec.indicator.get() -> common.progress {
                message = operation
                action()
            }
            else -> action()
        }

        return result
    }

    private fun runInternal(spec: DockerRunSpec): DockerResult {
        logger.info("Running Docker command: ${spec.command}")
        return DockerProcess.execSpec(spec)
    }

    fun daemon(spec: DockerDaemonSpec.() -> Unit) = daemonInteractive(spec)

    private fun daemonInteractive(options: DockerDaemonSpec.() -> Unit) {
        val spec = DockerDaemonSpec(this).apply(options)
        common.progress {
            step = "Docker daemon"
            message = spec.operation.get()
            daemonInternal(spec)
        }
    }

    private fun daemonInternal(spec: DockerDaemonSpec) = runBlocking {
        val operation = spec.operation.get()
        if (spec.stopPrevious.get()) {
            logger.info("Stopping previous Docker daemon \"$operation\"")
            DockerProcess.execQuietly { withArgs("kill", spec.name.get()) }
        }

        val outFile = spec.outputFile.get().asFile.apply { parentFile.mkdirs() }
        val outFileStream = FileOutputStream(outFile)

        logger.lifecycle("Starting Docker daemon \"$operation\" with logs written to file: $outFile")
        val runJob = async(Dispatchers.IO) {
            spec.ignoreExitCodes()
            spec.output.set(when {
                spec.output.isPresent -> TeeOutputStream(spec.output.get(), outFileStream)
                else -> outFileStream
            })
            spec.errors.set(when {
                spec.errors.isPresent -> TeeOutputStream(spec.errors.get(), outFileStream)
                else -> outFileStream
            })

            runInternal(spec)
        }

        delay(spec.initTime.get())

        while (runJob.isActive) {
            val answer = common.userInput.askYesNoQuestion("Daemon \"$operation\" is running. Stop it?")
            if (answer != null && answer == true) {
                break
            }
        }

        logger.info("Stopping current Docker daemon \"$operation\"")
        DockerProcess.execQuietly { withArgs("kill", spec.name.get()) }
        runJob.cancelAndJoin()

        logger.lifecycle("Stopped Docker daemon \"$operation\"")
    }
}
