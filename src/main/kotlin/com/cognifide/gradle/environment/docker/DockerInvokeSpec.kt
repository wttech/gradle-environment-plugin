package com.cognifide.gradle.environment.docker

/**
 * Base for both 'docker run' and 'docker exec' commands.
 */
open class DockerInvokeSpec(docker: Docker) : DockerSpec(docker) {

    val options = environment.obj.list<String> { set(listOf()) }

    fun options(vararg options: String) = options(options.asIterable())

    fun options(options: Iterable<String>) {
        this.options.set(options)
    }

    val args = environment.obj.list<String> { set(listOf()) }

    fun args(vararg args: Any) = args(args.asIterable())

    fun args(args: Iterable<Any>) {
        this.args.set(args.map { it.toString() })
    }

    fun argsShell(command: String) {
        args.set(listOf("sh", "-c", command))
    }

    val command = args.map { it.joinToString(" ") }

    val operation = environment.obj.string {
        convention(command.map { "Command: $it" })
    }

    val indicator = environment.obj.boolean { convention(true) }
}
