package com.cognifide.gradle.environment.docker

import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.StringUtils

/**
 * Specialized DSL for running command 'docker run' with daemon-like capability.
 */
class DockerDaemonSpec(docker: Docker) : DockerRunSpec(docker) {

    val initTime = environment.obj.long { convention(3_000L) }

    val stopPrevious = environment.obj.boolean { convention(true) }

    val id = environment.obj.string()

    val idUnique = environment.obj.boolean { convention(false) }

    val outputFile = environment.obj.file { fileProvider(id.map { environment.project.buildDir.resolve("environment/docker/$it.log") }) }

    init {
        nullOut()
        name.convention(id.map { "${docker.stack.internalName.get()}_$it" })
        id.convention(image.map { imageName ->
            val outputId = StringUtils.replaceEach(imageName, arrayOf("/", ":"), arrayOf(".", "."))
            when {
                idUnique.get() -> "$outputId.${idUnique()}"
                else -> outputId
            }
        })
    }

    @Suppress("MagicNumber")
    private fun idUnique() = RandomStringUtils.random(8, true, true)
}
