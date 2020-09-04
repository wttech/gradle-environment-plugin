package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.DockerProcess
import com.cognifide.gradle.environment.docker.Runtime
import org.gradle.internal.os.OperatingSystem

abstract class Base(protected val environment: EnvironmentExtension) : Runtime {

    protected val logger = environment.project.logger

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

    override val hostInternalIpMissing: Boolean get() {
        val os = OperatingSystem.current()
        return this is Toolbox || !(os.isWindows || os.isMacOsX)
    }

    override fun toString(): String = name.toLowerCase()
}
