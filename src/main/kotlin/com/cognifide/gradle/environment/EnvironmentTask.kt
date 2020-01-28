package com.cognifide.gradle.environment

import org.gradle.api.Task
import org.gradle.api.tasks.Internal

interface EnvironmentTask : Task {

    @get:Internal
    val environment: EnvironmentExtension

    companion object {
        const val GROUP = "Environment"
    }
}
