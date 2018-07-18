[![Build Status](https://travis-ci.org/soywiz/ktor-cio-clients.svg?branch=master)](https://travis-ci.org/soywiz/ktor-cio-clients)

# ktor-cio-clients

Ktor CIO (Coroutine I/O) Clients proposal for JetBrains based on kotlinx.coroutines.

This is a prototype playground.

This is a pet temporal project with Kotlin CIO clients implementing several protocols to connect to popular services.
Package names are likely to change.

The WIP proposal artifacts are being published to:

```kotlin
repositories {
    maven { url "https://dl.bintray.com/soywiz/soywiz" }
}
```

## Redis client

For a Redis client, please use <https://github.com/ktorio/ktor-client-redis>

Right now, some of the repos here require to locally install the `ktor-client-redis`:

```
git clone https://github.com/ktorio/ktor-client-redis
cd ktor-client-redis
./gradlew publishToMavenLocal
```
