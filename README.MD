[![Cognifide logo](docs/cognifide-logo.png)](http://cognifide.com)

[![Download](https://api.bintray.com/packages/cognifide/maven-public/gradle-environment-plugin/images/download.svg) ](https://bintray.com/cognifide/maven-public/gradle-environment-plugin/_latestVersion)
[![Build Status](https://dev.azure.com/gradle-aem/gradle-environment-plugin/_apis/build/status/Cognifide.gradle-environment-plugin?branchName=master)](https://dev.azure.com/gradle-aem/gradle-environment-plugin/_build/latest?definitionId=9&branchName=master)[![Gradle Status](https://gradleupdate.appspot.com/Cognifide/gradle-environment-plugin/status.svg?random=123)](https://gradleupdate.appspot.com/Cognifide/gradle-environment-plugin/status)
[![Apache License, Version 2.0, January 2004](docs/apache-license-badge.svg)](http://www.apache.org/licenses/)

# Gradle Environment Plugin

  * [About](#about)
  * [Setup](#setup)
  * [Prerequisites](#prerequisites)
     * [Notice for Docker on Windows](#notice-for-docker-on-windows)
     * [Notice for Docker Toolbox](#notice-for-docker-toolbox)
  * [Configuration](#configuration)
     * [Docker compose file](#docker-compose-file)
     * [DSL options](#dsl-options)
  * [Health checks](#health-checks)
  * [Task environmentUp](#task-environmentup)
  * [Task environmentDown](#task-environmentdown)
  * [Task environmentDev](#task-environmentdev)
  * [Task environmentAwait](#task-environmentawait)
  * [Task environmentReload](#task-environmentreload)
  * [Task environmentHosts](#task-environmenthosts)
  * [License](#license)

## About

Provides seamless Gradle integration with Docker & Swarm. Hides many OS-specific glitches from unexperienced Docker users.

Controls virtualized environment consisting of any Docker-compatible software (e.g Apache Web Server (HTTPD), SQL/noSQL databases).
Such software is deployed as [Docker Containers](https://www.docker.com/resources/what-container) orchestrated by [Swarm](https://docs.docker.com/engine/swarm/). This solution is cross-platform just like Docker.

Provides environment related tasks: `environmentUp`, `environmentDev`, `environmentHosts` etc.

Should be applied only at root project / only once within whole build.
    
## Setup

As of plugin is published at official Gradle Plugin Portal, see instructions from [there](https://plugins.gradle.org/plugin/com.cognifide.environment).

## Prerequisites

Most of the configuration steps are automated. However, there are three manual steps to make this setup fully operating:

1. [Install Docker](https://docs.docker.com/install/)
    * [Mac](https://docs.docker.com/docker-for-mac/)
    * [Ubuntu](https://docs.docker.com/install/linux/docker-ce/ubuntu/)
    * Windows
        * [Desktop](https://docs.docker.com/docker-for-windows/install/) (highly recommended, using Hyper-V)
        * [Toolbox](https://docs.docker.com/toolbox/toolbox_install_windows/) (legacy, using VirtualBox)

2. Setup hosts on local machine (admin rights are required to access `/etc/hosts` or `C:\Windows\System32\drivers\etc\hosts` file)
    * Unix: execute `sudo gradlew environmentHosts --no-daemon`
    * Windows: start PowerShell with "Run as administrator", then execute `./gradlew.bat environmentHosts --no-daemon`
    
### Notice for Docker on Windows

Ensure having shared folders enabled via GUI:

![Docker Desktop Shared Drive](docs/docker-desktop-share.png)

Please note that after each Windows credentials change (e.g forced by company policy), it is required to click button "Reset credentials" to ensure having shared drives working correctly.
When running task `environmentUp` first time, Windows User Account Control (UAC) may require user confirmation to allow virtualized container to access local configuration files stored on shared drives.

### Notice for Docker Toolbox

While using Docker Toolbox, beware that, by default, there is only one shared folder in VirtualBox configured which mounts *C:/Users* to */c/users* on Docker Machine / Ubuntu. 
As a consequence, if project is located outside of *C:/Users* directory, there is a need to manually add corresponding shared folder and mount it on Docker Machine using VirtualBox GUI.

## Configuration

### Docker compose file

To configure containers to be deployed using Docker Swarm, simply create standard Docker Compose file but with templating (file at path *src/environment/docker-compose.yml.peb*)

```yml
version: "3"
services:
  httpd:
    image: centos/httpd:latest
    command: ["tail", "-f", "--retry", "/usr/local/apache2/logs/error.log"]
    deploy:
      replicas: 1
    ports:
      - "80:80"
    networks:
      - docker-net
    volumes:
      - "{{ docker.rootPath }}/env/src/environment/httpd/conf/httpd.conf:/etc/httpd/conf/httpd.conf"
      - "{{ docker.rootPath }}/app/aem/dispatcher/src/conf.d:/etc/httpd/conf.d"
      - "{{ docker.rootPath }}/app/aem/dispatcher/src/conf.dispatcher.d:/etc/httpd/conf.dispatcher.d"
      - "{{ docker.workPath }}/httpd/modules/mod_dispatcher.so:/etc/httpd/modules/mod_dispatcher.so"
      - "{{ docker.workPath }}/httpd/logs:/etc/httpd/logs"
      {% if docker.runtime.safeVolumes %}
      - "{{ docker.workPath }}/httpd/cache:/var/www/localhost/cache"
      - "{{ docker.workPath }}/httpd/htdocs:/var/www/localhost/htdocs"
      {% endif %}
    {% if docker.runtime.hostInternalIpMissing %}
    extra_hosts:
      - "host.docker.internal:{{ docker.runtime.hostInternalIp }}"
    {% endif %}
networks:
  docker-net:
```

Docker has differences in case of how it works on different operating systems. While using templating and [variables](src/main/kotlin/com/cognifide/gradle/environment/docker/Docker.kt#L46) in that file, Gradle Environment Plugin tries to mitigate these differences, to make your build working smoothly on all OSes and hide glitches from unexperienced Docker users. For example, when using Docker Toolbox, paths should be absolute and having Unix separators despite that host OS is Windows.

Example above comes from [that file](https://github.com/Cognifide/gradle-aem-multi/blob/master/env/src/environment/docker-compose.yml.peb) which is setting up Apache HTTPD for [Adobe Experience Manager](https://www.adobe.com/pl/marketing/experience-manager.html).

### DSL options

Moreover, plugin comes with rich DSL which allows to:

* run callbacks on each container lifecycle event (resolve, up, reload, dev),
* configure where container resources are stored,
* configure health checks to be able to detect moment in build lifecycle when are container services are stable and be ready to perform next build tasks using these services,
* configure hosts which should be apended to OS-specific hosts file.

```kotlin
environment { // config for AEM environment running on Docker
    rootDir.set(prop.string("environment.rootDir")) // project.file(".environment")
    hosts { // domains to be appended to hosts file automatically
        "http://example.com" { tag("publish") }
        "http://locahost:4502 { tag("author") }
    }
    docker { // Docker specific configuration
        containers {
            "httpd" { // control container described in 'docker-compose.yml.peb'
                resolve {
                    resolveFiles {
                        download("http://download.macromedia.com/dispatcher/download/dispatcher-apache2.4-linux-x86_64-4.3.2.tar.gz").use {
                            copyArchiveFile(it, "**/dispatcher-apache*.so", file("modules/mod_dispatcher.so"))
                        }
                    }
                    ensureDir("cache", "logs")
                }
                up {
                    ensureDir("/usr/local/apache2/logs", "/opt/aem/dispatcher/cache/content/example/demo", "/opt/aem/dispatcher/cache/content/example/live")
                    execShell("Starting HTTPD server", "/usr/local/apache2/bin/httpd -k start")
                }
                reload {
                    cleanDir("/opt/aem/dispatcher/cache/content/example/demo", "/opt/aem/dispatcher/cache/content/example/live")
                    execShell("Restarting HTTPD server", "/usr/local/apache2/bin/httpd -k restart")
                }
                dev {
                    watchConfigDir("conf")
                }
            }
        }                        
    }
    healthChecks { // checks (e.g GET requests) verifying running Docker containers like HTTPD
          http("http://example.com/en-us.html", "Live site", "English US")
          http("http://author.example.com/sites.html", "Author module 'Sites'") {
              containsText("Sites")
              options { basicCredentials = authorInstance.credentials }
          }
    }
}
```

## Health checks

In case of the dispatcher it takes few seconds to start. Service health checks could be described by following configuration.
For example, to wait for all domains to be available and having particular text in response, write:

```kotlin
environment {
    healthChecks {
        http("http://example.com/en-us.html", "Live site", "English US")
    }
}
```

These checks are performed automatically after each file change applied when running task `environmentDev` or on demand when running task `environmentAwait`.

## Task `environmentUp`

Turns on local environment.

![Environment up task](docs/environment-up-task.gif)

## Task `environmentDown`

Turns off local environment.

![Environment down task](docs/environment-down-task.gif)

## Task `environmentDev`

Allows to listen for Apache Web Server / Dispatcher configuration files changed and then automatically reload HTTP service.

Workflow:

1. Run command `gradlew environmentDev`,
2. Edit files located in *src/environment/httpd/conf* ,
3. Notice that HTTPD service should be restarted automatically after file changes,
4. Check results of [health checks](#health-checks),
4. Optionally, check:
   * HTTPD logs located at path *.environment/httpd/logs*
   
![Environment dev task](docs/environment-dev-task.gif)

## Task `environmentAwait`

Performs [environment service health checks](#environment-service-health-checks) on demand.

## Task `environmentReload`

Reloads virtualized environment by reloading all Docker containers (e.g. removing Dispatcher cache files then restarting HTTPD server).

## Task `environmentHosts`

Appends environment specific hosts entries to OS specific hosts file.

Example configuration:

```kotlin
environment {
    hosts { // domains to be appended to hosts file automatically
        "http://example.com" { tag("live site") }
        "http://locahost:4502" { tag("cms authoring") }
        "http://locahost:8080" { tag("dbms") }
    }
}
```

Result of running task `environmentHosts` will be an list of IP-domain pairs should should be appended to OS-specific file (e.g /etc/hosts on Unix). When using Docker Toolbox on Windows, IP may be different, as of it is using extra VM to run Docker and IP of that VM should be used instead of loopback IP (localhost / 127.0.0.1). Plugin is trying to make this difference transparent for build users.

## License

**Gradle Environment Plugin** is licensed under the [Apache License, Version 2.0 (the "License")](https://www.apache.org/licenses/LICENSE-2.0.txt)
