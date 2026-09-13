package io.github.donggi.iroiroviewer.format.text

import java.util.Locale

/**
 * 파일 이름에서 언어를 고른다.
 *
 * ## 내용으로 판별하지 않는다
 *
 * 셔뱅(`#!/usr/bin/env python3`)을 읽으면 확장자 없는 스크립트를 맞힐 수 있다. 그러나
 * 그러려면 파일을 **열어서** 판별해야 하고, 목록에서 종류를 정하는 [FileKind] 규칙
 * ("확장자로만 정한다")과 어긋나는 두 번째 규칙이 생긴다. 강조가 틀리는 값은 확장자
 * 없는 스크립트 하나인데, 사용자가 화면에서 언어를 고를 수 있으면 그것으로 충분하다.
 *
 * ## 이름 전체를 보는 것들
 *
 * `Makefile`·`Dockerfile`·`.gitignore` 는 확장자가 없거나 확장자가 곧 이름이다.
 * 그래서 확장자보다 **이름 전체를 먼저** 본다.
 */
object TextLanguage {

    /** 이 파일을 무엇으로 칠할 것인가. 모르면 [PlainHighlighter]. */
    fun forFileName(name: String): RowHighlighter {
        val lower = name.lowercase(Locale.ROOT)
        byWholeName[lower]?.let { return it }
        val dot = lower.lastIndexOf('.')
        if (dot < 0 || dot == lower.length - 1) return PlainHighlighter
        return byExtension[lower.substring(dot + 1)] ?: PlainHighlighter
    }

    /** 사용자가 손으로 고를 수 있는 목록. 첫째가 '칠하지 않음' 이다. */
    val userChoices: List<RowHighlighter> by lazy {
        listOf(PlainHighlighter) +
            (byExtension.values + byWholeName.values).distinct().sortedBy { it.label }
    }

    // ---- 낱말 ------------------------------------------------------------------
    //
    // 목록은 각 언어의 **예약어**다. 표준 라이브러리 이름(`println`·`String`)은 넣지
    // 않는다 — 넣기 시작하면 어디서 끊을지가 없고, 사용자가 만든 같은 이름의 변수까지
    // 칠해져 틀린 색이 늘어난다.

    private val COMMON_LITERALS = setOf("true", "false", "null")

