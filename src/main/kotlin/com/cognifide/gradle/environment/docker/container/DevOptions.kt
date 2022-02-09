package com.cognifide.gradle.environment.docker.container

import com.cognifide.gradle.environment.docker.Container

class DevOptions(val container: Container) {

    val watchDirs = container.common.obj.files()

    fun watchDir(vararg paths: Any) {
        watchDirs.from(paths)
    }

    fun watchSourceDir() {
        watchDir(container.host.sourceDir)
    }

    fun watchSourceDir(vararg paths: String) = paths.forEach {
        watchDir(container.host.sourceDir.dir(it))
    }

    fun watchRootDir(vararg paths: String) = paths.forEach {
        watchDir(container.environment.project.rootProject.layout.projectDirectory.dir(it))
    }
}
