package com.cognifide.gradle.environment.docker.runtime

import com.cognifide.gradle.environment.EnvironmentExtension
import com.cognifide.gradle.environment.docker.DockerException
import org.buildobjects.process.ProcBuilder

class Toolbox(environment: EnvironmentExtension) : Base(environment) {

    override val name: String get() = NAME

    override val hostIp: String get() = environment.prop.string("docker.toolbox.hostIp")
        ?: detectHostIp() ?: "192.168.99.100"

    override val hostInternalIp: String get() = environment.prop.string("docker.toolbox.hostInternalIp")
        ?: detectHostInternalIp() ?: "10.0.2.2"

    @Suppress("TooGenericExceptionCaught")
    fun detectHostIp(): String? = try {
        ProcBuilder("docker-machine").withArg("ip").run()
            .outputString.trim().takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        logger.debug("Cannot detect Docker host IP (error while executing 'docker-machine ip'). Cause: ${e.message}")
        null
    }

    override val safeVolumes: Boolean get() = environment.prop.boolean("docker.toolbox.safeVolumes") ?: true

    var cygpathPath = environment.prop.string("docker.toolbox.cygpath.path")
        ?: "C:\\Program Files\\Git\\usr\\bin\\cygpath.exe"

    override fun determinePath(path: String): String {
        return try {
            executeCygpath(path)
        } catch (e: DockerException) {
            logger.debug("Cannot determine Docker path for '$path' using 'cygpath', because it is not available.", e)
            imitateCygpath(path)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun executeCygpath(path: String) = try {
        ProcBuilder(cygpathPath).withArg(path).run()
            .outputString.trim()
    } catch (e: Exception) {
        throw DockerException("Cannot execute '$cygpathPath' for path: $path", e)
    }

    fun imitateCygpath(path: String): String {
        return Regex("(\\w):/(.*)").matchEntire(path.replace("\\", "/"))?.let {
            val (letter, drivePath) = it.groupValues.drop(1)
            "/${letter.lowercase()}/$drivePath"
        } ?: path
    }

    companion object {
        const val NAME = "toolbox"
    }
}
