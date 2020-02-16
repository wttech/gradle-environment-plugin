package com.cognifide.gradle.environment.docker.container

import com.cognifide.gradle.common.file.resolver.FileResolver
import com.cognifide.gradle.environment.docker.Container
import java.io.File

/**
 * File manager for host OS files related specific Docker container.
 * Provides DSL for e.g creating directories for volumes and providing extra files shared via volumes.
 */
class HostFileManager(val container: Container) {

    private val aem = container.common

    private val logger = aem.logger

    private val docker = container.docker

    val rootDir = aem.obj.relativeDir(docker.environment.rootDir, container.name)

    val fileDir = aem.obj.dir {
        convention(rootDir.dir("files"))
        aem.prop.file("environment.container.host.fileDir")?.let { set(it) }
    }

    fun file(path: String) = rootDir.get().asFile.resolve(path)

    val configDir = aem.obj.relativeDir(docker.environment.sourceDir, container.name)

    fun configFile(path: String) = configDir.get().asFile.resolve(path)

    fun resolveFiles(options: FileResolver.() -> Unit): List<File> {
        logger.info("Resolving files for container '${container.name}'")
        val files = aem.resolveFiles(options)
        logger.info("Resolved files for container '${container.name}':\n${files.joinToString("\n")}")

        return files
    }

    fun ensureDir() {
        rootDir.get().asFile.apply {
            logger.info("Ensuring root directory '$this' for container '${container.name}'")
            mkdirs()
        }
    }

    fun ensureDir(vararg paths: String) = paths.forEach { path ->
        file(path).apply {
            logger.info("Ensuring directory '$this' for container '${container.name}'")
            mkdirs()
        }
    }

    fun cleanDir(vararg paths: String) = paths.forEach { path ->
        file(path).apply {
            logger.info("Cleaning directory '$this' for container '${container.name}'")
            deleteRecursively(); mkdirs()
        }
    }
}
