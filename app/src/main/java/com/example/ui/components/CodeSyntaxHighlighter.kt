package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxAnnotation
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxProperty
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxType

enum class ArtifactRenderKind(val badgeLabel: String) {
    CODE("Code"),
    MARKDOWN("Markdown"),
    MERMAID("Mermaid"),
    LATEX("LaTeX"),
    CYPHER("Cypher · AGE"),
    GRAPH_DATA("Graph"),
    GEOSPATIAL("PostGIS Geo"),
    POSTGRES_SQL("PostgreSQL"),
    STRUCTURED_DATA("Structured"),
    RESEARCH_DOC("Research"),
    PLAIN_TEXT("Text")
}

object CodeSyntaxHighlighter {

    private val pythonKeywords = setOf(
        "and", "as", "assert", "async", "await", "break", "case", "class", "continue",
        "def", "del", "elif", "else", "except", "False", "finally", "for", "from",
        "global", "if", "import", "in", "is", "lambda", "match", "None", "nonlocal",
        "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "self"
    )
    private val pythonTypes = setOf(
        "int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes",
        "object", "Any", "Optional", "Union", "List", "Dict", "Tuple", "Sequence",
        "Callable", "Iterable", "Protocol", "dataclass", "Exception", "ValueError", "TypeError"
    )

    private val jsTsKeywords = setOf(
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "declare", "default", "delete", "do", "else", "enum",
        "export", "extends", "false", "finally", "for", "from", "function", "get", "if",
        "implements", "import", "in", "infer", "instanceof", "interface", "is", "keyof",
        "let", "module", "namespace", "new", "null", "of", "override", "package", "private",
        "protected", "public", "readonly", "return", "satisfies", "set", "static", "super",
        "switch", "this", "throw", "true", "try", "type", "typeof", "undefined", "var",
        "void", "while", "with", "yield"
    )
    private val jsTsTypes = setOf(
        "string", "number", "boolean", "bigint", "symbol", "unknown", "never", "any",
        "object", "Array", "Promise", "Record", "Partial", "Required", "Pick", "Omit",
        "Map", "Set", "Date", "Error", "RegExp", "HTMLElement", "Response", "Request"
    )

