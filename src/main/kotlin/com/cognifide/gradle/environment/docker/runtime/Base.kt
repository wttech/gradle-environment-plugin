package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.DockerProcess
import com.cognifide.gradle.environment.docker.Runtime

abstract class Base(protected val environment: EnvironmentExtension) : Runtime {

    protected val logger = environment.project.logger

    override fun toString(): String = name.toLowerCase()

    @Suppress("SpreadOperator", "TooGenericExceptionCaught")
    protected fun detectHostInternalIp(): String? = try {
        DockerProcess.execString {
            val args = listOf("run", "alpine", "/bin/ash", "-c", "ip -4 route list match 0/0 | cut -d ' ' -f 3")
            withArgs(*args.toTypedArray())
        }.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        logger.debug("Cannot detect Docker host internal IP. Cause: ${e.message}", e)
        null
    }
}
