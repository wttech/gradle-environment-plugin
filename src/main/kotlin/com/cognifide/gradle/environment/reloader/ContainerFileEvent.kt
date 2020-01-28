package com.cognifide.gradle.environment.reloader

import com.cognifide.gradle.common.file.watcher.Event
import com.cognifide.gradle.environment.docker.Container

class ContainerFileEvent(val container: Container, val event: Event)
