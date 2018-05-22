Allows to serve files from amazon S3.

```kotlin
object RespondAmazonS3FileSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(Netty, port = 8080) {
            val s3 = runBlocking {
                S3(
                    "http://127.0.0.1:4567/{bucket}/{key}",
                    accessKey = "demo", secretKey = "demo"
                )
            }
            val bucket = s3.bucket("mybucket")
            install(PartialContent)
            routing {
                get("/{name}") {
                    val name = call.parameters["name"] ?: error("name not specified")
                    call.respondAmazonS3File(bucket, name)
                }
            }
        }.start(wait = true)
    }
}
```
