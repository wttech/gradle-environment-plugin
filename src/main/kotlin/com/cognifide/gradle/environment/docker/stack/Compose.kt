package com.cognifide.gradle.environment.docker.stack

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.DockerException
import com.cognifide.gradle.environment.docker.DockerProcess
import com.cognifide.gradle.environment.docker.Stack
import com.cognifide.gradle.environment.docker.StackException

@Suppress("SpreadOperator")
class Compose(environment: EnvironmentExtension) : Stack(environment) {

    val initTimeout = common.obj.long {
        convention(30_000L)
        common.prop.long("docker.compose.initTimeout")?.let { set(it) }
    }

    override val initialized: Boolean by lazy {
        var error: Exception? = null

        common.progressIndicator {
            message = "Initializing stack - Docker Compose"

            try {
                initCompose()
            } catch (e: DockerException) {
                error = e
            }
        }

        error?.let { e ->
            throw StackException("Docker Compose stack cannot be initialized. Is Docker running / installed? Error '${e.message}'", e)
        }

        true
    }

    private fun initCompose() {
        val result = DockerProcess.execQuietly {
            withTimeoutMillis(initTimeout.get())
            withArgs("compose", "version")
        }
        if (result.exitValue != 0) {
            throw StackException("Failed to initialize Docker Compose. Is Docker running / installed? Error: '${result.errorString}'")
        }
    }

    override fun init() {
        initialized
    }

    var deployRetry = common.retry { afterSecond(common.prop.long("docker.compose.deployRetry") ?: 30) }

    override fun deploy() {
        init()

        common.progressIndicator {
            message = "Starting stack '${internalName.get()}'"

            try {
                DockerProcess.exec {
                    withArgs("compose", "-p", internalName.get(), "-f", composeFilePath, "up", "-d")
                }
            } catch (e: DockerException) {
                throw StackException("Failed to deploy Docker Compose stack '${internalName.get()}'!", e)
            }

            message = "Awaiting started stack '${internalName.get()}'"
            Behaviors.waitUntil(deployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == deployRetry.times && !running) {
                    throw StackException("Failed to start Docker Compose stack named '${internalName.get()}'!")
                }

                !running
            }
        }
    }

    var undeployRetry = common.retry { afterSecond(common.prop.long("docker.compose.undeployRetry") ?: 30) }

    override fun undeploy() {
        init()

        common.progressIndicator {
            message = "Stopping stack '${internalName.get()}'"

            val args = arrayOf("compose", "-p", internalName.get(), "-f", composeFilePath, "down")
            try {
                DockerProcess.exec { withArgs(*args) }
            } catch (e: DockerException) {
                throw StackException("Failed to remove Docker Compose stack '${internalName.get()}'!", e)
            }

            message = "Awaiting stopped stack '${internalName.get()}'"
            Behaviors.waitUntil(undeployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == undeployRetry.times && running) {
                    throw StackException("Failed to stop stack named '${internalName.get()}'!" +
                            " Try to stop manually using Docker command: 'docker ${args.joinToString(" ")}}'")
                }

                running
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun troubleshoot(): List<String> = mutableListOf<String>().apply {
        add("Consider troubleshooting:")

        val psArgs = arrayOf("compose", "-p", internalName.get(), "ps", "--no-trunc")
        try {
            val out = try {
                DockerProcess.execString { withArgs(*psArgs) }
            } catch (e: Exception) {
                throw StackException("Cannot list processes in Docker Compose stack named '${internalName.get()}'!", e)
            }
            add("* restarting Docker")
            add("* using output of command: 'docker ${psArgs.joinToString(" ")}':\n")
            add(out)
        } catch (e: Exception) {
            add("* using command: 'docker ${psArgs.joinToString(" ")}'")
            add("* restarting Docker")
        }
    }

    companion object {
        const val NAME = "compose"
    }
}
