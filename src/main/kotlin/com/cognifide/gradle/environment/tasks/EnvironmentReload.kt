package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentReload : EnvironmentDefaultTask() {

    @TaskAction
    fun reload() {
        environment.reload()

        common.notifier.lifecycle("Environment reloaded", "Reloaded with success")
    }

    init {
        description = "Reloads virtualized environment (e.g reloads HTTPD configuration and cleans cache files)"
    }

    companion object {
        const val NAME = "environmentReload"
    }
}
