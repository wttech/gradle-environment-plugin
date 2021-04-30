package com.cognifide.gradle.environment.docker.tasks

import com.cognifide.gradle.environment.EnvironmentDefaultTask
import com.cognifide.gradle.environment.docker.DockerRunSpec
import org.gradle.api.tasks.TaskAction

open class DockerRun : EnvironmentDefaultTask() {

    private var spec: DockerRunSpec.() -> Unit = {}

    fun spec(options: DockerRunSpec.() -> Unit) {
        this.spec = options
    }

    @TaskAction
    fun run() {
        environment.docker.run(spec)
    }
}
