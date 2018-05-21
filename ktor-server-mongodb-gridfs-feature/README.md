Allows to serve files from MongoDB's GridFS. It is compatible with the PartialContent feature.

MongoDB provides a CLI to handle the GridFS:

```bash
mongofiles put README.md
```

You can create a small server to serve GridFS files like this:

```kotlin
fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) {
        val mongo = MongoDB()
        val db = mongo["test"]
        val grid = db.gridFs()
        install(PartialContent)
        routing {
            get("/{name}") {
                val name = call.parameters["name"] ?: error("name not specified")
                call.respondMongoDBGridFSFile(grid, name)
            }
        }
    }.start(wait = true)
}
```