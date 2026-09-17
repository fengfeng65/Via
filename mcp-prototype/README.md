# Via MCP Prototype

This branch is the prototype workspace for connecting Via Browser to an MCP server.

## Current architecture

AI client -> MCP Streamable HTTP -> local Android bridge -> Via WebView

The existing Via repository is primarily a localization repository, so the prototype keeps the MCP integration isolated rather than pretending the repository contains the full Via browser source.

## Planned browser tools

- browser_open
- browser_current_page
- browser_back
- browser_forward
- browser_refresh
- browser_tabs
- browser_execute_js
- browser_click
- browser_type
- browser_scroll

## Security

The first test build should bind only to 127.0.0.1 and require an authentication token. Remote access should only be added after local control works.

## Important implementation note

Controlling Via's existing WebView requires a bridge into the Via process. A companion MCP server alone cannot control the WebView. The bridge will therefore be implemented as a separate Android component/hook layer and tested against the Via package before being called an installable release.
