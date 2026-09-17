# Via MCP Integration Prototype

> Experimental integration work for connecting Via Browser to Model Context Protocol (MCP).

## Current status

The `fengfeng65/Via` repository is a Via localization repository. It does not contain the browser's Java/Kotlin application source, so this branch deliberately does **not** pretend to modify Via's WebView internals.

The integration target is:

```text
AI client
   │
   │ MCP / Streamable HTTP
   ▼
Via-MCP bridge
   │
   │ Android IPC / Hook layer
   ▼
Via Browser WebView
```

## Planned MCP tools

- `browser_open`
- `browser_current_page`
- `browser_back`
- `browser_forward`
- `browser_refresh`
- `browser_tabs`
- `browser_execute_js`
- `browser_click`
- `browser_type`
- `browser_scroll`

## Important implementation constraint

The official Via GitHub repository is intended for localization and does not expose the browser implementation needed for direct WebView integration. A real implementation therefore needs either:

1. a verified full Via source tree, or
2. an Android runtime integration layer (for example a compatible Hook/IPC layer).

Until one of those is verified, MCP tools that claim to directly read or manipulate Via's WebView must not be shipped as fake/stub functionality.

## MCP transport

The implementation will use Streamable HTTP. For local development it should bind to `127.0.0.1` and validate Origin/authentication before exposing the endpoint remotely.

## Next implementation stage

Build the Android bridge as a separate module/project while keeping this branch as the integration specification and compatibility anchor for Via.
