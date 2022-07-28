package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.common.utils.using
import com.cognifide.gradle.environment.EnvironmentExtension
import kotlinx.coroutines.*
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.provider.Provider
import org.gradle.process.internal.streams.SafeStreams
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

@Suppress("TooManyFunctions")
class Docker(val environment: EnvironmentExtension) {

    private val logger = environment.logger

    private val common = environment.common

    val running: Boolean get() = stack.running && containers.running

    val up: Boolean get() = stack.running && containers.up

    val upToDate get() = composeFile.get().asFile.exists() && composeFileContent() == generateComposeFileContent()

    /**
     * How long wait to wait until checking if environment remains up.
     * Checks if containers are not being restarted after running hooks.
     */
    val upCheck = common.obj.long {
        convention(0)
        common.prop.long("docker.upCheck")?.let { set(it) }
    }

    /**
     * Represents Docker stack and provides API for manipulating it.
     */
    val stack: Stack by lazy { Stack.determine(environment) }

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

    /**
     * Holds registry-related configuration options.
     */
    val registry by lazy { DockerRegistry(this) }

    /**
     * Configure registry-related options.
     */
    fun registry(options: DockerRegistry.() -> Unit) = registry.using(options)

    val composeFile = common.obj.relativeFile(environment.workDir, "docker-compose.yml")

    val composeTemplateFile = common.obj.relativeFile(environment.sourceDir, "docker-compose.yml.peb")

    val composeProperties = common.obj.map<String, Any?> {
        set(
            mapOf(
                "sourcePath" to runtime.determinePath(environment.sourceDir.get().asFile),
                "buildPath" to runtime.determinePath(environment.buildDir.get().asFile),
                "workPath" to runtime.determinePath(environment.workDir.get().asFile),
                "rootPath" to runtime.determinePath(environment.project.rootProject.projectDir),
                "homePath" to runtime.determinePath(environment.project.file(System.getProperty("user.home")))
            )
        )
    }

    fun init() {
        registry.loginAuto()
        resolve()
    }

    fun resolve() {
        generateComposeFile()
        containers.resolve()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun generateComposeFile() {
        val targetFile = composeFile.get().asFile
        val templateFile = composeTemplateFile.get().asFile

        try {
            logger.info("Generating Docker compose file '$targetFile' from template '$templateFile'")
            targetFile.apply {
                parentFile.mkdirs()
                writeText(generateComposeFileContent())
            }
        } catch (e: Exception) {
            throw DockerException("Cannot generate compose file '$targetFile'! Cause '${e.message}'", e)
        }
    }

    private fun composeFileContent() = composeFile.get().asFile.readText().trim()

    private fun generateComposeFileContent(): String {
        val templateFile = composeTemplateFile.get().asFile
        if (!templateFile.exists()) {
            throw DockerException("Docker compose file template does not exist: $templateFile")
        }
        return common.prop.expand(
            templateFile,
            composeProperties.get() + mapOf(
                "docker" to this,
                "project" to common.project
            )
        ).trim()
    }

    fun checkUp() {
        if (upCheck.get() > 0) {
            common.progress {
                step = "Docker checking"
                message = "Delaying after initial up"
                Behaviors.waitFor(upCheck.get())
                message = "Verifying if still up"
                if (!up) {
                    throw DockerException(
                        mutableListOf<String>().apply {
                            add("Docker environment was up only temporarily and its state changed after running up hooks!")
                            add(
                                "Most probably container manager like Docker Swarm has restarted the container" +
                                    " as its entrypoint exited with a non-zero status code."
                            )
                            add("")
                            addAll(stack.troubleshoot())
                        }.joinToString("\n")
                    )
                }
            }
        }
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

    fun runAsStream(spec: DockerRunSpec.() -> Unit) = ByteArrayOutputStream().also { out ->
        run {
            nullOut()
            output.set(out)
            spec()
        }
        return out
    }

    fun runAsString(spec: DockerRunSpec.() -> Unit) = String(runAsStream(spec).toByteArray(), StandardCharsets.UTF_8)

    fun run(image: String, args: List<String>, exitCode: Int = 0) = run {
        this.image.set(image)
        this.args.set(args)
        this.exitCode(exitCode)
    }

    fun runShell(image: String, command: String, exitCode: Int = 0) = run {
        this.image.set(image)
        this.argsShell(command)
        this.exitCode(exitCode)
    }

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
        logger.info("Running Docker command: ${spec.command.get().ifBlank { "<image_default>" }}")
        return DockerProcess().execSpec(spec)
    }

    @Suppress("TooGenericExceptionCaught")
    fun pull(image: String) {
        try {
            DockerProcess().exec {
                withArgs("pull", image)
                withOutputStream(SafeStreams.systemOut())
                withErrorStream(SafeStreams.systemErr())
            }
        } catch (e: Exception) {
            throw DockerException("Cannot pull Docker image '$image'! Cause '${e.message}'", e)
        }
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
            DockerProcess().execQuietly { withArgs("kill", spec.name.get()) }
        }

        val outFile = spec.outputFile.get().asFile.apply { parentFile.mkdirs() }
        val outFileStream = FileOutputStream(outFile)

        logger.lifecycle("Starting Docker daemon \"$operation\" with logs written to file: $outFile")
        val runJob = async(Dispatchers.IO) {
            spec.ignoreExitCodes()
            spec.output.set(
                when {
                    spec.output.isPresent -> TeeOutputStream(spec.output.get(), outFileStream)
                    else -> outFileStream
                }
            )
            spec.errors.set(
                when {
                    spec.errors.isPresent -> TeeOutputStream(spec.errors.get(), outFileStream)
                    else -> outFileStream
                }
            )

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
        DockerProcess().execQuietly { withArgs("kill", spec.name.get()) }
        runJob.cancelAndJoin()

        logger.lifecycle("Stopped Docker daemon \"$operation\"")
    }

    @Suppress("TooGenericExceptionCaught")
    fun load(file: File): String {
        val output = DockerProcess().execString {
            withArgs("load", "--input", file.absolutePath)
        }
        return output.lineSequence().firstOrNull { it.startsWith("Loaded image:") }
            ?.substringAfter(":")?.trim()
            ?: throw DockerException("Cannot determine loaded Docker image name from output:\n$output\n")
    }

    fun load(composePropertyName: String, fileProvider: () -> File) = load(composePropertyName, common.project.provider { fileProvider() })

    fun load(composePropertyName: String, fileProvider: Provider<File>) {
        property(composePropertyName) { load(fileProvider.get()) }
    }

    fun <T> property(name: String, valueProvider: () -> T) = property(name, common.project.provider { valueProvider() })

    /**
     * @see <https://github.com/gradle/gradle/issues/8500>
     */
    fun <T> property(name: String, valueProvider: Provider<T>) {
        composeProperties.putAll(common.project.provider { mapOf(name to valueProvider.get()) })
    }
}