    private val rustKeywords = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
        "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
        "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
        "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while"
    )
    private val rustTypes = setOf(
        "bool", "char", "str", "i8", "i16", "i32", "i64", "i128", "isize",
        "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64",
        "String", "Vec", "Option", "Result", "Box", "Arc", "Rc", "Mutex", "RwLock",
        "HashMap", "HashSet", "BTreeMap", "Cow", "Pin", "Future", "Some", "None", "Ok", "Err"
    )

    private val generalCodeKeywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
        "override", "private", "public", "protected", "internal", "suspend", "return",
        "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
        "import", "package", "func", "struct", "defer", "go", "chan", "select", "range",
        "type", "const", "switch", "case", "default", "break", "continue", "true", "false",
        "null", "nil", "void", "static", "final", "new", "this", "super", "SELECT", "FROM",
        "WHERE", "JOIN", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE", "INDEX", "GROUP",
        "ORDER", "BY", "LIMIT", "AS", "AND", "OR", "NOT", "IN", "ON", "VALUES", "SET"
    )

    private val yamlKeywords = setOf("true", "false", "null", "yes", "no", "on", "off")
    private val mermaidKeywords = setOf(
        "flowchart", "graph", "sequenceDiagram", "classDiagram", "stateDiagram",
        "stateDiagram-v2", "erDiagram", "gantt", "mindmap", "timeline", "subgraph",
        "end", "classDef", "class", "style", "linkStyle", "participant", "actor",
        "note", "loop", "alt", "else", "opt", "par", "and", "TD", "TB", "LR", "RL", "BT"
    )

    private val cypherKeywords = setOf(
        "MATCH", "OPTIONAL", "WHERE", "RETURN", "WITH", "CREATE", "MERGE", "DELETE", "DETACH",
        "SET", "REMOVE", "ORDER", "BY", "SKIP", "LIMIT", "UNWIND", "AS", "DISTINCT", "AND", "OR",
        "XOR", "NOT", "IN", "IS", "NULL", "TRUE", "FALSE", "UNION", "ALL", "CALL", "YIELD",
        "ON", "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS", "COUNT", "COLLECT", "cypher", "ag_catalog"
    )
    private val postgresKeywords = setOf(
        "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "FULL", "CROSS",
        "ON", "AS", "CREATE", "ALTER", "DROP", "TABLE", "VIEW", "INDEX", "EXTENSION", "SCHEMA",
        "FUNCTION", "TRIGGER", "IF", "NOT", "EXISTS", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
        "UNIQUE", "CHECK", "DEFAULT", "NULL", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
        "RETURNING", "WITH", "RECURSIVE", "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET",
        "USING", "GIST", "GIN", "BTREE", "LOAD", "SEARCH_PATH", "BEGIN", "COMMIT", "ROLLBACK",
        "AND", "OR", "IN", "BETWEEN", "LIKE", "ILIKE", "CASE", "WHEN", "THEN", "ELSE", "END"
    )
    private val postgresTypes = setOf(
        "INT", "INTEGER", "BIGINT", "BIGSERIAL", "SERIAL", "SMALLINT", "TEXT", "VARCHAR", "CHAR",
        "BOOLEAN", "BOOL", "NUMERIC", "DECIMAL", "DOUBLE", "REAL", "UUID", "JSON", "JSONB",
        "TIMESTAMP", "TIMESTAMPTZ", "DATE", "BYTEA", "GEOMETRY", "GEOGRAPHY", "AGTYPE",
        "POINT", "LINESTRING", "POLYGON", "MULTIPOLYGON", "ST_GeomFromText", "ST_GeomFromGeoJSON",
        "ST_SetSRID", "ST_Transform", "ST_Intersects", "ST_DWithin", "ST_Area", "ST_Distance"
    )
    private val dotKeywords = setOf(
        "digraph", "graph", "subgraph", "strict", "node", "edge", "rankdir", "label", "shape", "style", "color"
    )

    private val codeExtensions = setOf(
        "py", "js", "jsx", "mjs", "cjs", "ts", "tsx", "rs", "go", "kt", "kts", "java",
        "swift", "dart", "c", "cpp", "cc", "h", "hpp", "cs", "rb", "php", "sh", "bash",
        "zsh", "proto", "graphql", "gql", "html", "css", "scss", "r", "scala", "ex", "zig"
    )

    private val markdownExtensions = setOf("md", "markdown", "pdf")
    private val mermaidExtensions = setOf("mmd", "mermaid")
    private val latexExtensions = setOf("tex", "latex")
    private val cypherExtensions = setOf("cypher", "cql", "age")
    private val graphDataExtensions = setOf("dot", "gv", "graphml", "gexf", "d3json")
    private val geospatialExtensions = setOf("geojson", "topojson", "wkt", "ewkt")
    private val postgresExtensions = setOf("sql", "pgsql", "psql", "postgres", "postgresql")
    private val structuredExtensions = setOf("json", "jsonl", "yaml", "yml", "toml", "ini", "xml", "csv")
    private val researchExtensions = setOf("bib")

    fun classifyExtension(extension: String): ArtifactRenderKind {
        val ext = extension.trim().lowercase().removePrefix(".")
        return when {
            ext in markdownExtensions -> ArtifactRenderKind.MARKDOWN
            ext in mermaidExtensions -> ArtifactRenderKind.MERMAID
            ext in latexExtensions -> ArtifactRenderKind.LATEX
            ext in cypherExtensions -> ArtifactRenderKind.CYPHER
            ext in graphDataExtensions -> ArtifactRenderKind.GRAPH_DATA
            ext in geospatialExtensions -> ArtifactRenderKind.GEOSPATIAL
            ext in postgresExtensions -> ArtifactRenderKind.POSTGRES_SQL
            ext in codeExtensions -> ArtifactRenderKind.CODE
            ext in structuredExtensions -> ArtifactRenderKind.STRUCTURED_DATA
            ext in researchExtensions -> ArtifactRenderKind.RESEARCH_DOC
            else -> ArtifactRenderKind.PLAIN_TEXT
        }
    }

    fun highlight(code: String, extension: String): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        val ext = extension.trim().lowercase().removePrefix(".")

        val keywords = when (ext) {
            "py" -> pythonKeywords
            "js", "jsx", "mjs", "cjs", "ts", "tsx" -> jsTsKeywords
            "rs" -> rustKeywords
            "yaml", "yml", "json", "jsonl", "toml", "geojson", "topojson", "d3json" -> yamlKeywords
            "mmd", "mermaid" -> mermaidKeywords
            "cypher", "cql", "age" -> cypherKeywords
            "sql", "pgsql", "psql", "postgres", "postgresql", "plpgsql" -> postgresKeywords
            "dot", "gv", "graphviz" -> dotKeywords
            else -> generalCodeKeywords
        }
        val builtInTypes = when (ext) {
            "py" -> pythonTypes
            "js", "jsx", "mjs", "cjs", "ts", "tsx" -> jsTsTypes
            "rs" -> rustTypes
            "sql", "pgsql", "psql", "postgres", "postgresql", "plpgsql" -> postgresTypes
            else -> emptySet()
        }
        val hashComments = ext in setOf("py", "sh", "bash", "zsh", "yaml", "yml", "toml", "rb", "r", "ex", "dot", "gv")
        val slashComments = ext !in setOf("py", "yaml", "yml", "toml", "tex", "latex", "bib")
        val sqlDashComments = ext in setOf("sql", "pgsql", "psql", "postgres", "postgresql", "plpgsql", "wkt")
        val latexComments = ext in setOf("tex", "latex", "bib")
        val isRust = ext == "rs"
        val isYamlOrToml = ext in setOf("yaml", "yml", "toml")
        val isJson = ext in setOf("json", "jsonl")
        val isMarkdown = ext in setOf("md", "markdown")

        val len = code.length
        return buildAnnotatedString {
            append(code)
            var i = 0
            while (i < len) {
                val c = code[i]

                // Markdown headings & fences when editing raw markdown
                if (isMarkdown && (i == 0 || code[i - 1] == '\n')) {
                    if (c == '#') {
                        val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                        addStyle(SpanStyle(color = SyntaxAnnotation, fontWeight = FontWeight.Bold), i, lineEnd)
                        i = lineEnd
                        continue
                    }
                    if (code.startsWith("```", i)) {
                        val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                        addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), i, lineEnd)
                        i = lineEnd
                        continue
                    }
                }

                // Line comments: //
                if (slashComments && c == '/' && i + 1 < len && code[i + 1] == '/') {
                    val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), i, lineEnd)
                    i = lineEnd
                    continue
                }

                // Block comments: /* ... */
                if (slashComments && c == '/' && i + 1 < len && code[i + 1] == '*') {
                    val close = code.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                    addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), i, close)
                    i = close
                    continue
                }

                // Hash comments (#) or Rust attributes (#[...])
                if (c == '#') {
                    if (isRust && i + 1 < len && (code[i + 1] == '[' || (code[i + 1] == '!' && i + 2 < len && code[i + 2] == '['))) {
                        val close = code.indexOf(']', i + 2).let { if (it == -1) len else it + 1 }
                        addStyle(SpanStyle(color = SyntaxAnnotation), i, close)
                        i = close
                        continue
                    } else if (hashComments) {
                        val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                        addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), i, lineEnd)
                        i = lineEnd
                        continue
                    }
                }

                // SQL -- comments
                if (sqlDashComments && c == '-' && i + 1 < len && code[i + 1] == '-') {
                    val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), i, lineEnd)
                    i = lineEnd
                    continue
                }

                // LaTeX % comments & \commands
                if (latexComments && c == '%') {
                    val lineEnd = code.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxComment, fontStyle = FontStyle.Italic), i, lineEnd)
                    i = lineEnd
                    continue
                }
                if (latexComments && c == '\\' && i + 1 < len && code[i + 1].isLetter()) {
                    var j = i + 1
                    while (j < len && code[j].isLetter()) j++
                    addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), i, j)
                    i = j
                    continue
                }

                // Triple-quoted strings (Python / Kotlin)
                if ((c == '"' || c == '\'') && i + 2 < len && code[i + 1] == c && code[i + 2] == c) {
                    val delim = "$c$c$c"
                    val close = code.indexOf(delim, i + 3).let { if (it == -1) len else it + 3 }
                    addStyle(SpanStyle(color = SyntaxString), i, close)
                    i = close
                    continue
                }

                // Double-quoted or backtick strings (and JSON keys)
                if (c == '"' || c == '`') {
                    var j = i + 1
                    while (j < len) {
                        if (code[j] == '\\') {
                            j += 2
                            continue
                        }
                        if (code[j] == c) {
                            j++
                            break
                        }
                        if (c == '"' && code[j] == '\n') break
                        j++
                    }
                    var nextNonWs = j
                    while (nextNonWs < len && (code[nextNonWs] == ' ' || code[nextNonWs] == '\t')) nextNonWs++
                    val isJsonKey = isJson && nextNonWs < len && code[nextNonWs] == ':'
                    addStyle(SpanStyle(color = if (isJsonKey) SyntaxProperty else SyntaxString), i, j)
                    i = j
                    continue
                }

                // Single-quoted strings / Rust lifetimes
                if (c == '\'') {
                    if (isRust && i + 1 < len && (code[i + 1].isLetter() || code[i + 1] == '_')) {
                        // Check if char literal ('a') vs lifetime ('a / 'static)
                        if (i + 2 < len && code[i + 2] == '\'') {
                            addStyle(SpanStyle(color = SyntaxString), i, i + 3)
                            i += 3
                        } else {
                            var j = i + 1
                            while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                            addStyle(SpanStyle(color = SyntaxAnnotation, fontStyle = FontStyle.Italic), i, j)
                            i = j
                        }
                        continue
                    } else if (!isMarkdown) {
                        var j = i + 1
                        while (j < len) {
                            if (code[j] == '\\') {
                                j += 2
                                continue
                            }
                            if (code[j] == '\'') {
                                j++
                                break
                            }
                            if (code[j] == '\n') break
                            j++
                        }
                        addStyle(SpanStyle(color = SyntaxString), i, j)
                        i = j
                        continue
                    }
                }

                // Decorators / Annotations (@foo)
                if (c == '@' && i + 1 < len && (code[i + 1].isLetter() || code[i + 1] == '_')) {
                    var j = i + 1
                    while (j < len && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                    addStyle(SpanStyle(color = SyntaxAnnotation), i, j)
                    i = j
                    continue
                }

                // Numeric literals
                if (c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_')) {
                    var j = i + 1
                    while (j < len && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    addStyle(SpanStyle(color = SyntaxNumber), i, j)
                    i = j
                    continue
                }

                // Identifiers, Keywords, Types, Rust Macros, YAML Keys
                if (c.isLetter() || c == '_') {
                    var j = i + 1
                    while (j < len && (code[j].isLetterOrDigit() || code[j] == '_' || (isYamlOrToml && code[j] == '-'))) j++
                    val word = code.substring(i, j)

                    when {
                        isYamlOrToml && j < len && code[j] == ':' -> {
                            addStyle(SpanStyle(color = SyntaxProperty, fontWeight = FontWeight.Medium), i, j)
                        }
                        isRust && j < len && code[j] == '!' -> {
                            addStyle(SpanStyle(color = SyntaxAnnotation, fontWeight = FontWeight.SemiBold), i, j + 1)
                            j++
                        }
                        word in keywords -> {
                            addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), i, j)
                        }
                        word in builtInTypes || (word.length > 1 && word[0].isUpperCase() && word.any { it.isLowerCase() }) -> {
                            addStyle(SpanStyle(color = SyntaxType), i, j)
                        }
                    }
                    i = j
                    continue
                }

                i++
            }
        }
    }
}

class SyntaxHighlightVisualTransformation(
    private val extension: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = CodeSyntaxHighlighter.highlight(text.text, extension),
            offsetMapping = OffsetMapping.Identity
        )
    }
}
