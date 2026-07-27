package com.turbomates.openapi

private val pathTemplateVariable = Regex("""\{([^{}]*)}""")

/**
 * Names of the variables declared in a path template.
 *
 * Ktor syntax is richer than OpenAPI, so the optional marker (`{id?}`) and the tailcard
 * marker (`{path...}`) are stripped to get the bare variable name. Nameless variables
 * (a bare `{...}` tailcard) are skipped: there is nothing to document them under.
 */
internal fun String.pathTemplateVariables(): List<String> {
    return pathTemplateVariable.findAll(this)
        .map { it.groupValues[1].removeSuffix("...").removeSuffix("?").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
}
