package com.cognifide.gradle.environment.docker

import com.cognifide.gradle.environment.EnvironmentException

open class DockerException : EnvironmentException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