    private val KOTLIN = CodeHighlighter(
        "Kotlin",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "as", "break", "class", "continue", "do", "else", "for", "fun", "if", "in",
                "interface", "is", "object", "package", "return", "super", "this", "throw",
                "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch",
                "constructor", "delegate", "dynamic", "field", "file", "finally", "get",
                "import", "init", "param", "property", "receiver", "set", "setparam", "where",
                "actual", "abstract", "annotation", "companion", "const", "crossinline",
                "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
                "internal", "lateinit", "noinline", "open", "operator", "out", "override",
                "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
                "value", "vararg",
            ),
            literals = COMMON_LITERALS,
            lineComments = listOf("//"),
            nestedBlockComments = true,
            tripleQuotes = true,
        ),
    )

    private val JAVA = CodeHighlighter(
        "Java",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native", "new",
                "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while", "record", "sealed",
                "permits", "var", "yield",
            ),
            literals = COMMON_LITERALS,
            tripleQuotes = true,
        ),
    )

    private val C = CodeHighlighter(
        "C",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "auto", "break", "case", "char", "const", "continue", "default", "do",
                "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline",
                "int", "long", "register", "restrict", "return", "short", "signed",
                "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned",
                "void", "volatile", "while", "include", "define", "ifdef", "ifndef",
                "endif", "pragma", "undef", "elif",
            ),
            literals = setOf("NULL", "true", "false"),
        ),
    )

    private val CPP = CodeHighlighter(
        "C++",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case",
                    "catch", "char", "class", "concept", "const", "consteval", "constexpr",
                    "constinit", "const_cast", "continue", "co_await", "co_return",
                    "co_yield", "decltype", "default", "delete", "do", "double",
                    "dynamic_cast", "else", "enum", "explicit", "export", "extern", "float",
                    "for", "friend", "goto", "if", "inline", "int", "long", "mutable",
                    "namespace", "new", "noexcept", "operator", "or", "private",
                    "protected", "public", "reinterpret_cast", "requires", "return",
                    "short", "signed", "sizeof", "static", "static_assert", "static_cast",
                    "struct", "switch", "template", "this", "thread_local", "throw", "try",
                    "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual",
                "void", "volatile", "while", "include", "define", "pragma", "ifdef",
                "ifndef", "endif",
            ),
            literals = setOf("nullptr", "true", "false", "NULL"),
        ),
    )

    private val CSHARP = CodeHighlighter(
        "C#",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
                "checked", "class", "const", "continue", "decimal", "default", "delegate",
                "do", "double", "else", "enum", "event", "explicit", "extern", "finally",
                "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int",
                "interface", "internal", "is", "lock", "long", "namespace", "new",
                "object", "operator", "out", "override", "params", "private", "protected",
                "public", "readonly", "ref", "return", "sbyte", "sealed", "short",
                "sizeof", "stackalloc", "static", "string", "struct", "switch", "this",
                "throw", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
                "using", "var", "virtual", "void", "volatile", "while", "async", "await",
                "record", "yield",
            ),
            literals = COMMON_LITERALS,
        ),
    )

    private val JS = CodeHighlighter(
        "JavaScript",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "async", "await", "break", "case", "catch", "class", "const", "continue",
                "debugger", "default", "delete", "do", "else", "export", "extends",
                "finally", "for", "function", "get", "if", "import", "in", "instanceof",
                "let", "new", "of", "return", "set", "static", "super", "switch", "this",
                "throw", "try", "typeof", "var", "void", "while", "with", "yield",
            ),
            literals = setOf("true", "false", "null", "undefined", "NaN", "Infinity"),
            backtick = true,
        ),
    )

    private val TS = CodeHighlighter(
        "TypeScript",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "abstract", "any", "as", "asserts", "async", "await", "bigint", "boolean",
                "break", "case", "catch", "class", "const", "continue", "declare",
                "default", "delete", "do", "else", "enum", "export", "extends", "finally",
                "for", "from", "function", "get", "if", "implements", "import", "in",
                "infer", "instanceof", "interface", "is", "keyof", "let", "namespace",
                "never", "new", "number", "object", "of", "private", "protected", "public",
                "readonly", "return", "satisfies", "set", "static", "string", "super",
                "switch", "symbol", "this", "throw", "try", "type", "typeof", "unknown",
                "var", "void", "while", "yield",
            ),
            literals = setOf("true", "false", "null", "undefined", "NaN", "Infinity"),
            backtick = true,
        ),
    )

    private val GO = CodeHighlighter(
        "Go",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "break", "case", "chan", "const", "continue", "default", "defer", "else",
                "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
                "map", "package", "range", "return", "select", "struct", "switch", "type",
                "var", "bool", "byte", "complex64", "complex128", "error", "float32",
                "float64", "int", "int8", "int16", "int32", "int64", "rune", "string",
                "uint", "uint8", "uint16", "uint32", "uint64", "uintptr",
            ),
            literals = setOf("true", "false", "nil", "iota"),
            backtick = true,
        ),
    )

    private val RUST = CodeHighlighter(
        "Rust",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "as", "async", "await", "break", "const", "continue", "crate", "dyn",
                "else", "enum", "extern", "fn", "for", "if", "impl", "in", "let", "loop",
                "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self",
                "static", "struct", "super", "trait", "type", "unsafe", "use", "where",
                "while", "bool", "char", "f32", "f64", "i8", "i16", "i32", "i64", "i128",
                "isize", "str", "u8", "u16", "u32", "u64", "u128", "usize",
            ),
            literals = setOf("true", "false", "None", "Some", "Ok", "Err"),
            nestedBlockComments = true,
        ),
    )

    private val SWIFT = CodeHighlighter(
        "Swift",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
                "func", "import", "init", "inout", "internal", "let", "open", "operator",
                "private", "protocol", "public", "rethrows", "static", "struct",
                "subscript", "typealias", "var", "break", "case", "continue", "default",
                "defer", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat",
                "return", "switch", "where", "while", "as", "catch", "is", "some", "super",
                "self", "Self", "throw", "throws", "try", "async", "await", "actor",
            ),
            literals = setOf("true", "false", "nil"),
            nestedBlockComments = true,
            tripleQuotes = true,
        ),
    )

    private val DART = CodeHighlighter(
        "Dart",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "abstract", "as", "assert", "async", "await", "break", "case", "catch",
                "class", "const", "continue", "covariant", "default", "deferred", "do",
                "dynamic", "else", "enum", "export", "extends", "extension", "external",
                "factory", "final", "finally", "for", "get", "hide", "if", "implements",
                "import", "in", "interface", "is", "late", "library", "mixin", "new", "on",
                "operator", "part", "required", "rethrow", "return", "sealed", "set",
                "show", "static", "super", "switch", "sync", "this", "throw", "try",
                "typedef", "var", "void", "while", "with", "yield",
            ),
            literals = COMMON_LITERALS,
            tripleQuotes = true,
        ),
    )

    private val PHP = CodeHighlighter(
        "PHP",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "abstract", "and", "array", "as", "break", "callable", "case", "catch",
                "class", "clone", "const", "continue", "declare", "default", "do", "echo",
                "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
                "endswitch", "endwhile", "enum", "extends", "final", "finally", "fn",
                "for", "foreach", "function", "global", "goto", "if", "implements",
                "include", "instanceof", "insteadof", "interface", "isset", "list",
                "match", "namespace", "new", "or", "print", "private", "protected",
                "public", "readonly", "require", "return", "static", "switch", "throw",
                "trait", "try", "unset", "use", "var", "while", "xor", "yield",
            ),
            literals = setOf("true", "false", "null", "TRUE", "FALSE", "NULL"),
            lineComments = listOf("//", "#"),
        ),
    )

    private val PYTHON = CodeHighlighter(
        "Python",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "and", "as", "assert", "async", "await", "break", "class", "continue",
                "def", "del", "elif", "else", "except", "finally", "for", "from",
                "global", "if", "import", "in", "is", "lambda", "match", "nonlocal",
                "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
                "case",
            ),
            literals = setOf("True", "False", "None", "self", "cls"),
            lineComments = listOf("#"),
            blockOpen = null,
            blockClose = null,
            tripleQuotes = true,
        ),
    )

    private val RUBY = CodeHighlighter(
        "Ruby",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "alias", "and", "begin", "break", "case", "class", "def", "defined?",
                "do", "else", "elsif", "end", "ensure", "for", "if", "in", "module",
                "next", "not", "or", "redo", "rescue", "retry", "return", "self", "super",
                "then", "undef", "unless", "until", "when", "while", "yield", "require",
                "attr_accessor", "attr_reader", "attr_writer",
            ),
            literals = setOf("true", "false", "nil"),
            lineComments = listOf("#"),
            blockOpen = null,
            blockClose = null,
        ),
    )

    private val SHELL = CodeHighlighter(
        "셸",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "if", "then", "elif", "else", "fi", "case", "esac", "for", "select",
                "while", "until", "do", "done", "in", "function", "time", "coproc",
                "return", "exit", "break", "continue", "local", "export", "readonly",
                "declare", "typeset", "unset", "shift", "source", "alias", "eval", "exec",
                "trap", "set",
            ),
            lineComments = listOf("#"),
            blockOpen = null,
            blockClose = null,
            wordExtra = "_",
        ),
    )

    private val SQL = CodeHighlighter(
        "SQL",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "select", "from", "where", "insert", "into", "values", "update", "set",
                "delete", "create", "table", "index", "view", "drop", "alter", "add",
                "column", "primary", "key", "foreign", "references", "join", "inner",
                "left", "right", "full", "outer", "on", "group", "by", "order", "having",
                "limit", "offset", "union", "all", "distinct", "as", "and", "or", "not",
                "in", "like", "between", "is", "exists", "case", "when", "then", "else",
                "end", "begin", "commit", "rollback", "transaction", "with", "returning",
                "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
                "DELETE", "CREATE", "TABLE", "JOIN", "GROUP", "ORDER", "BY", "AND", "OR",
                "NOT", "NULL", "PRIMARY", "KEY",
            ),
            literals = setOf("null", "true", "false"),
            lineComments = listOf("--"),
        ),
    )

    private val CSS = CodeHighlighter(
        "CSS",
        CodeHighlighter.Dialect(
            keywords = setOf(
                "important", "media", "import", "charset", "keyframes", "supports",
                "font-face", "namespace", "page", "layer", "container", "property",
            ),
            lineComments = emptyList(),
            wordExtra = "-_",
        ),
    )

    private val JSON = CodeHighlighter(
        "JSON",
        CodeHighlighter.Dialect(
            keywords = emptySet(),
            literals = COMMON_LITERALS,
            // **JSON 에는 주석이 없다.** 넣으면 값 안의 `//` 가 주석으로 칠해진다.
            lineComments = emptyList(),
            blockOpen = null,
            blockClose = null,
            singleQuote = false,
        ),
    )

    private val YAML = CodeHighlighter(
        "YAML",
        CodeHighlighter.Dialect(
            keywords = emptySet(),
            literals = setOf("true", "false", "null", "yes", "no", "on", "off", "~"),
            lineComments = listOf("#"),
            blockOpen = null,
            blockClose = null,
            wordExtra = "_-",
        ),
    )

    private val INI = CodeHighlighter(
        "설정 파일",
        CodeHighlighter.Dialect(
            keywords = emptySet(),
            literals = COMMON_LITERALS,
            lineComments = listOf("#", ";"),
            blockOpen = null,
            blockClose = null,
            wordExtra = "_-.",
        ),
    )

    private val XML = MarkupHighlighter("XML")
    private val HTML = MarkupHighlighter("HTML")

    // ---- 표 --------------------------------------------------------------------

    private val byExtension: Map<String, RowHighlighter> = buildMap {
        fun put(h: RowHighlighter, vararg exts: String) { exts.forEach { put(it, h) } }
        put(KOTLIN, "kt", "kts")
        put(JAVA, "java", "aidl")
        put(C, "c", "h")
        put(CPP, "cc", "cpp", "cxx", "hpp", "hh", "hxx", "inl")
        put(CSHARP, "cs")
        put(JS, "js", "mjs", "cjs", "jsx")
        put(TS, "ts", "tsx", "mts", "cts")
        put(GO, "go")
        put(RUST, "rs")
        put(SWIFT, "swift")
        put(DART, "dart")
        put(PHP, "php", "phtml")
        put(PYTHON, "py", "pyi", "pyw")
        put(RUBY, "rb", "gemspec", "rake")
        put(SHELL, "sh", "bash", "zsh", "ksh", "profile", "bashrc", "zshrc")
        put(SQL, "sql")
        put(CSS, "css", "scss", "less", "sass")
        put(JSON, "json", "json5", "jsonc", "webmanifest")
        put(YAML, "yaml", "yml")
        put(INI, "ini", "cfg", "conf", "toml", "properties", "editorconfig", "env")
        put(XML, "xml", "xsd", "xsl", "xslt", "svg", "plist", "resx", "csproj", "vcxproj", "pom")
        put(HTML, "html", "htm", "xhtml", "vue")
    }

    private val byWholeName: Map<String, RowHighlighter> = buildMap {
        fun put(h: RowHighlighter, vararg names: String) { names.forEach { put(it, h) } }
        put(SHELL, "dockerfile", "containerfile", ".bashrc", ".zshrc", ".profile", ".bash_profile")
        put(INI, ".gitignore", ".gitattributes", ".editorconfig", ".npmrc", ".env", "gradle.properties")
        put(YAML, ".gitlab-ci.yml", "docker-compose.yml")
        put(SHELL, "makefile", "gnumakefile")
    }
}
