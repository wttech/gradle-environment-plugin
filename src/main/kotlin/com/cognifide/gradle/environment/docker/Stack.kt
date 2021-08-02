package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.stack.Compose
import com.cognifide.gradle.environment.docker.stack.Swarm

/**
 * Represents project specific Docker stack and provides API for manipulating it.
 */
abstract class Stack(val environment: EnvironmentExtension) {

    protected val common = environment.common

    val internalName = common.obj.string {
        convention(common.project.rootProject.name)
        common.prop.string("docker.stack.name")?.let { set(it) }
    }

    val networkSuffix = common.obj.string {
        convention("default")
        common.prop.string("docker.stack.networkSuffix")?.let { set(it) }
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
            val result = DockerProcess().execQuietly {
                withTimeoutMillis(networkTimeout.get())
                withArgs("network", "inspect", networkName.get())
            }
            return when {
                result.exitValue == 0 -> true
                result.errorString.contains("Error: No such network") -> false
                else -> throw StackException("Unable to determine Docker stack '${internalName.get()}' status. Error: '${result.errorString}'")
            }
        }

    val running: Boolean get() = initialized && networkAvailable

    fun reset() {
        undeploy()
        deploy()
    }

    abstract val initialized: Boolean

    abstract fun init()

    abstract fun deploy()

    abstract fun undeploy()

    abstract fun troubleshoot(): List<String>

    protected val composeFilePath get() = environment.docker.composeFile.get().asFile.path

    companion object {

        fun determine(env: EnvironmentExtension) = env.prop.string("docker.stack")
            ?.let { of(env, it) } ?: Compose(env)

        fun of(env: EnvironmentExtension, name: String): Stack = when (name.toLowerCase()) {
            Compose.NAME -> Compose(env)
            Swarm.NAME -> Swarm(env)
            else -> throw DockerException("Unsupported Docker stack '$name'")
        }
    }
}
