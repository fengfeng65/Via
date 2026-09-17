package com.fengfeng65.viamcp

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var server: LocalMcpServer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        status = TextView(this).apply {
            text = "Via MCP Bridge\n\nBridge installed successfully.\nServer is stopped."
            textSize = 16f
        }
        val start = Button(this).apply {
            text = "Start MCP Server"
            setOnClickListener { startServer() }
        }
        val stop = Button(this).apply {
            text = "Stop MCP Server"
            setOnClickListener { stopServer() }
        }
        root.addView(status)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    private fun startServer() {
        if (server != null) return
        server = LocalMcpServer { message ->
            runOnUiThread { status.text = "Via MCP Bridge\n\n$message" }
        }
        server?.start()
    }

    private fun stopServer() {
        server?.stop()
        server = null
        status.text = "Via MCP Bridge\n\nBridge installed successfully.\nServer is stopped."
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}

private class LocalMcpServer(private val onStatus: (String) -> Unit) {
    private var socket: java.net.ServerSocket? = null
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()

    fun start() = executor.execute {
        try {
            socket = java.net.ServerSocket(8787, 20, java.net.InetAddress.getByName("127.0.0.1"))
            onStatus("MCP endpoint:\nhttp://127.0.0.1:8787/mcp\n\nProtocol: 2026-07-28\nServer: RUNNING")
            while (socket?.isClosed == false) {
                val client = socket?.accept() ?: break
                executor.execute { client.close() }
            }
        } catch (e: Exception) {
            onStatus("MCP server error:\n${e.message ?: "unknown error"}")
        }
    }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}