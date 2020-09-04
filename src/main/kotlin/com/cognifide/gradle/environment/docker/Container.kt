package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.environment.docker.container.ContainerException
import com.cognifide.gradle.environment.docker.container.DevOptions
import com.cognifide.gradle.environment.docker.container.HostFileManager
import com.cognifide.gradle.environment.docker.container.ExecSpec
import org.gradle.internal.os.OperatingSystem

class Container(val docker: Docker, val name: String) {

    val environment = docker.environment

    val common = environment.common

    private val logger = common.logger

    val internalName = "${docker.stack.internalName}_$name"

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

    var runningTimeout = common.prop.long("environment.docker.container.runningTimeout") ?: 10000L

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

    val up: Boolean
        get() = running && isLocked(LOCK_UP)

    private val lockRequired = mutableSetOf<String>()

    var awaitRetry = common.retry { afterSecond(this@Container.common.prop.long("environment.docker.container.awaitRetry") ?: 60) }

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
                        add("* using command: 'docker stack ps ${docker.stack.internalName} --no-trunc'")
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

    fun exec(execSpec: ExecSpec.() -> Unit): DockerResult {
        val spec = ExecSpec(environment).apply(execSpec)
        val operation = spec.operation()

        lateinit var result: DockerResult
        val action = {
            try {
                result = exec(spec)
            } catch (e: DockerException) {
                logger.debug("Exec operation \"$operation\" error", e)
                throw ContainerException("Failed to perform operation \"$operation\" on container '$name'!\n${e.message}")
            }
        }

        if (spec.indicator) {
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

    fun exec(command: String, exitCode: Int? = 0) = exec {
        this.command = command
        this.exitCodes = exitCode?.run { listOf(this) } ?: listOf()
    }

    fun exec(operation: String, command: String, exitCode: Int? = 0) = exec {
        this.operation = { operation }
        this.command = command
        this.exitCodes = exitCode?.run { listOf(this) } ?: listOf()
    }

    fun execShell(command: String, exitCode: Int? = 0) = exec("sh -c '$command'", exitCode)

    fun execShell(operation: String, command: String, exitCode: Int? = 0) = exec(operation, "sh -c '$command'", exitCode)

    fun execShellQuiet(command: String, exitCode: Int? = 0) = exec {
        this.indicator = false
        this.command = "sh -c '$command'"
        this.exitCodes = exitCode?.run { listOf(this) } ?: listOf()
    }

    fun ensureFile(vararg paths: String) = ensureFile(paths.asIterable())

    fun ensureFile(paths: Iterable<String>) {
        ensureDir(paths.map { it.substringBeforeLast("/") }.toSet())

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

    fun cleanDir(vararg paths: String) = cleanDir(paths.asIterable())

    fun cleanDir(paths: Iterable<String>) {
        val command = "rm -fr ${paths.joinToString(" ") { "$it/*" }}"
        when (paths.count()) {
            1 -> execShell("Cleaning contents of directory at path '${paths.first()}'", command)
            else -> execShell("Cleaning contents of directories (${paths.count()})", command)
        }
    }

    private fun exec(spec: ExecSpec): DockerResult {
        if (spec.command.isBlank()) {
            throw ContainerException("Exec command cannot be blank!")
        }

        if (!running) {
            throw ContainerException("Cannot exec command '${spec.command}' since Docker container '$name' is not running!")
        }

        val customSpec = DockerCustomSpec(spec, mutableListOf<String>().apply {
            add("exec")
            addAll(spec.options)
            add(id!!)
            addAll(DockerProcess.commandToArgs(spec.command))
        })

        logger.info("Executing command '${customSpec.fullCommand}' for Docker container '$name'")

        return DockerProcess.execSpec(customSpec)
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
