package com.fengfeng65.viamcp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var server: LocalMcpServer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "Via MCP Bridge\n\nStarting local MCP server..."
            textSize = 16f
            setPadding(32, 48, 32, 32)
        }
        setContentView(status)

        server = LocalMcpServer(this) { message ->
            runOnUiThread { status.text = "Via MCP Bridge\n\n$message" }
        }
        server?.start()
    }

    fun openVia(url: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("mark.via.gp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            "Opened in Via: $url"
        } catch (_: ActivityNotFoundException) {
            "Via is not installed or the installed Via package is different."
        } catch (e: Exception) {
            "Unable to open Via: ${e.message ?: "unknown error"}"
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }
}

private class LocalMcpServer(
    private val activity: MainActivity,
    private val onStatus: (String) -> Unit
) {
    private var socket: java.net.ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() = executor.execute {
        try {
            socket = java.net.ServerSocket(
                8787,
                20,
                java.net.InetAddress.getByName("127.0.0.1")
            )
            onStatus("MCP endpoint:\nhttp://127.0.0.1:8787/mcp\n\nProtocol: 2026-07-28\nMode: local loopback\n\nServer: RUNNING")
            while (socket?.isClosed == false) {
                val client = socket?.accept() ?: break
                executor.execute { handle(client) }
            }
        } catch (e: Exception) {
            if (socket?.isClosed != true) {
                onStatus("MCP server failed to start:\n${e.message ?: "unknown error"}")
            }
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        executor.shutdownNow()
    }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            try {
                val r = s.getInputStream().bufferedReader(Charsets.UTF_8)
                val requestLine = r.readLine() ?: return
                val headers = mutableMapOf<String, String>()
                var length = 0
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isEmpty()) break
                    val p = line.indexOf(':')
                    if (p > 0) {
                        val k = line.substring(0, p).trim().lowercase()
                        val v = line.substring(p + 1).trim()
                        headers[k] = v
                        if (k == "content-length") length = v.toIntOrNull() ?: 0
                    }
                }
                if (length < 0 || length > 2_000_000) {
                    respond(s, 400, "{\"error\":\"invalid content length\"}")
                    return
                }
                val chars = CharArray(length)
                var n = 0
                while (n < length) {
                    val x = r.read(chars, n, length - n)
                    if (x <= 0) break
                    n += x
                }
                val method = requestLine.substringBefore(' ')
                val path = requestLine.substringAfter(' ').substringBefore(' ')
                if (method != "POST" || path != "/mcp") {
                    respond(s, 404, "{\"error\":\"not found\"}")
                    return
                }
                if (headers["mcp-protocol-version"] != "2026-07-28") {
                    respond(s, 400, rpcError(null, -32022, "MCP-Protocol-Version 2026-07-28 is required"))
                    return
                }
                val req = org.json.JSONObject(String(chars, 0, n))
                val id = if (req.has("id")) req.get("id") else org.json.JSONObject.NULL
                val methodName = req.optString("method")
                val params = req.optJSONObject("params") ?: org.json.JSONObject()
                val result = when (methodName) {
                    "server/discover" -> org.json.JSONObject()
                        .put("protocolVersion", "2026-07-28")
                        .put("capabilities", org.json.JSONObject().put("tools", org.json.JSONObject()))
                    "tools/list" -> org.json.JSONObject().put("tools", toolsJson())
                    "tools/call" -> callTool(params)
                    else -> org.json.JSONObject().put("error", "Unsupported method: $methodName")
                }
                respond(s, 200, org.json.JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("result", result).toString())
            } catch (e: Exception) {
                respond(s, 500, "{\"error\":\"internal error\"}")
            }
        }
    }

    private fun toolsJson(): org.json.JSONArray = org.json.JSONArray().also { a ->
        ViaTools.definitions.forEach { t ->
            a.put(org.json.JSONObject()
                .put("name", t.name)
                .put("description", t.description)
                .put("inputSchema", org.json.JSONObject(t.inputSchema)))
        }
    }

    private fun callTool(p: org.json.JSONObject): org.json.JSONObject {
        val name = p.optString("name")
        val args = p.optJSONObject("arguments") ?: org.json.JSONObject()
        return when (name) {
            "browser_open" -> activity.runOnUiThreadResult {
                activity.openVia(args.optString("url"))
            }
            "browser_current_page", "browser_execute_js", "browser_back", "browser_forward", "browser_refresh", "browser_scroll" ->
                textResult("WebView hook not connected yet; MCP transport is ready for the next bridge stage.", true)
            else -> textResult("Unknown tool: $name", true)
        }
    }

    private fun textResult(text: String, error: Boolean = false) = org.json.JSONObject()
        .put("content", org.json.JSONArray().put(org.json.JSONObject().put("type", "text").put("text", text)))
        .put("isError", error)

    private fun rpcError(id: Any?, code: Int, msg: String) = org.json.JSONObject()
        .put("jsonrpc", "2.0").put("id", id ?: org.json.JSONObject.NULL)
        .put("error", org.json.JSONObject().put("code", code).put("message", msg)).toString()

    private fun respond(s: java.net.Socket, status: Int, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        val out = s.getOutputStream()
        out.write("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(b)
        out.flush()
    }
}

private fun MainActivity.runOnUiThreadResult(action: () -> String): org.json.JSONObject {
    val result = arrayOf("Opening Via...")
    runOnUiThread { result[0] = action() }
    return org.json.JSONObject().put("content", org.json.JSONArray().put(org.json.JSONObject().put("type", "text").put("text", result[0])))
}