package com.cognifide.gradle.environment.docker

import org.gradle.process.internal.streams.SafeStreams
import java.io.InputStream
import java.io.OutputStream

open class DockerSpec(protected val docker: Docker) {

    protected val environment = docker.environment

    protected val runtime = docker.runtime

    val commandDir = environment.obj.dir { convention(environment.project.layout.projectDirectory) }

    val commandLine = environment.obj.list<String>()

    var exitCodes = environment.obj.list<Int> { convention(listOf(0)) }

    fun exitCode(exitCode: Int?) {
        if (exitCode != null) {
            exitCodes.set(listOf(exitCode))
        } else {
            ignoreExitCodes()
        }
    }

    fun ignoreExitCodes() {
        exitCodes.set(listOf())
    }

    var input = environment.obj.typed<InputStream>()

    val output = environment.obj.typed<OutputStream>()

    val errors = environment.obj.typed<OutputStream>()

    fun systemOut() {
        input.set(SafeStreams.emptyInput())
        output.set(SafeStreams.systemOut())
        errors.set(SafeStreams.systemErr())
    }

    fun nullOut() {
        input.set(SafeStreams.emptyInput())
        output.set(NULL_OUTPUT_STREAM)
        errors.set(NULL_OUTPUT_STREAM)
    }

    companion object {
        val NULL_OUTPUT_STREAM = object : java.io.OutputStream() {
            override fun write(b: Int) {
                // intentionally empty
            }
        }
    }
}
