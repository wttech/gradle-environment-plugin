package com.cognifide.gradle.environment.docker.container

import com.cognifide.gradle.common.file.resolver.FileResolver
import com.cognifide.gradle.environment.docker.Container
import java.io.File

/**
 * File manager for host OS files related specific Docker container.
 * Provides DSL for e.g creating directories for volumes and providing extra files shared via volumes.
 */
class HostFileManager(val container: Container) {

    private val common = container.common

    private val logger = common.logger

    private val docker = container.docker

    val rootDir = common.obj.relativeDir(docker.environment.rootDir, container.name)

    fun file(path: String) = rootDir.get().asFile.resolve(path)

    val configDir = common.obj.relativeDir(docker.environment.sourceDir, container.name)

    fun configFile(path: String) = configDir.get().asFile.resolve(path)

    private var fileResolverOptions: FileResolver.() -> Unit = {
        downloadDir.convention(docker.environment.buildDir.dir("host"))
    }

    fun fileResolver(options: FileResolver.() -> Unit) {
        this.fileResolverOptions = options
    }

    fun resolveFiles(options: FileResolver.() -> Unit): List<File> {
        logger.info("Resolving files for container '${container.name}'")
        val files = common.resolveFiles { fileResolverOptions(); options() }
        logger.info("Resolved files for container '${container.name}':\n${files.joinToString("\n")}")

        return files
    }

    fun ensureFile(vararg paths: String, content: String = "") = paths.forEach { path ->
        path.takeIf { it.contains("/") }
                ?.substringBeforeLast("/")
                ?.takeIf { it.isNotBlank() }
                ?.let { ensureDir(it) }

        file(path).apply {
            if (!exists()) {
                logger.info("Ensuring file '$this' for container '${container.name}'")
                writeText(content)
            }
        }
    }

    fun ensureDir() {
        rootDir.get().asFile.apply {
            logger.info("Ensuring root directory '$this' for container '${container.name}'")
            mkdirs()
        }
    }

    fun ensureDir(vararg paths: String) {
        paths.forEach { path ->
            file(path).apply {
                logger.info("Ensuring directory '$this' for container '${container.name}'")
                mkdirs()
            }
        }
    }

    fun cleanDir(vararg paths: String) = paths.forEach { path ->
        file(path).apply {
            logger.info("Cleaning directory '$this' for container '${container.name}'")
            deleteRecursively(); mkdirs()
        }
    }
}
