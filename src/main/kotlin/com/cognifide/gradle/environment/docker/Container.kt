package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.environment.docker.container.ContainerException
import com.cognifide.gradle.environment.docker.container.DevOptions
import com.cognifide.gradle.environment.docker.container.HostFileManager
import com.cognifide.gradle.environment.docker.exec.DirConfig
import org.gradle.internal.os.OperatingSystem
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

@Suppress("TooManyFunctions")
class Container(val docker: Docker, val name: String) {

    val environment = docker.environment

    val common = environment.common

    private val logger = common.logger

    val internalName get() = "${docker.stack.internalName.get()}_$name"

    val host = HostFileManager(this)

    fun host(options: HostFileManager.() -> Unit) {
        host.apply(options)
    }

    var resolveAction: HostFileManager.() -> Unit = {}

    fun resolve(action: HostFileManager.() -> Unit) {
        resolveAction = action
    }

    fun resolve() {
        resolveAction(host)
    }

    var upAction: Container.() -> Unit = {}

    fun up(action: Container.() -> Unit) {
        upAction = action
        lockRequired.add(LOCK_UP)
    }

    var reloadAction: Container.() -> Unit = {}

    fun reload(action: Container.() -> Unit) {
        reloadAction = action
    }

    val devOptions = DevOptions(this)

    fun dev(options: DevOptions.() -> Unit) {
        devOptions.apply(options)
    }

    var runningTimeout = common.prop.long("environment.docker.container.runningTimeout") ?: 30_000L

    val id: String?
        get() {
            try {
                logger.debug("Determining ID for Docker container '$internalName'")

                val containerId = DockerProcess.execString {
                    withArgs("ps", "-l", "-q", "-f", "name=$internalName")
                    withTimeoutMillis(runningTimeout)
                }

                return if (containerId.isBlank()) {
                    null
                } else {
                    containerId
                }
            } catch (e: DockerException) {
                throw ContainerException("Failed to load Docker container ID for name '$internalName'!", e)
            }
        }

    val running: Boolean
        get() {
            val currentId = id ?: return false

            return try {
                logger.debug("Checking running state of Docker container '$name'")

                DockerProcess.execString {
                    withArgs("inspect", "-f", "{{.State.Running}}", currentId)
                    withTimeoutMillis(runningTimeout)
                }.toBoolean()
            } catch (e: DockerException) {
                throw ContainerException("Failed to check Docker container '$name' state!", e)
            }
        }

    val up: Boolean get() = running && isLocked(LOCK_UP)

    private val lockRequired = mutableSetOf<String>()

    var awaitRetry = common.retry {
        afterSecond(this@Container.common.prop.long("environment.docker.container.awaitRetry") ?: 60)
    }

    fun await() {
        common.progressIndicator {
            message = "Awaiting container '$name'"
            Behaviors.waitUntil(awaitRetry.delay) { timer ->
                val running = this@Container.running
                if (timer.ticks == awaitRetry.times && !running) {
                    mutableListOf<String>().apply {
                        add("Failed to await container '$name'!")

                        if (OperatingSystem.current().isWindows) {
                            add("Ensure having shared drives configured and reset performed after changing Windows credentials.")
                        }

                        add("Consider troubleshooting:")
                        add("* using command: 'docker stack ps ${docker.stack.internalName.get()} --no-trunc'")
                        add("* restarting Docker")

                        throw ContainerException(joinToString("\n"))
                    }
                }

                !running
            }
        }
    }

    fun up() {
        await()
        upAction()
        lock(LOCK_UP)
    }

    fun reload() {
        reloadAction()
    }

    fun exec(execSpec: DockerExecSpec.() -> Unit): DockerResult {
        val spec = DockerExecSpec(docker).apply {
            id.set(this@Container.id)
            apply(execSpec)
        }
        val operation = spec.operation.get()

        lateinit var result: DockerResult
        val action = {
            try {
                result = exec(spec)
            } catch (e: DockerException) {
                logger.debug("Exec operation \"$operation\" error", e)
                throw ContainerException("Failed to perform operation \"$operation\" on container '$name'!\n${e.message}")
            }
        }

        if (spec.indicator.get()) {
            common.progress {
                step = "Container '$name'"
                message = operation

                action()
            }
        } else {
            action()
        }

        return result
    }

    fun exec(args: List<String>, exitCode: Int? = 0) = exec {
        this.args.set(args)
        this.exitCode(exitCode)
    }

    fun exec(operation: String, args: List<String>, exitCode: Int? = 0) = exec {
        this.operation.set(operation)
        this.args.set(args)
        this.exitCode(exitCode)
    }

    fun execShell(command: String, exitCode: Int? = 0) = exec {
        this.argsShell(command)
        this.exitCode(exitCode)
    }

