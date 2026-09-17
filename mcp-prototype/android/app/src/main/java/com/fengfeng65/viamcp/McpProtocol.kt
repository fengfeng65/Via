package com.fengfeng65.viamcp

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>
)

object ViaTools {
    val definitions = listOf(
        ToolDefinition("browser_open", "Open a URL in Via", mapOf("type" to "object", "properties" to mapOf("url" to mapOf("type" to "string")), "required" to listOf("url"))),
        ToolDefinition("browser_current_page", "Read the current Via page URL and title", mapOf("type" to "object", "properties" to emptyMap<String, Any?>())),
        ToolDefinition("browser_execute_js", "Execute JavaScript in the current Via WebView", mapOf("type" to "object", "properties" to mapOf("script" to mapOf("type" to "string")), "required" to listOf("script"))),
        ToolDefinition("browser_back", "Go back in Via history", emptyMap()),
        ToolDefinition("browser_forward", "Go forward in Via history", emptyMap()),
        ToolDefinition("browser_refresh", "Reload the current Via page", emptyMap()),
        ToolDefinition("browser_scroll", "Scroll the current Via page", mapOf("type" to "object", "properties" to mapOf("x" to mapOf("type" to "integer"), "y" to mapOf("type" to "integer")), "required" to listOf("y")))
    )
}
