package com.cognifide.gradle.environment.docker

import java.io.File

/**
 * Base for both 'docker run' and 'docker exec' commands.
 */
open class DockerInvokeSpec(docker: Docker) : DockerSpec(docker) {

    val options = environment.obj.list<String> { set(listOf()) }

    fun options(vararg options: String) = options(options.asIterable())

    fun options(options: Iterable<String>) {
        this.options.addAll(options)
    }

    val args = environment.obj.list<String> { set(listOf()) }

    fun args(vararg args: Any) = args(args.asIterable())

    fun args(args: Iterable<Any>) {
        this.args.addAll(args.map { it.toString() })
    }

    fun argsShell(command: String) {
        args.set(listOf("sh", "-c", command))
    }

    fun workDir(path: String) {
        options.addAll(listOf("--workdir", path))
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

    fun hostNetwork() {
        options.addAll("--network", "host")
    }

    val command = args.map { it.joinToString(" ") }

    val operation = environment.obj.string {
        convention(command.map { "Command: ${it.ifBlank { "<image_default>" }}" })
    }

    val indicator = environment.obj.boolean { convention(true) }
}
