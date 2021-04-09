package com.cognifide.gradle.environment.docker

import java.io.File

/**
 * DSL for running command 'docker exec' (container).
 */
class DockerExecSpec(docker: Docker) : DockerInvokeSpec(docker) {

    val id = environment.obj.string()

    fun workDir(path: String) {
        options.addAll(listOf("--workdir", path))
    }

    fun user(id: String) {
        options.addAll(listOf("--user", id))
    }

    fun env(vars: Map<String, Any?>) {
        vars.forEach { (varName, varValue) -> env(varName, varValue) }
    }

    fun env(varName: String, varValue: Any?) {
        options.addAll(listOf("--env", "$varName=$varValue"))
    }

    fun envFile(file: File) = envFile(file.absolutePath)

    fun envFile(path: String) {
        options.addAll(listOf("--env-file", path))
    }

    fun privileged() {
        options.add("--privileged")
    }

    init {
        systemOut()

        commandLine.set(environment.obj.provider {
            mutableListOf<String>().apply {
                add("exec")
                addAll(options.get())
                add(id.orNull ?: throw DockerException("Docker container ID is not specified!"))
                addAll(args.get())
            }
        })
    }
}
