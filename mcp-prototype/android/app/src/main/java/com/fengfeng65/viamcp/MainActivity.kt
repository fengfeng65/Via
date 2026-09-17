package com.fengfeng65.viamcp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var server: LocalMcpServer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Via MCP Bridge\n\nMCP: http://127.0.0.1:8787/mcp\nProtocol: 2026-07-28\nMode: local loopback"
            textSize = 16f
            setPadding(32, 48, 32, 32)
        })
        server = LocalMcpServer(this).also { it.start() }
    }
    override fun onDestroy() { server?.stop(); super.onDestroy() }
}

private class LocalMcpServer(private val activity: MainActivity) {
    private var socket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() = executor.execute {
        try {
            socket = ServerSocket(8787, 20, java.net.InetAddress.getByName("127.0.0.1"))
            while (!socket!!.isClosed) executor.execute { handle(socket!!.accept()) }
        } catch (_: Exception) { }
    }
    fun stop() { try { socket?.close() } catch (_: Exception) {}; executor.shutdownNow() }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = r.readLine() ?: return
            val headers = mutableMapOf<String,String>()
            var length = 0
            while (true) {
                val line = r.readLine() ?: break
                if (line.isEmpty()) break
                val p = line.indexOf(':')
                if (p > 0) {
                    val k = line.substring(0,p).trim().lowercase(); val v = line.substring(p+1).trim()
                    headers[k] = v
                    if (k == "content-length") length = v.toIntOrNull() ?: 0
                }
            }
            val chars = CharArray(length); var n = 0
            while (n < length) { val x = r.read(chars,n,length-n); if (x <= 0) break; n += x }
            val method = requestLine.substringBefore(' ')
            val path = requestLine.substringAfter(' ').substringBefore(' ')
            if (method != "POST" || path != "/mcp") { respond(s,404,JSONObject().put("error","not found").toString()); return }
            if (headers["mcp-protocol-version"] != "2026-07-28") {
                respond(s,400,rpcError(null,-32022,"MCP-Protocol-Version 2026-07-28 is required")); return
            }
            val req = JSONObject(String(chars,0,n)); val id = if (req.has("id")) req.get("id") else JSONObject.NULL
            val m = req.optString("method"); val p = req.optJSONObject("params") ?: JSONObject()
            val result = when (m) {
                "server/discover" -> JSONObject().put("protocolVersion","2026-07-28").put("capabilities",JSONObject().put("tools",JSONObject()))
                "tools/list" -> JSONObject().put("tools",toolsJson())
                "tools/call" -> callTool(p)
                else -> JSONObject().put("error","Unsupported method: $m")
            }
            respond(s,200,JSONObject().put("jsonrpc","2.0").put("id",id).put("result",result).toString())
        }
    }

    private fun toolsJson(): JSONArray = JSONArray().also { a ->
        ViaTools.definitions.forEach { t ->
            a.put(JSONObject().put("name",t.name).put("description",t.description).put("inputSchema",JSONObject(t.inputSchema)))
        }
    }

    private fun callTool(p: JSONObject): JSONObject {
        val name = p.optString("name"); val a = p.optJSONObject("arguments") ?: JSONObject()
        return when (name) {
            "browser_open" -> {
                val url = a.optString("url")
                activity.runOnUiThread { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).apply { setPackage("mark.via.gp") }) }
                textResult("Opened in Via: $url")
            }
            "browser_current_page","browser_execute_js","browser_back","browser_forward","browser_refresh","browser_scroll" ->
                textResult("WebView hook not connected yet; MCP transport is ready for the next bridge stage.", true)
            else -> textResult("Unknown tool: $name", true)
        }
    }
    private fun textResult(text:String,error:Boolean=false) = JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text",text))).put("isError",error)
    private fun rpcError(id:Any?,code:Int,msg:String) = JSONObject().put("jsonrpc","2.0").put("id",id ?: JSONObject.NULL).put("error",JSONObject().put("code",code).put("message",msg)).toString()
    private fun respond(s:java.net.Socket,status:Int,body:String) {
        val b=body.toByteArray(Charsets.UTF_8); val out=s.getOutputStream()
        out.write("HTTP/1.1 $status ${if(status==200) "OK" else "Error"}\r\nContent-Type: application/json\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(b); out.flush()
    }
}
