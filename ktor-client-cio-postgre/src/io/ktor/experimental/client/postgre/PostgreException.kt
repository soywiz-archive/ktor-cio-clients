package io.ktor.experimental.client.postgre

class PostgreException(
    query: String,
    val items: List<String>
) : RuntimeException() {
    private val parts by lazy {
        items.filter { it.isNotEmpty() }.associate { it.first() to it.substring(1) }
    }

    /**
     * Severity: the field contents are:
     * ERROR, FATAL, or PANIC (in an error message),
     * or WARNING, NOTICE, DEBUG, INFO,
     * or LOG (in a notice message),
     * or a localized translation of one of these.
     * Always present.
     */
    val severity: String get() = parts['S']!!

    val sqlstate: String? get() = parts['C'] // Code: the SQLSTATE code for the error (see Appendix A). Not localizable. Always present.
    val pmessage: String? get() = parts['M'] // Message: the primary human-readable error message. This should be accurate but terse (typically one line). Always present.
    val detail: String? get() = parts['D'] // Detail: an optional secondary error message carrying more detail about the problem. Might run to multiple lines.
    val hint: String? get() = parts['H'] // Hint: an optional suggestion what to do about the problem. This is intended to differ from Detail in that it offers advice (potentially inappropriate) rather than hard facts. Might run to multiple lines.
    val position: String? get() = parts['P'] // Position: the field value is a decimal ASCII integer, indicating an error cursor position as an index into the original query string. The first character has index 1, and positions are measured in characters not bytes.
    val internalPosition: String? get() = parts['p'] // Internal position: this is defined the same as the P field, but it is used when the cursor position refers to an internally generated command rather than the one submitted by the client. The q field will always appear when this field appears.
    val internalQuery: String? get() = parts['q'] // Internal query: the text of a failed internally-generated command. This could be, for example, a SQL query issued by a PL/pgSQL function.
    val where: String? get() = parts['W'] // Where: an indication of the context in which the error occurred. Presently this includes a call stack traceback of active procedural language functions and internally-generated queries. The trace is one entry per line, most recent first.
    val schemaName: String? get() = parts['s'] // Schema name: if the error was associated with a specific database object, the name of the schema containing that object, if any.
    val tableName: String? get() = parts['t'] // Table name: if the error was associated with a specific table, the name of the table. (Refer to the schema name field for the name of the table's schema.)
    val columnName: String? get() = parts['c'] // Column name: if the error was associated with a specific table column, the name of the column. (Refer to the schema and table name fields to identify the table.)
    val dataTypeName: String? get() = parts['d'] // Data type name: if the error was associated with a specific data type, the name of the data type. (Refer to the schema name field for the name of the data type's schema.)
    val contraintName: String? get() = parts['n'] // Constraint name: if the error was associated with a specific constraint, the name of the constraint. Refer to fields listed above for the associated table or domain. (For this purpose, indexes are treated as constraints, even if they weren't created with constraint syntax.)
    val fileName: String? get() = parts['F'] // File: the file name of the source-code location where the error was reported.
    val line: String? get() = parts['L'] // Line: the line number of the source-code location where the error was reported.
    val routine: String? get() = parts['R'] // Routine: the name of the source-code routine reporting the error.

    override val message: String? = "$severity: $pmessage ($parts) for query=$query"
}