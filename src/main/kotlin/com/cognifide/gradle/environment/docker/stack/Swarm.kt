package com.cognifide.gradle.environment.docker.stack

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.DockerException
import com.cognifide.gradle.environment.docker.DockerProcess
import com.cognifide.gradle.environment.docker.Stack
import com.cognifide.gradle.environment.docker.StackException
import com.cognifide.gradle.environment.docker.runtime.Toolbox

@Suppress("SpreadOperator")
class Swarm(environment: EnvironmentExtension) : Stack(environment) {

    val initTimeout = common.obj.long {
        convention(30_000L)
        common.prop.long("docker.swarm.initTimeout")?.let { set(it) }
    }

    override val initialized: Boolean by lazy {
        var error: Exception? = null

        common.progressIndicator {
            message = "Initializing stack - Docker Swarm"

            try {
                initSwarm()
            } catch (e: DockerException) {
                error = e
            }
        }

        error?.let { e ->
            throw StackException("Docker Swarm stack cannot be initialized. Is Docker running / installed? Error '${e.message}'", e)
        }

        true
    }

    private fun initSwarm() {
        val result = DockerProcess.execQuietly {
            withTimeoutMillis(initTimeout.get())
            withArgs("swarm", "init")

            if (environment.docker.runtime is Toolbox) {
                withArgs("--advertise-addr", environment.docker.runtime.hostIp)
            }
        }
        if (result.exitValue != 0 && !result.errorString.contains("This node is already part of a swarm")) {
            throw StackException("Failed to initialize Docker Swarm. Is Docker running / installed? Error: '${result.errorString}'")
        }
    }

    override fun init() {
        initialized
    }

    var deployRetry = common.retry { afterSecond(common.prop.long("docker.swarm.deployRetry") ?: 30) }

    override fun deploy() {
        init()

        common.progressIndicator {
            message = "Starting stack '${internalName.get()}'"

            try {
                DockerProcess.exec {
                    withArgs("stack", "deploy", "-c", composeFilePath, internalName.get(), "--with-registry-auth", "--resolve-image=always")
                }
            } catch (e: DockerException) {
                throw StackException("Failed to deploy Docker Swarm stack '${internalName.get()}'!", e)
            }

            message = "Awaiting started stack '${internalName.get()}'"
            Behaviors.waitUntil(deployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == deployRetry.times && !running) {
                    throw StackException("Failed to start Docker Swarm stack named '${internalName.get()}'!")
                }

                !running
            }
        }
    }

    var undeployRetry = common.retry { afterSecond(common.prop.long("docker.swarm.undeployRetry") ?: 30) }

    override fun undeploy() {
        init()

        common.progressIndicator {
            message = "Stopping stack '${internalName.get()}'"

            val args = arrayOf("stack", "rm", internalName.get())
            try {
                DockerProcess.exec { withArgs(*args) }
            } catch (e: DockerException) {
                throw StackException("Failed to remove Docker Swarm stack '${internalName.get()}'!", e)
            }

            message = "Awaiting stopped stack '${internalName.get()}'"
            Behaviors.waitUntil(undeployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == undeployRetry.times && running) {
                    throw StackException("Failed to stop Docker Swarm stack named '${internalName.get()}'!" +
                            " Try to stop manually using Docker command: 'docker ${args.joinToString(" ")}}'")
                }

                running
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun troubleshoot(): List<String> = mutableListOf<String>().apply {
        add("Consider troubleshooting:")

        val psArgs = arrayOf("stack", "ps", internalName.get(), "--no-trunc")
        try {
            val out = try {
                DockerProcess.execString { withArgs(*psArgs) }
            } catch (e: Exception) {
                throw StackException("Cannot list processes in Docker Swarm stack named '${internalName.get()}'!", e)
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
        const val NAME = "swarm"
    }
}
