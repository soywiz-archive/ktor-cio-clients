object Generate {
    interface PARAM_DESC
    interface PARAM : PARAM_DESC {
        val name: String
    }

    data class STR(override val name: String) : PARAM
    data class INT(override val name: String) : PARAM

    data class OPTIONAL(val params: List<PARAM_DESC>) : PARAM_DESC {
        companion object {
            operator fun invoke(vararg params: PARAM_DESC) = OPTIONAL(params.toList())
        }
    }

    data class PARAMS(val params: List<PARAM_DESC>) {
        companion object {
            operator fun invoke(vararg params: PARAM_DESC) = PARAMS(params.toList())
        }
    }

    @Deprecated("Missing")
    val PARAMS_TODO: PARAMS?
        get() = null

    private fun cmd(
        name: String,
        params: String,
        description: String,
        group: Int,
        since: String,
        parsedParams: PARAMS? = null
    ) {

    }

    @JvmStatic
    fun main(args: Array<String>) {
        generateCommandList()
    }

    @JvmStatic
    fun generateCommandList() {
        // https://redis.io/commands/
        // https://github.com/antirez/redis/blob/32ac4c64baf00747da1acc0cc61ee236922e2dcf/src/help.h#L23

        cmd(
            "APPEND",
            "key value",
            "Append a value to a key",
            1,
            "2.0.0",
            PARAMS(STR("key"), STR("value"))
        )

        cmd(
            "AUTH",
            "password",
            "Authenticate to the server",
            8,
            "1.0.0",
            PARAMS(STR("password"))
        )
        cmd(
            "BGREWRITEAOF",
            "-",
            "Asynchronously rewrite the append-only file",
            9,
            "1.0.0",
            PARAMS()
        )
        cmd(
            "BGSAVE",
            "-",
            "Asynchronously save the dataset to disk",
            9,
            "1.0.0",
            PARAMS()
        )
        cmd(
            "BITCOUNT",
            "key [start end]",
            "Count set bits in a string",
            1,
            "2.6.0",
            PARAMS(STR("key"), OPTIONAL(INT("start"), INT("end")))
        )
        cmd(
            "BITFIELD",
            "key [GET type offset] [SET type offset value] [INCRBY type offset increment] [OVERFLOW WRAP|SAT|FAIL]",
            "Perform arbitrary bitfield integer operations on strings",
            1,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "BITOP",
            "operation destkey key [key ...]",
            "Perform bitwise operations between strings",
            1,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "BITPOS",
            "key bit [start] [end]",
            "Find first bit set or clear in a string",
            1,
            "2.8.7",
            PARAMS_TODO
        )
        cmd(
            "BLPOP",
            "key [key ...] timeout",
            "Remove and get the first element in a list, or block until one is available",
            2,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "BRPOP",
            "key [key ...] timeout",
            "Remove and get the last element in a list, or block until one is available",
            2,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "BRPOPLPUSH",
            "source destination timeout",
            "Pop a value from a list, push it to another list and return it; or block until one is available",
            2,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "CLIENT GETNAME",
            "-",
            "Get the current connection name",
            9,
            "2.6.9",
            PARAMS_TODO
        )
        cmd(
            "CLIENT KILL",
            "[ip:port] [ID client-id] [TYPE normal|master|slave|pubsub] [ADDR ip:port] [SKIPME yes/no]",
            "Kill the connection of a client",
            9,
            "2.4.0",
            PARAMS_TODO
        )
        cmd(
            "CLIENT LIST",
            "-",
            "Get the list of client connections",
            9,
            "2.4.0",
            PARAMS_TODO
        )
        cmd(
            "CLIENT PAUSE",
            "timeout",
            "Stop processing commands from clients for some time",
            9,
            "2.9.50",
            PARAMS_TODO
        )
        cmd(
            "CLIENT REPLY",
            "ON|OFF|SKIP",
            "Instruct the server whether to reply to commands",
            9,
            "3.2",
            PARAMS_TODO
        )
        cmd(
            "CLIENT SETNAME",
            "connection-name",
            "Set the current connection name",
            9,
            "2.6.9",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER ADDSLOTS",
            "slot [slot ...]",
            "Assign new hash slots to receiving node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER COUNT-FAILURE-REPORTS",
            "node-id",
            "Return the number of failure reports active for a given node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER COUNTKEYSINSLOT",
            "slot",
            "Return the number of local keys in the specified hash slot",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER DELSLOTS",
            "slot [slot ...]",
            "Set hash slots as unbound in receiving node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER FAILOVER",
            "[FORCE|TAKEOVER]",
            "Forces a slave to perform a manual failover of its master.",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER FORGET",
            "node-id",
            "Remove a node from the nodes table",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER GETKEYSINSLOT",
            "slot count",
            "Return local key names in the specified hash slot",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER INFO",
            "-",
            "Provides info about Redis Cluster node state",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER KEYSLOT",
            "key",
            "Returns the hash slot of the specified key",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER MEET",
            "ip port",
            "Force a node cluster to handshake with another node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER NODES",
            "-",
            "Get Cluster config for the node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER REPLICATE",
            "node-id",
            "Reconfigure a node as a slave of the specified master node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER RESET",
            "[HARD|SOFT]",
            "Reset a Redis Cluster node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER SAVECONFIG",
            "-",
            "Forces the node to save cluster state on disk",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER SET-CONFIG-EPOCH",
            "config-epoch",
            "Set the configuration epoch in a new node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER SETSLOT",
            "slot IMPORTING|MIGRATING|STABLE|NODE [node-id]",
            "Bind a hash slot to a specific node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER SLAVES",
            "node-id",
            "List slave nodes of the specified master node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "CLUSTER SLOTS",
            "-",
            "Get array of Cluster slot to node mappings",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "COMMAND",
            "-",
            "Get array of Redis command details",
            9,
            "2.8.13",
            PARAMS_TODO
        )
        cmd(
            "COMMAND COUNT",
            "-",
            "Get total number of Redis commands",
            9,
            "2.8.13",
            PARAMS_TODO
        )
        cmd(
            "COMMAND GETKEYS",
            "-",
            "Extract keys given a full Redis command",
            9,
            "2.8.13",
            PARAMS_TODO
        )
        cmd(
            "COMMAND INFO",
            "command-name [command-name ...]",
            "Get array of specific Redis command details",
            9,
            "2.8.13",
            PARAMS_TODO
        )
        cmd(
            "CONFIG GET",
            "parameter",
            "Get the value of a configuration parameter",
            9,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "CONFIG RESETSTAT",
            "-",
            "Reset the stats returned by INFO",
            9,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "CONFIG REWRITE",
            "-",
            "Rewrite the configuration file with the in memory configuration",
            9,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "CONFIG SET",
            "parameter value",
            "Set a configuration parameter to the given value",
            9,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "DBSIZE",
            "-",
            "Return the number of keys in the selected database",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DEBUG OBJECT",
            "key",
            "Get debugging information about a key",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DEBUG SEGFAULT",
            "-",
            "Make the server crash",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DECR",
            "key",
            "Decrement the integer value of a key by one",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DECRBY",
            "key decrement",
            "Decrement the integer value of a key by the given number",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DEL",
            "key [key ...]",
            "Delete a key",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "DISCARD",
            "-",
            "Discard all commands issued after MULTI",
            7,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "DUMP",
            "key",
            "Return a serialized version of the value stored at the specified key.",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "ECHO",
            "message",
            "Echo the given string",
            8,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "EVAL",
            "script numkeys key [key ...] arg [arg ...]",
            "Execute a Lua script server side",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "EVALSHA",
            "sha1 numkeys key [key ...] arg [arg ...]",
            "Execute a Lua script server side",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "EXEC",
            "-",
            "Execute all commands issued after MULTI",
            7,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "EXISTS",
            "key [key ...]",
            "Determine if a key exists",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "EXPIRE",
            "key seconds",
            "Set a key's time to live in seconds",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "EXPIREAT",
            "key timestamp",
            "Set the expiration for a key as a UNIX timestamp",
            0,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "FLUSHALL",
            "-",
            "Remove all keys from all databases",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "FLUSHDB",
            "-",
            "Remove all keys from the current database",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "GEOADD",
            "key longitude latitude member [longitude latitude member ...]",
            "Add one or more geospatial items in the geospatial index represented using a sorted set",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GEODIST",
            "key member1 member2 [unit]",
            "Returns the distance between two members of a geospatial index",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GEOHASH",
            "key member [member ...]",
            "Returns members of a geospatial index as standard geohash strings",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GEOPOS",
            "key member [member ...]",
            "Returns longitude and latitude of members of a geospatial index",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GEORADIUS",
            "key longitude latitude radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count] [ASC|DESC] [STORE key] [STOREDIST key]",
            "Query a sorted set representing a geospatial index to fetch members matching a given maximum distance from a point",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GEORADIUSBYMEMBER",
            "key member radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count] [ASC|DESC] [STORE key] [STOREDIST key]",
            "Query a sorted set representing a geospatial index to fetch members matching a given maximum distance from a member",
            13,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "GET",
            "key",
            "Get the value of a key",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "GETBIT",
            "key offset",
            "Returns the bit value at offset in the string value stored at key",
            1,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "GETRANGE",
            "key start end",
            "Get a substring of the string stored at a key",
            1,
            "2.4.0",
            PARAMS_TODO
        )
        cmd(
            "GETSET",
            "key value",
            "Set the string value of a key and return its old value",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "HDEL",
            "key field [field ...]",
            "Delete one or more hash fields",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HEXISTS",
            "key field",
            "Determine if a hash field exists",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HGET",
            "key field",
            "Get the value of a hash field",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HGETALL",
            "key",
            "Get all the fields and values in a hash",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HINCRBY",
            "key field increment",
            "Increment the integer value of a hash field by the given number",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HINCRBYFLOAT",
            "key field increment",
            "Increment the float value of a hash field by the given amount",
            5,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "HKEYS",
            "key",
            "Get all the fields in a hash",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HLEN",
            "key",
            "Get the number of fields in a hash",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HMGET",
            "key field [field ...]",
            "Get the values of all the given hash fields",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HMSET",
            "key field value [field value ...]",
            "Set multiple hash fields to multiple values",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HSCAN",
            "key cursor [MATCH pattern] [COUNT count]",
            "Incrementally iterate hash fields and associated values",
            5,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "HSET",
            "key field value",
            "Set the string value of a hash field",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HSETNX",
            "key field value",
            "Set the value of a hash field, only if the field does not exist",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "HSTRLEN",
            "key field",
            "Get the length of the value of a hash field",
            5,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "HVALS",
            "key",
            "Get all the values in a hash",
            5,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "INCR",
            "key",
            "Increment the integer value of a key by one",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "INCRBY",
            "key increment",
            "Increment the integer value of a key by the given amount",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "INCRBYFLOAT",
            "key increment",
            "Increment the float value of a key by the given amount",
            1,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "INFO",
            "[section]",
            "Get information and statistics about the server",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "KEYS",
            "pattern",
            "Find all keys matching the given pattern",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LASTSAVE",
            "-",
            "Get the UNIX time stamp of the last successful save to disk",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LINDEX",
            "key index",
            "Get an element from a list by its index",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LINSERT",
            "key BEFORE|AFTER pivot value",
            "Insert an element before or after another element in a list",
            2,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "LLEN",
            "key",
            "Get the length of a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LPOP",
            "key",
            "Remove and get the first element in a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LPUSH",
            "key value [value ...]",
            "Prepend one or multiple values to a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LPUSHX",
            "key value",
            "Prepend a value to a list, only if the list exists",
            2,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "LRANGE",
            "key start stop",
            "Get a range of elements from a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LREM",
            "key count value",
            "Remove elements from a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LSET",
            "key index value",
            "Set the value of an element in a list by its index",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "LTRIM",
            "key start stop",
            "Trim a list to the specified range",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "MGET",
            "key [key ...]",
            "Get the values of all the given keys",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "MIGRATE",
            "host port key|\"\" destination-db timeout [COPY] [REPLACE] [KEYS key]",
            "Atomically transfer a key from a Redis instance to another one.",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "MONITOR",
            "-",
            "Listen for all requests received by the server in real time",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "MOVE",
            "key db",
            "Move a key to another database",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "MSET",
            "key value [key value ...]",
            "Set multiple keys to multiple values",
            1,
            "1.0.1",
            PARAMS_TODO
        )
        cmd(
            "MSETNX",
            "key value [key value ...]",
            "Set multiple keys to multiple values, only if none of the keys exist",
            1,
            "1.0.1",
            PARAMS_TODO
        )
        cmd(
            "MULTI",
            "-",
            "Mark the start of a transaction block",
            7,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "OBJECT",
            "subcommand [arguments [arguments ...]]",
            "Inspect the internals of Redis objects",
            0,
            "2.2.3",
            PARAMS_TODO
        )
        cmd(
            "PERSIST",
            "key",
            "Remove the expiration from a key",
            0,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "PEXPIRE",
            "key milliseconds",
            "Set a key's time to live in milliseconds",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "PEXPIREAT",
            "key milliseconds-timestamp",
            "Set the expiration for a key as a UNIX timestamp specified in milliseconds",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "PFADD",
            "key element [element ...]",
            "Adds the specified elements to the specified HyperLogLog.",
            11,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "PFCOUNT",
            "key [key ...]",
            "Return the approximated cardinality of the set(s, PARAMS_TODO) observed by the HyperLogLog at key(s, PARAMS_TODO).",
            11,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "PFMERGE",
            "destkey sourcekey [sourcekey ...]",
            "Merge N different HyperLogLogs into a single one.",
            11,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "PING",
            "[message]",
            "Ping the server",
            8,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "PSETEX",
            "key milliseconds value",
            "Set the value and expiration in milliseconds of a key",
            1,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "PSUBSCRIBE",
            "pattern [pattern ...]",
            "Listen for messages published to channels matching the given patterns",
            6,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "PTTL",
            "key",
            "Get the time to live for a key in milliseconds",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "PUBLISH",
            "channel message",
            "Post a message to a channel",
            6,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "PUBSUB",
            "subcommand [argument [argument ...]]",
            "Inspect the state of the Pub/Sub subsystem",
            6,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "PUNSUBSCRIBE",
            "[pattern [pattern ...]]",
            "Stop listening for messages posted to channels matching the given patterns",
            6,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "QUIT",
            "-",
            "Close the connection",
            8,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "RANDOMKEY",
            "-",
            "Return a random key from the keyspace",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "READONLY",
            "-",
            "Enables read queries for a connection to a cluster slave node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "READWRITE",
            "-",
            "Disables read queries for a connection to a cluster slave node",
            12,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "RENAME",
            "key newkey",
            "Rename a key",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "RENAMENX",
            "key newkey",
            "Rename a key, only if the new key does not exist",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "RESTORE",
            "key ttl serialized-value [REPLACE]",
            "Create a key using the provided serialized value, previously obtained using DUMP.",
            0,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "ROLE",
            "-",
            "Return the role of the instance in the context of replication",
            9,
            "2.8.12",
            PARAMS_TODO
        )
        cmd(
            "RPOP",
            "key",
            "Remove and get the last element in a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "RPOPLPUSH",
            "source destination",
            "Remove the last element in a list, prepend it to another list and return it",
            2,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "RPUSH",
            "key value [value ...]",
            "Append one or multiple values to a list",
            2,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "RPUSHX",
            "key value",
            "Append a value to a list, only if the list exists",
            2,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "SADD",
            "key member [member ...]",
            "Add one or more members to a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SAVE",
            "-",
            "Synchronously save the dataset to disk",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SCAN",
            "cursor [MATCH pattern] [COUNT count]",
            "Incrementally iterate the keys space",
            0,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "SCARD",
            "key",
            "Get the number of members in a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SCRIPT DEBUG",
            "YES|SYNC|NO",
            "Set the debug mode for executed scripts.",
            10,
            "3.2.0",
            PARAMS_TODO
        )
        cmd(
            "SCRIPT EXISTS",
            "script [script ...]",
            "Check existence of scripts in the script cache.",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "SCRIPT FLUSH",
            "-",
            "Remove all the scripts from the script cache.",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "SCRIPT KILL",
            "-",
            "Kill the script currently in execution.",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "SCRIPT LOAD",
            "script",
            "Load the specified Lua script into the script cache.",
            10,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "SDIFF",
            "key [key ...]",
            "Subtract multiple sets",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SDIFFSTORE",
            "destination key [key ...]",
            "Subtract multiple sets and store the resulting set in a key",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SELECT",
            "index",
            "Change the selected database for the current connection",
            8,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SET",
            "key value [EX seconds] [PX milliseconds] [NX|XX]",
            "Set the string value of a key",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SETBIT",
            "key offset value",
            "Sets or clears the bit at offset in the string value stored at key",
            1,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "SETEX",
            "key seconds value",
            "Set the value and expiration of a key",
            1,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "SETNX",
            "key value",
            "Set the value of a key, only if the key does not exist",
            1,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SETRANGE",
            "key offset value",
            "Overwrite part of a string at key starting at the specified offset",
            1,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "SHUTDOWN",
            "[NOSAVE|SAVE]",
            "Synchronously save the dataset to disk and then shut down the server",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SINTER",
            "key [key ...]",
            "Intersect multiple sets",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SINTERSTORE",
            "destination key [key ...]",
            "Intersect multiple sets and store the resulting set in a key",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SISMEMBER",
            "key member",
            "Determine if a given value is a member of a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SLAVEOF",
            "host port",
            "Make the server a slave of another instance, or promote it as master",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SLOWLOG",
            "subcommand [argument]",
            "Manages the Redis slow queries log",
            9,
            "2.2.12",
            PARAMS_TODO
        )
        cmd(
            "SMEMBERS",
            "key",
            "Get all the members in a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SMOVE",
            "source destination member",
            "Move a member from one set to another",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SORT",
            "key [BY pattern] [LIMIT offset count] [GET pattern [GET pattern ...]] [ASC|DESC] [ALPHA] [STORE destination]",
            "Sort the elements in a list, set or sorted set",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SPOP",
            "key [count]",
            "Remove and return one or multiple random members from a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SRANDMEMBER",
            "key [count]",
            "Get one or multiple random members from a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SREM",
            "key member [member ...]",
            "Remove one or more members from a set",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SSCAN",
            "key cursor [MATCH pattern] [COUNT count]",
            "Incrementally iterate Set elements",
            3,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "STRLEN",
            "key",
            "Get the length of the value stored in a key",
            1,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "SUBSCRIBE",
            "channel [channel ...]",
            "Listen for messages published to the given channels",
            6,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "SUNION",
            "key [key ...]",
            "Add multiple sets",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SUNIONSTORE",
            "destination key [key ...]",
            "Add multiple sets and store the resulting set in a key",
            3,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "SYNC",
            "-",
            "Internal command used for replication",
            9,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "TIME",
            "-",
            "Return the current server time",
            9,
            "2.6.0",
            PARAMS_TODO
        )
        cmd(
            "TTL",
            "key",
            "Get the time to live for a key",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "TYPE",
            "key",
            "Determine the type stored at key",
            0,
            "1.0.0",
            PARAMS_TODO
        )
        cmd(
            "UNSUBSCRIBE",
            "[channel [channel ...]]",
            "Stop listening for messages posted to the given channels",
            6,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "UNWATCH",
            "-",
            "Forget about all watched keys",
            7,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "WAIT",
            "numslaves timeout",
            "Wait for the synchronous replication of all the write commands sent in the context of the current connection",
            0,
            "3.0.0",
            PARAMS_TODO
        )
        cmd(
            "WATCH",
            "key [key ...]",
            "Watch the given keys to determine execution of the MULTI/EXEC block",
            7,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZADD",
            "key [NX|XX] [CH] [INCR] score member [score member ...]",
            "Add one or more members to a sorted set, or update its score if it already exists",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZCARD",
            "key",
            "Get the number of members in a sorted set",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZCOUNT",
            "key min max",
            "Count the members in a sorted set with scores within the given values",
            4,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "ZINCRBY",
            "key increment member",
            "Increment the score of a member in a sorted set",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZINTERSTORE",
            "destination numkeys key [key ...] [WEIGHTS weight] [AGGREGATE SUM|MIN|MAX]",
            "Intersect multiple sorted sets and store the resulting sorted set in a new key",
            4,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "ZLEXCOUNT",
            "key min max",
            "Count the number of members in a sorted set between a given lexicographical range",
            4,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "ZRANGE",
            "key start stop [WITHSCORES]",
            "Return a range of members in a sorted set, by index",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZRANGEBYLEX",
            "key min max [LIMIT offset count]",
            "Return a range of members in a sorted set, by lexicographical range",
            4,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "ZRANGEBYSCORE",
            "key min max [WITHSCORES] [LIMIT offset count]",
            "Return a range of members in a sorted set, by score",
            4,
            "1.0.5",
            PARAMS_TODO
        )
        cmd(
            "ZRANK",
            "key member",
            "Determine the index of a member in a sorted set",
            4,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "ZREM",
            "key member [member ...]",
            "Remove one or more members from a sorted set",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZREMRANGEBYLEX",
            "key min max",
            "Remove all members in a sorted set between the given lexicographical range",
            4,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "ZREMRANGEBYRANK",
            "key start stop",
            "Remove all members in a sorted set within the given indexes",
            4,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "ZREMRANGEBYSCORE",
            "key min max",
            "Remove all members in a sorted set within the given scores",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZREVRANGE",
            "key start stop [WITHSCORES]",
            "Return a range of members in a sorted set, by index, with scores ordered from high to low",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZREVRANGEBYLEX",
            "key max min [LIMIT offset count]",
            "Return a range of members in a sorted set, by lexicographical range, ordered from higher to lower strings.",
            4,
            "2.8.9",
            PARAMS_TODO
        )
        cmd(
            "ZREVRANGEBYSCORE",
            "key max min [WITHSCORES] [LIMIT offset count]",
            "Return a range of members in a sorted set, by score, with scores ordered from high to low",
            4,
            "2.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZREVRANK",
            "key member",
            "Determine the index of a member in a sorted set, with scores ordered from high to low",
            4,
            "2.0.0",
            PARAMS_TODO
        )
        cmd(
            "ZSCAN",
            "key cursor [MATCH pattern] [COUNT count]",
            "Incrementally iterate sorted sets elements and associated scores",
            4,
            "2.8.0",
            PARAMS_TODO
        )
        cmd(
            "ZSCORE",
            "key member",
            "Get the score associated with the given member in a sorted set",
            4,
            "1.2.0",
            PARAMS_TODO
        )
        cmd(
            "ZUNIONSTORE",
            "destination numkeys key [key ...] [WEIGHTS weight] [AGGREGATE SUM|MIN|MAX]",
            "Add multiple sorted sets and store the resulting sorted set in a new key",
            4,
            "2.0.0",
            PARAMS_TODO
        )
    }
}