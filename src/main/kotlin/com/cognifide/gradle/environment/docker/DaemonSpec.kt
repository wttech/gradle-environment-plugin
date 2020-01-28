package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.environment.EnvironmentExtension
import org.gradle.process.internal.streams.SafeStreams

class DaemonSpec(environment: EnvironmentExtension) : RunSpec(environment) {

    var initTime = 3_000L

    var stopPrevious = true

    var unique = false

    var id: String? = null

    val outputFile get() = environment.project.file("build/environment/docker/$id.log")

    init {
        input = SafeStreams.emptyInput()
        output = null
        errors = null

        cleanup = true
    }
}
