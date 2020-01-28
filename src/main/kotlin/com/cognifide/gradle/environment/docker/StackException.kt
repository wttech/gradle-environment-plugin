package com.cognifide.gradle.environment.docker

class StackException : DockerException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
