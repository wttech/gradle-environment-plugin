package com.cognifide.gradle.environment.docker

/**
 * DSL for running command 'docker exec' (container).
 */
class DockerExecSpec(docker: Docker) : DockerInvokeSpec(docker) {

    val id = environment.obj.string()

    fun user(id: String) {
        options.addAll(listOf("--user", id))
    }

    fun privileged() {
        options.add("--privileged")
    }

    init {
        systemOut()

        commandLine.set(
            environment.obj.provider {
                mutableListOf<String>().apply {
                    add("exec")
                    addAll(options.get())
                    add(id.orNull ?: throw DockerException("Docker container ID is not specified!"))
                    addAll(args.get())
                }
            }
        )
    }
}
