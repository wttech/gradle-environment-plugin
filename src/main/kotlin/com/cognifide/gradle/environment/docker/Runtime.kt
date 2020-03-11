package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.runtime.Desktop
import com.cognifide.gradle.environment.docker.runtime.Toolbox
import java.io.File

interface Runtime {

    val name: String

    val hostIp: String

    val safeVolumes: Boolean

    val hostInternalIp: String

    val hostInternalIpMissing: Boolean

    fun determinePath(path: String): String

    fun determinePath(file: File) = determinePath(file.toString())

    companion object {

        fun determine(env: EnvironmentExtension): Runtime {
            return env.prop.string("environment.docker.runtime")?.let { of(env, it) } ?: detect(env) ?: Desktop(env)
        }

        fun of(env: EnvironmentExtension, name: String): Runtime? = when (name.toLowerCase()) {
            Toolbox.NAME -> Toolbox(env)
            Desktop.NAME -> Desktop(env)
            else -> throw DockerException("Unsupported Docker runtime '$name'")
        }

        fun detect(env: EnvironmentExtension): Runtime? {
            if (!System.getenv("DOCKER_TOOLBOX_INSTALL_PATH").isNullOrBlank()) {
                return Toolbox(env)
            }

            return null
        }
    }
}
