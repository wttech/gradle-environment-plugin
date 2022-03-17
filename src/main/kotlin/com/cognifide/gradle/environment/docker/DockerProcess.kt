package com.cognifide.gradle.environment.docker

import org.buildobjects.process.ExternalProcessFailureException
import org.buildobjects.process.ProcBuilder
import org.buildobjects.process.ProcResult
import org.buildobjects.process.TimeoutException
import org.gradle.process.internal.streams.SafeStreams

@Suppress("TooGenericExceptionCaught", "SpreadOperator")
class DockerProcess(val commandLine: Iterable<String>) {

    constructor() : this(COMMAND)

    constructor(vararg commandLine: String) : this(commandLine.asIterable())

    private fun builder(): ProcBuilder {
        val command = commandLine.first()
        val args = if (commandLine.count() > 1) commandLine.drop(1) else listOf()
        return ProcBuilder(command, *args.toTypedArray())
    }

    fun exec(options: ProcBuilder.() -> Unit = {}): ProcResult = try {
        builder()
            .withNoTimeout()
            .withOutputStream(SafeStreams.systemOut())
            .withErrorStream(SafeStreams.systemErr())
            .apply(options)
            .run()
    } catch (e: Exception) {
        throw composeException(e)
    }

    fun execQuietly(options: ProcBuilder.() -> Unit = {}): ProcResult = try {
        builder()
            .ignoreExitStatus()
            .apply(options)
            .run()
    } catch (e: Exception) {
        throw composeException(e)
    }

    fun execString(options: ProcBuilder.() -> Unit = {}): String = try {
        builder()
            .withNoTimeout()
            .apply(options)
            .run()
            .outputString.trim()
    } catch (e: Exception) {
        throw composeException(e)
    }

    @Suppress("SpreadOperator")
    fun execSpec(spec: DockerSpec) = DockerResult(
        exec {
            withWorkingDirectory(spec.commandDir.get().asFile)
            withArgs(*spec.commandLine.get().map { it.toString() }.toTypedArray())
            withExpectedExitStatuses(spec.exitCodes.get().toSet())
            spec.input.orNull.let { withInputStream(it) }
            spec.output.orNull?.let { withOutputStream(it) }
            spec.errors.orNull?.let { withErrorStream(it) }
        }
    )

    private fun composeException(e: Exception): DockerException = when (e) {
        is ExternalProcessFailureException -> DockerException(
            "Docker command process failure!" +
                " Command: '${e.command}', error: '${e.stderr}', exit code: '${e.exitValue}'",
            e
        )
        is TimeoutException -> DockerException("Docker command timeout! Error: '${e.message}'", e)
        is DockerException -> e
        else -> DockerException("Docker unknown error: '${e.message}'", e)
    }

    companion object {
        const val COMMAND = "docker"
    }
}
