package com.fengfeng65.viamcp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var server: LocalMcpServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Via MCP Bridge\n\nMCP endpoint: http://127.0.0.1:8787/mcp\n\nProtocol: 2026-07-28\nMode: local loopback"
            textSize = 16f
            setPadding(32, 48, 32, 32)
        }
        setContentView(status)
        server = LocalMcpServer(this).also { it.start() }
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}

private class LocalMcpServer(private val activity: MainActivity) {
    private var socket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        executor.execute {
            try {
                socket = ServerSocket(8787, 20, java.net.InetAddress.getByName("127.0.0.1"))
                while (!socket!!.isClosed) executor.execute { handle(socket!!.accept()) }
            } catch (_: Exception) { }
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) { }
        executor.shutdownNow()
    }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                val p = line!!.indexOf(':')
                if (p > 0) {
                    val k = line!!.substring(0, p).trim().lowercase()
                    val v = line!!.substring(p + 1).trim()
                    headers[k] = v
                    if (k == "content-length") contentLength = v.toIntOrNull() ?: 0
                }
            }
            val body = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(body, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            val method = requestLine.substringBefore(' ')
            val path = requestLine.substringAfter(' ').substringBefore(' ')
            if (method != "POST" || path != "/mcp") {
                respond(s, 404, JSONObject().put("error", "not found").toString())
                return
            }
            val modern = headers["mcp-protocol-version"] == "2026-07-28"
            if (!modern) {
                respond(s, 400, rpcError(null, -32022, "MCP-Protocol-Version 2026-07-28 is required"))
                return
            }
            val request = JSONObject(String(body, 0, read))
            val id = if (request.has("id")) request.get("id") else JSONObject.NULL
            val rpcMethod = request.optString("method")
            val params = request.optJSONObject("params") ?: JSONObject()
            val result = when (rpcMethod) {
                "server/discover" -> JSONObject().put("protocolVersion", "2026-07-28").put("capabilities", JSONObject().put("tools", JSONObject()))
                "tools/list" -> JSONObject().put("tools", toolsJson())
                "tools/call" -> callTool(params)
                else -> JSONObject().put("error", "Unsupported method: $rpcMethod")
            }
            val response = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
            respond(s, 200, response.toString())
        }
    }

    private fun toolsJson(): JSONArray {
        val a = JSONArray()
        ViaTools.definitions.forEach {
            a.put(JSONObject().put("name", it.name).put("description", it.description).put("inputSchema", JSONObject(it.inputSchema)))
        }
        return a
    }

    private fun callTool(params: JSONObject): JSONObject {
        val name = params.optString("name")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        return when (name) {
            "browser_open" -> {
                val url = args.optString("url")
                activity.runOnUiThread {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("mark.via.gp") })
                }
                JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Opened in Via: $url")))
            }
            "browser_current_page", "browser_execute_js", "browser_back", "browser_forward", "browser_refresh", "browser_scroll" ->
                JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Via WebView hook is not connected yet; this tool is registered and ready for the next bridge stage."))).put("isError", true)
            else -> JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Unknown tool: $name"))).put("isError", true)
        }
    }

    private fun rpcError(id: Any?, code: Int, message: String): String =
        JSONObject().put("jsonrpc", "2.0").put("id", id ?: JSONObject.NULL).put("error", JSONObject().put("code", code).put("message", message)).toString()

    private fun respond(s: java.net.Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = s.getOutputStream()
        val header = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8)); out.write(bytes); out.flush()
    }
}
