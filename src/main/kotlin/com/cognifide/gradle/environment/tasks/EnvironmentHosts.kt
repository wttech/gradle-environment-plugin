package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentHosts : EnvironmentDefaultTask() {

    @TaskAction
    fun appendHosts() {
        environment.hosts.update()
    }

    init {
        description = "Updates environment hosts entries."
    }

    companion object {
        const val NAME = "environmentHosts"
    }
}
