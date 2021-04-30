package com.cognifide.gradle.environment.docker

import org.buildobjects.process.ExternalProcessFailureException
import org.buildobjects.process.ProcBuilder
import org.buildobjects.process.ProcResult
import org.buildobjects.process.TimeoutException
import org.gradle.process.internal.streams.SafeStreams

@Suppress("TooGenericExceptionCaught")
object DockerProcess {

    const val COMMAND = "docker"

    fun exec(options: ProcBuilder.() -> Unit): ProcResult {
        return try {
            ProcBuilder(COMMAND)
                    .withNoTimeout()
                    .withOutputStream(SafeStreams.systemOut())
                    .withErrorStream(SafeStreams.systemErr())
                    .apply(options)
                    .run()
        } catch (e: Exception) {
            throw composeException(e)
        }
    }

    fun execQuietly(options: ProcBuilder.() -> Unit): ProcResult {
        return try {
            ProcBuilder(COMMAND)
                    .ignoreExitStatus()
                    .apply(options)
                    .run()
        } catch (e: Exception) {
            throw composeException(e)
        }
    }

    fun execString(options: ProcBuilder.() -> Unit): String {
        return try {
            ProcBuilder(COMMAND)
                    .withNoTimeout()
                    .apply(options)
                    .run()
                    .outputString.trim()
        } catch (e: Exception) {
            throw composeException(e)
        }
    }

    @Suppress("SpreadOperator")
    fun execSpec(spec: DockerSpec): DockerResult {
        return DockerResult(exec {
            withWorkingDirectory(spec.commandDir.get().asFile)
            withArgs(*spec.commandLine.get().map { it.toString() }.toTypedArray())
            withExpectedExitStatuses(spec.exitCodes.get().toSet())
            spec.input.orNull.let { withInputStream(it) }
            spec.output.orNull?.let { withOutputStream(it) }
            spec.errors.orNull?.let { withErrorStream(it) }
        })
    }

    private fun composeException(e: Exception): DockerException {
        return when (e) {
            is ExternalProcessFailureException -> DockerException("Docker command process failure!" +
                    " Command: '${e.command}', error: '${e.stderr}', exit code: '${e.exitValue}'", e)
            is TimeoutException -> DockerException("Docker command timeout! Error: '${e.message}'", e)
            is DockerException -> e
            else -> DockerException("Docker unknown error: '${e.message}'", e)
        }
    }
}
