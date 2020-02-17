package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.environment.EnvironmentExtension
import kotlinx.coroutines.*
import org.apache.commons.io.output.TeeOutputStream
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils
import java.io.FileOutputStream

class Docker(val environment: EnvironmentExtension) {

    private val logger = environment.logger

    private val common = environment.common

    val running: Boolean get() = stack.running && containers.running

    val up: Boolean get() = stack.running && containers.up

    /**
     * Represents Docker stack named 'aem' and provides API for manipulating it.
     */
    val stack = Stack(environment)

    /**
     * Provides API for manipulating Docker containers defined in 'docker-compose.yml'.
     */
    val containers = ContainerManager(this)

    /**
     * Configure additional behavior for Docker containers defined in 'docker-compose.yml'.
     */
    fun containers(options: ContainerManager.() -> Unit) {
        containers.apply(options)
    }

    /**
     * Represents Docker runtime specific options.
     * On Windows there could be installed Docker distribution named  'Desktop' or 'Toolbox'.
     */
    val runtimeType = common.obj.typed<Runtime> {
        convention(common.obj.provider { Runtime.detect(environment) })
    }

    fun runtimeType(name: String) {
        runtimeType.set(Runtime.of(environment, name))
    }

    val composeFile = common.obj.relativeFile(environment.rootDir, "docker-compose.yml")

    val composeTemplateFile = common.obj.relativeFile(environment.sourceDir, "docker-compose.yml.peb")

    // Shorthands useful to be used in template: 'docker-compose.yml.peb'

    val runtime get() = runtimeType.get()

    val configPath: String get() = runtime.determinePath(environment.sourceDir.get().asFile)

    val rootPath: String get() = runtime.determinePath(environment.rootDir.get().asFile)

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
        common.prop.expand(targetFile, mapOf("docker" to this))
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

    fun run(spec: RunSpec.() -> Unit) = runInteractive(spec)

    fun run(image: String, command: String, exitCode: Int = 0) = run {
        this.image = image
        this.command = command
        this.exitCodes = listOf(exitCode)
    }

    fun runShell(image: String, command: String, exitCode: Int = 0) = run(image, "sh -c '$command'", exitCode)

    fun run(operation: String, image: String, command: String, exitCode: Int = 0) = run {
        this.operation(operation)
        this.image = image
        this.command = command
        this.exitCode(exitCode)
    }

    fun runShell(operation: String, image: String, command: String, exitCode: Int = 0) = run(operation, image, "sh -c '$command'", exitCode)

    private fun runInteractive(spec: RunSpec.() -> Unit) = runInteractive(RunSpec(environment).apply(spec))

    private fun runInteractive(spec: RunSpec): DockerResult {
        val operation = spec.operation

        lateinit var result: DockerResult
        val action = {
            try {
                result = runInternal(spec)
            } catch (e: DockerException) {
                logger.debug("Run operation '$operation' error", e)
                throw DockerException("Failed to run operation on Docker!\n$operation\n${e.message}")
            }
        }

        if (spec.indicator) {
            common.progress {
                message = operation
                action()
            }
        } else {
            action()
        }

        return result
    }

    private fun runInternal(spec: RunSpec): DockerResult {
        if (spec.image.isBlank()) {
            throw DockerException("Run image cannot be blank!")
        }

        val customSpec = DockerCustomSpec(spec, mutableListOf<String>().apply {
            add("run")
            spec.name?.let { add("--name=$it") }
            if (spec.detached) add("-d")
            addAll(spec.volumes.map { (localPath, containerPath) -> "-v=${runtime.determinePath(localPath)}:$containerPath" })
            addAll(spec.ports.map { (hostPort, containerPort) -> "-p=$hostPort:$containerPort" })
            addAll(spec.options.apply { if (spec.cleanup) add("--rm") }.toSet())
            add(spec.image)
            addAll(DockerProcess.commandToArgs(spec.command))
        })

        logger.info("Running Docker command '${customSpec.fullCommand}'")

        return DockerProcess.execSpec(customSpec)
    }

    fun daemon(spec: DaemonSpec.() -> Unit) = daemonInteractive(spec)

    private fun daemonInteractive(options: DaemonSpec.() -> Unit) {
        val spec = DaemonSpec(environment).apply {
            options()

            if (image.isBlank()) {
                throw DockerException("Docker daemon specification require image to be defined!")
            }
            if (id == null) {
                val outputId = StringUtils.replaceEach(image, arrayOf("/", ":"), arrayOf(".", "."))
                id = when {
                    unique -> "$outputId.${daemonUniqueId()}"
                    else -> outputId
                }
            }
            if (name == null) {
                name = "${stack.internalName}_$id"
            }
        }

        common.progress {
            step = "Docker daemon"
            message = spec.operation

            daemonInternal(spec)
        }
    }

    @Suppress("MagicNumber")
    private fun daemonUniqueId() = RandomStringUtils.random(8, true, true)

    private fun daemonInternal(spec: DaemonSpec) = runBlocking {
        if (spec.stopPrevious) {
            logger.info("Stopping previous Docker daemon \"${spec.operation}\"")
            DockerProcess.execQuietly { withArgs("kill", spec.name) }
        }

        logger.lifecycle("Starting Docker daemon \"${spec.operation}\" with logs written to file: ${spec.outputFile}")
        val runJob = async(Dispatchers.IO) {
            spec.ignoreExitCodes()
            spec.outputFile.parentFile.mkdirs()

            val fileOutput = FileOutputStream(spec.outputFile)
            spec.output = when {
                spec.output != null -> TeeOutputStream(spec.output, fileOutput)
                else -> fileOutput
            }
            spec.errors = when {
                spec.errors != null -> TeeOutputStream(spec.errors, fileOutput)
                else -> fileOutput
            }

            runInternal(spec)
        }

        delay(spec.initTime)

        while (runJob.isActive) {
            val answer = common.userInput.askYesNoQuestion("Daemon \"${spec.operation}\" is running. Stop it?")
            if (answer != null && answer == true) {
                break
            }
        }

        logger.info("Stopping current Docker daemon \"${spec.operation}\"")
        DockerProcess.execQuietly { withArgs("kill", spec.name) }
        runJob.cancelAndJoin()

        logger.lifecycle("Stopped Docker daemon \"${spec.operation}\"")
    }
}
