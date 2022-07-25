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

    val workDir = common.obj.relativeDir(docker.environment.workDir, container.name)

    fun workFile(path: String) = workDir.get().asFile.resolve(path)

    val sourceDir = common.obj.relativeDir(docker.environment.sourceDir, container.name)

    fun sourceFile(path: String) = sourceDir.get().asFile.resolve(path)

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

    fun ensureFilePath(vararg paths: String) = paths.forEach { path ->
        path.takeIf { it.contains("/") }
            ?.substringBeforeLast("/")
            ?.takeIf { it.isNotBlank() }
            ?.let { ensureDirPath(it) }
        ensureFile(workFile(path))
    }

    fun ensureFile(vararg files: File) = files.forEach { file ->
        if (!file.exists()) {
            logger.info("Ensuring file '$file' for container '${container.name}'")
            file.writeText("")
        }
    }

    fun ensureDirPath(vararg paths: String) {
        paths.forEach { path -> ensureDir(workFile(path)) }
    }

    fun ensureDir() = ensureDir(workDir.get().asFile)

    fun ensureDir(vararg dirs: File) = dirs.forEach { file ->
        logger.info("Ensuring directory '$file' for container '${container.name}'")
        file.mkdirs()
    }

    fun cleanDirPath(vararg paths: String) = paths.forEach { path -> cleanDir(workFile(path)) }

    fun cleanDir(vararg dirs: File) = dirs.forEach { dir ->
        logger.info("Cleaning directory '$dir' for container '${container.name}'")
        dir.deleteRecursively()
        dir.mkdirs()
    }
}
