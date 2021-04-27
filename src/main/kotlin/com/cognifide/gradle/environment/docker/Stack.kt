package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.common.build.Behaviors
import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.EnvironmentException
import com.cognifide.gradle.environment.docker.runtime.Toolbox

/**
 * Represents project specific Docker stack and provides API for manipulating it.
 */
class Stack(val environment: EnvironmentExtension) {

    private val common = environment.common

    val internalName = common.obj.string {
        convention(common.project.rootProject.name)
        common.prop.string("docker.stack.name")?.let { set(it) }
    }

    val networkSuffix = common.obj.string {
        convention("docker-net")
        common.prop.string("docker.networkSuffix")?.let { set(it) }
    }

    val networkName = common.obj.string {
        convention(internalName.map { "${it}_${networkSuffix.get()}" })
    }

    val networkTimeout = common.obj.long {
        convention(30_000L)
        common.prop.long("docker.stack.networkTimeout")?.let { set(it) }
    }

    val networkAvailable: Boolean
        get() {
            val result = DockerProcess.execQuietly {
                withTimeoutMillis(networkTimeout.get())
                withArgs("network", "inspect", networkName.get())
            }
            return when {
                result.exitValue == 0 -> true
                result.errorString.contains("Error: No such network") -> false
                else -> throw StackException("Unable to determine Docker stack '${internalName.get()}' status. Error: '${result.errorString}'")
            }
        }

    val initTimeout = common.obj.long {
        convention(30_000L)
        common.prop.long("docker.stack.initTimeout")?.let { set(it) }
    }

    val initialized: Boolean by lazy {
        var error: Exception? = null

        common.progressIndicator {
            message = "Initializing stack"

            try {
                initSwarm()
            } catch (e: DockerException) {
                error = e
            }
        }

        error?.let { e ->
            throw EnvironmentException("Stack cannot be initialized. Is Docker running / installed? Error '${e.message}'", e)
        }

        true
    }

    fun init() = initialized

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

    var deployRetry = common.retry { afterSecond(this@Stack.common.prop.long("docker.stack.deployRetry") ?: 30) }

    fun deploy() {
        init()

        common.progressIndicator {
            message = "Starting stack '${internalName.get()}'"

            try {
                val composeFilePath = environment.docker.composeFile.get().asFile.path
                DockerProcess.exec {
                    withArgs("stack", "deploy", "-c", composeFilePath, internalName.get(), "--with-registry-auth", "--resolve-image=always")
                }
            } catch (e: DockerException) {
                throw StackException("Failed to deploy Docker stack '${internalName.get()}'!", e)
            }

            message = "Awaiting started stack '${internalName.get()}'"
            Behaviors.waitUntil(deployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == deployRetry.times && !running) {
                    throw EnvironmentException("Failed to start stack named '${internalName.get()}'!")
                }

                !running
            }
        }
    }

    var undeployRetry = common.retry { afterSecond(this@Stack.common.prop.long("docker.stack.undeployRetry") ?: 30) }

    fun undeploy() {
        init()

        common.progressIndicator {
            message = "Stopping stack '${internalName.get()}'"

            try {
                DockerProcess.exec { withArgs("stack", "rm", internalName.get()) }
            } catch (e: DockerException) {
                throw StackException("Failed to remove Docker stack '${internalName.get()}'!", e)
            }

            message = "Awaiting stopped stack '${internalName.get()}'"
            Behaviors.waitUntil(undeployRetry.delay) { timer ->
                val running = networkAvailable
                if (timer.ticks == undeployRetry.times && running) {
                    throw EnvironmentException("Failed to stop stack named '${internalName.get()}'!" +
                            " Try to stop manually using Docker command: 'docker stack rm ${internalName.get()}'")
                }

                running
            }
        }
    }

    val running: Boolean get() = initialized && networkAvailable

    fun reset() {
        undeploy()
        deploy()
    }
}
