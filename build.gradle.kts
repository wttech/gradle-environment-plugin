import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("com.gradle.plugin-publish") version "0.20.0"
    id("io.gitlab.arturbosch.detekt") version "1.20.0-RC1"
    id("net.researchgate.release") version "2.8.1"
    id("com.github.breadmoirai.github-release") version "2.2.12"
}

defaultTasks("build", "publishToMavenLocal")
description = "Gradle Environment Plugin"
group = "com.cognifide.gradle"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins.withId("java") {
        java {
            withSourcesJar()
            withJavadocJar()
        }
        tasks.withType<JavaCompile>().configureEach{
            sourceCompatibility = JavaVersion.VERSION_1_8.toString()
            targetCompatibility = JavaVersion.VERSION_1_8.toString()
        }
        tasks.withType<Test>().configureEach {
            testLogging.showStandardStreams = true
            useJUnitPlatform()
        }
    }
    plugins.withId("kotlin") {
        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_1_8.toString()
            }
        }
    }
}

dependencies {
    // Build environment
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.20.0-RC1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    implementation("com.cognifide.gradle:common-plugin:1.1.0")

    // External
    implementation("org.buildobjects:jproc:2.8.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")

    // Cross-project
    compileOnly(project(":common"))
}

val functionalTestSourceSet = sourceSets.create("functionalTest")
gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

val functionalTest by tasks.creating(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    dependsOn("detektFunctionalTest")
}

val check by tasks.getting(Task::class) {
    dependsOn(functionalTest)
}

tasks {
    jar {
        dependsOn(":hosts:jar")
        from(provider { zipTree((project(":common").tasks.getByName("jar") as Jar).archiveFile) })
        from(provider { project(":hosts").tasks.getByName("jar") })
    }

    withType<Test>().configureEach {
        testLogging.showStandardStreams = true
        useJUnitPlatform()
    }

    named<Test>("test") {
        dependsOn("detektTest")
    }

    named<Task>("build") {
        dependsOn("sourcesJar", "javadocJar")
    }

    named<Task>("publishToMavenLocal") {
        dependsOn("sourcesJar", "javadocJar")
    }

    named("afterReleaseBuild") {
        dependsOn("publishPlugins")
    }

    named("githubRelease") {
        mustRunAfter("release")
    }

    register("fullRelease") {
        dependsOn("release", "githubRelease")
    }
}

detekt {
    config.from(file("detekt.yml"))
    parallel = true
    autoCorrect = true
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            from(components["java"])
        }
    }
}

gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("environment") {
            id = "com.cognifide.environment"
            implementationClass = "com.cognifide.gradle.environment.EnvironmentPlugin"
            displayName = "Environment Plugin"
            description = "Provides seamless Gradle integration with Docker & Swarm."
        }
        create("common") {
            id = "com.cognifide.environment.common"
            implementationClass = "com.cognifide.gradle.environment.EnvironmentCommonPlugin"
            displayName = "Environment Common Plugin"
        }
    }
}

val pluginTags = listOf("docker", "swarm", "environment", "docker-compose")

pluginBundle {
    website = "https://github.com/wttech/gradle-environment-plugin"
    vcsUrl = "https://github.com/wttech/gradle-environment-plugin.git"
    description = "Gradle Environment Plugin"
    tags = pluginTags
}

githubRelease {
    owner("wttech")
    repo("gradle-environment-plugin")
    token((project.findProperty("github.token") ?: "").toString())
    tagName(project.version.toString())
    releaseName(project.version.toString())
    releaseAssets(tasks["jar"], tasks["sourcesJar"], tasks["javadocJar"])
    draft((project.findProperty("github.draft") ?: "false").toString().toBoolean())
    overwrite((project.findProperty("github.override") ?: "true").toString().toBoolean())

    val prerelease = (project.findProperty("github.prerelease") ?: "true").toString().toBoolean()
    if (prerelease) {
        prerelease(true)
    } else {
        body { """
            |# What's new
            |
            |TBD
            |
            |# Upgrade notes
            |
            |Nothing to do.
            |
            |# Contributions
            |
            |None.
            """.trimMargin()
        }
    }
}