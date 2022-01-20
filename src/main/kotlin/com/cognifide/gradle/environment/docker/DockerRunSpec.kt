package com.cognifide.gradle.environment.docker

import java.io.File

/**
 * DSL for running command 'docker run'.
 */
open class DockerRunSpec(docker: Docker) : DockerInvokeSpec(docker) {

    val name = environment.obj.string()

    val image = environment.obj.string()

    val volumes = environment.obj.map<String, String> { set(mapOf()) }

    val ports = environment.obj.map<String, String> { set(mapOf()) }

    fun port(hostPort: Int, containerPort: Int) = port(hostPort.toString(), containerPort.toString())

    fun port(hostPort: String, containerPort: String) {
        ports.put(hostPort, containerPort)
    }

    fun port(port: Int) = port(port, port)

    fun volume(containerPath: String) = volume(environment.project.projectDir.absolutePath, containerPath)

    fun volume(localFile: File, containerPath: String) = volume(localFile.absolutePath, containerPath)

    fun volume(localPath: String, containerPath: String) {
        volumes.put(localPath, containerPath)
    }

    fun workDirVolume(containerPath: String) {
        workDir(containerPath)
        volume(containerPath)
    }

    val autoRemove = environment.obj.boolean { convention(true) }

    val detached = environment.obj.boolean { convention(false) }

    private val imageOrFail get() = image.orNull ?: throw DockerException("Docker run image is not specified!")

    init {
        systemOut()
        commandLine.set(environment.obj.provider {
            mutableListOf<String>().apply {
                add("run")
                name.orNull?.let { addAll(listOf("--name", it)) }
                if (autoRemove.get() && !options.get().contains("--rm")) add("--rm")
                if (detached.get() && !options.get().contains("-d")) add("-d")
                addAll(options.get())
                addAll(volumes.get().map { (localPath, containerPath) -> "-v=${runtime.determinePath(localPath)}:$containerPath" })
                addAll(ports.get().map { (hostPort, containerPort) -> "-p=$hostPort:$containerPort" })
                add(imageOrFail)
                addAll(args.get())
            }
        })
        operation.set(environment.obj.provider {
            when {
                command.orNull.isNullOrBlank() -> "Running image '$imageOrFail'"
                else -> "Running image '$imageOrFail' with command '${command.get()}'"
            }
        })
    }
}
