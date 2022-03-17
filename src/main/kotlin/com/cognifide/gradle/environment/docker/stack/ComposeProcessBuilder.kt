package com.cognifide.gradle.environment.docker.stack

import com.cognifide.gradle.environment.docker.DockerProcess
import com.cognifide.gradle.environment.docker.StackException

enum class ComposeProcessBuilder(val commandLine: List<String>, val checkArgs: List<String>) {
    V2(listOf("docker", "compose"), listOf("version")),
    V1(listOf("docker-compose"), listOf("--version"));

    fun build() = DockerProcess(commandLine)

    companion object {

        @Suppress("TooGenericExceptionCaught")
        fun detect() = values().firstOrNull {
            try {
                DockerProcess(it.commandLine + it.checkArgs).execQuietly().exitValue == 0
            } catch (e: Exception) {
                false
            }
        } ?: throw StackException("Failed to detect Docker Compose. Is Docker running / installed?")

        fun of(name: String) = values().firstOrNull {
            it.name.equals(name, true)
        } ?: throw StackException(
            listOf(
                "Unsupported Docker Compose process builder type named '$name'",
                "Supported values: ${values().joinToString(", ") { it.name }}"
            ).joinToString("\n")
        )
    }
}
