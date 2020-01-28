package com.cognifide.gradle.environment.docker

class DockerCustomSpec(val base: DockerDefaultSpec, val argsOverride: List<String>) : DockerSpec by base {

    override val args: List<String>
        get() = argsOverride
}