    fun execShell(operation: String, command: String, exitCode: Int? = 0) = exec {
        this.operation.set(operation)
        this.argsShell(command)
        this.exitCode(exitCode)
    }

    fun execShellQuiet(command: String, exitCode: Int? = 0) = exec {
        this.indicator.set(false)
        this.argsShell(command)
        this.exitCode(exitCode)
    }

    fun execAsStream(spec: DockerExecSpec.() -> Unit) = ByteArrayOutputStream().also { out ->
        exec {
            nullOut()
            output.set(out)
            spec()
        }
        return out
    }

    fun execAsString(spec: DockerExecSpec.() -> Unit) = String(execAsStream(spec).toByteArray(), StandardCharsets.UTF_8)

    fun ensureFile(vararg paths: String) = ensureFile(paths.asIterable())

    fun ensureFile(paths: Iterable<String>) {
        val dirPaths = paths.filter { it.contains("/") }
                .map { it.substringBeforeLast("/") }
                .filter { it.isNotBlank() }
                .toSet()
        ensureDir(dirPaths)

        val command = "touch ${paths.joinToString(" ")}"
        when (paths.count()) {
            0 -> logger.info("No files to ensure on container '$name'")
            1 -> execShell("Ensuring file '${paths.first()}'", command)
            else -> execShell("Ensuring files (${paths.count()})", command)
        }
    }

    fun ensureDir(vararg paths: String) = ensureDir(paths.toList())

    fun ensureDir(paths: Iterable<String>) {
        val command = "mkdir -p ${paths.joinToString(" ")}"
        when (paths.count()) {
            0 -> logger.info("No directories to ensure on container '$name'")
            1 -> execShell("Ensuring directory '${paths.first()}'", command)
            else -> execShell("Ensuring directories (${paths.count()})", command)
        }
    }

    fun configureDir(vararg paths: String, config: DirConfig.() -> Unit) = configureDir(paths.asIterable(), config)

    fun configureDir(paths: Iterable<String>, config: DirConfig.() -> Unit) {
        val c = DirConfig().apply(config)
        val command = mutableListOf<String>().apply {
            if (c.owner.isNotBlank() && c.group.isNotBlank()) add("chown -R ${c.owner}:${c.group} ${paths.joinToString(" ")}")
            if (c.mode.isNotBlank()) add("chmod ${c.mode} ${paths.joinToString(" ")}")
        }.joinToString(" && ")
        when (paths.count()) {
            0 -> logger.info("No directories to configure on container '$name'")
            1 -> execShell("Configuring directory '${paths.first()}'", command)
            else -> execShell("Configuring directories (${paths.count()})", command)
        }
    }

    fun cleanDir(vararg paths: String) = cleanDir(paths.asIterable())

    fun cleanDir(paths: Iterable<String>) {
        val command = paths.joinToString(" && ") { "(test ! -d $it || find $it -mindepth 1 -exec rm -fr {} +)" }
        when (paths.count()) {
            1 -> execShell("Cleaning contents of directory at path '${paths.first()}'", command)
            else -> execShell("Cleaning contents of directories (${paths.count()})", command)
        }
    }

    fun symlink(sourcePath: String, targetPath: String) = symlink(mapOf(sourcePath to targetPath))

    fun symlink(vararg sourceTargetPairs: Pair<String, String>) = symlink(sourceTargetPairs.toMap())

    fun symlink(sourceTargetMap: Map<String, String>, force: Boolean = true) {
        when (sourceTargetMap.size) {
            0 -> logger.info("No paths to be symlinked on container '$name'")
            else -> {
                val command = sourceTargetMap.entries.joinToString(" && ") {
                    "${if (force) "ln -f -s" else "ln -s"} ${it.key} ${it.value}"
                }
                execShell("Symlinking files (${sourceTargetMap.size})", command)
            }
        }
    }

    private fun exec(spec: DockerExecSpec): DockerResult {
        if (!running) {
            throw ContainerException("Cannot exec command '${spec.command.get()}' since Docker container '$name' is not running!")
        }
        logger.info("Executing command '${spec.command.get()}' for Docker container '$name'")
        return DockerProcess.execSpec(spec)
    }

    private fun isLockRequired(name: String) = lockRequired.contains(name)

    private fun lock(name: String) {
        if (isLockRequired(name)) {
            execShellQuiet("mkdir -p $LOCK_ROOT && touch $LOCK_ROOT/$name")
        }
    }

    private fun isLocked(name: String): Boolean {
        return !isLockRequired(name) || execShellQuiet("test -f $LOCK_ROOT/$name", null).exitCode == 0
    }

    companion object {
        const val LOCK_ROOT = "/var/gap/lock"

        const val LOCK_UP = "up"
    }
}

val Collection<Container>.names: String
    get() = if (isNotEmpty()) joinToString(", ") { it.name } else "none"
