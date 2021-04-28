package com.cognifide.gradle.environment.docker

import org.buildobjects.process.ProcBuilder
import org.gradle.internal.os.OperatingSystem

@Suppress("TooGenericExceptionCaught")
class DockerRegistry(private val docker: Docker) {

    private val common = docker.environment.common

    val url = common.obj.string {
        common.prop.string("docker.registry.url")?.let { set(it) }
    }

    val user = common.obj.string {
        common.prop.string("docker.registry.user")?.let { set(it) }
    }

    val password = common.obj.string {
        common.prop.string("docker.registry.password")?.let { set(it) }
    }

    val loginAuto = common.obj.boolean {
        convention(common.obj.provider { loggable })
        common.prop.boolean("docker.registry.loginAuto")?.let { set(it) }
    }

    fun loginAuto() {
        if (loginAuto.get()) {
            login()
        }
    }

    val loggable get() = listOf(url.orNull, user.orNull, password.orNull).all { !it.isNullOrBlank() }

    fun login() {
        if (!loggable) {
            throw DockerException("Cannot login to Docker registry as credentials are not specified!")
        }

        val exitCode = try {
            if (OperatingSystem.current().isWindows) {
                ProcBuilder("docker")
                    .withArgs("login", url.orNull, "-u", user.orNull, "-p", password.orNull)
                    .ignoreExitStatus()
                    .run()
            } else {
                val passwordFile = docker.environment.rootDir.file("docker-registry-pwd.txt").get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(password.orNull.orEmpty())
                }
                try {
                    ProcBuilder("sh")
                        .withArgs("-c", "cat ${passwordFile.absolutePath} | docker login ${url.orNull} -u ${user.orNull} --password-stdin")
                        .ignoreExitStatus()
                        .run()
                } finally {
                    passwordFile.delete()
                }
            }.exitValue
        } catch (e: Exception) {
            throw DockerException("Error occurred while logging in to Docker registry '${url.orNull}' as '${user.orNull}'! Cause: ${e.message}", e)
        }

        if (exitCode != 0) {
            throw DockerException("Cannot login to Docker registry '${url.orNull}' as '${user.orNull}'! Exit code: $exitCode")
        }
    }

    fun logout() {
        if (!loggable) {
            throw DockerException("Cannot logout from Docker registry as credentials are not specified!")
        }

        val exitCode = try {
            ProcBuilder("docker")
                .withArgs("logout", url.orNull)
                .ignoreExitStatus()
                .run()
        } catch (e: Exception) {
            throw DockerException("Error occurred while logging out from Docker registry '${url.orNull}'! Cause: ${e.message}", e)
        }.exitValue

        if (exitCode != 0) {
            throw DockerException("Cannot logout from Docker registry '${url.orNull}'! Exit code: $exitCode")
        }
    }
}
