package com.cognifide.gradle.environment.docker.container

import com.cognifide.gradle.environment.docker.DockerException

class ContainerException : DockerException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
