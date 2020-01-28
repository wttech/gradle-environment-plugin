package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask

open class EnvironmentResetup : EnvironmentDefaultTask() {

    init {
        description = "Destroys then sets up virtualized development environment."
    }

    companion object {
        const val NAME = "environmentResetup"
    }
}
