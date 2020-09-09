package com.cognifide.gradle.environment.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.TaskAction

open class EnvironmentHosts : EnvironmentDefaultTask() {

    @TaskAction
    fun appendHosts() {
        logger.lifecycle("Hosts entries to be appended to ${environment.hosts.osFile.get()}:")
        logger.quiet(environment.hosts.appendix)
    }

    init {
        description = "Prints environment hosts entries."
    }

    companion object {
        const val NAME = "environmentHosts"
    }
}
