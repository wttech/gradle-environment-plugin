package com.cognifide.gradle.environment.docker.tasks

import com.cognifide.gradle.common.utils.Formats
import com.cognifide.gradle.environment.EnvironmentDefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class DockerPull : EnvironmentDefaultTask() {

    @Input
    val image = common.obj.string()

    @OutputFile
    val markerFile = common.obj.file {
        convention(image.flatMap { project.layout.buildDirectory.file("docker/pull/${it.replace("/", "_")}.txt") })
    }

    @TaskAction
    fun pull() {
        environment.docker.pull(image.get())
        markerFile.get().asFile.writeText(Formats.date())
    }
}
