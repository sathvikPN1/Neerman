package com.nirmaan.reimburse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP server endpoint (Streamable HTTP transport, stateless): each POST carries one JSON-RPC message and gets a
 * plain JSON response. Supports initialize, ping, tools/list and tools/call. Authentication is done by
 * {@link McpBearerFilter}; tools run as the signed-in team member.
 */
@RestController
public class McpController {

    static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final String INSTRUCTIONS = """
            Nirmaan Reimbursements: helps a startup team member at Nirmaan (IIT Madras pre-incubator) prepare \
            reimbursement claims. You can read the team's budget, claims and category rules, create and edit DRAFT \
            claims, and attach the invoice and payment proof. You cannot submit a claim: after preparing it, give \
            the user the edit_link so they can review and press Submit themselves. Before creating a claim, read \
            the receipt or invoice the user shares, show them the extracted vendor, amount, dates and justification, \
            and get their confirmation. Never invent amounts or invoice numbers. Use check_claim to see category and \
            budget warnings, and list_expense_categories for what is reimbursable. Amounts are in INR.""";

    private final McpTools tools;
    private final ObjectMapper json;

    public McpController(McpTools tools, ObjectMapper json) {
        this.tools = tools;
        this.json = json;
    }

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode body) {
        if (body.isArray()) {
            ArrayNode responses = json.createArrayNode();
            body.forEach(m -> {
                JsonNode r = dispatch(m);
                if (r != null) {
                    responses.add(r);
                }
            });
            return responses.isEmpty() ? ResponseEntity.accepted().build() : ResponseEntity.ok(responses);
        }
        JsonNode response = dispatch(body);
        return response == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(response);
    }

    /** Server-initiated SSE streams are not offered; clients fall back to POST-only. */
    @GetMapping("/mcp")
    public ResponseEntity<Void> noStream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
    }

    @DeleteMapping("/mcp")
    public ResponseEntity<Void> noSession() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", "POST").build();
    }

    /** Returns the JSON-RPC response, or null for notifications (messages without an id). */
    JsonNode dispatch(JsonNode message) {
        JsonNode id = message.get("id");
        String method = message.path("method").asText("");
        boolean notification = id == null || id.isNull();
        try {
            JsonNode result = switch (method) {
                case "initialize" -> initialize(message.path("params"));
                case "ping" -> json.createObjectNode();
                case "tools/list" -> {
                    ObjectNode r = json.createObjectNode();
                    r.set("tools", json.valueToTree(tools.definitions()));
                    yield r;
                }
                case "tools/call" -> tools.call(message.path("params").path("name").asText(),
                        message.path("params").path("arguments"));
                default -> {
                    if (method.startsWith("notifications/")) {
                        yield null;
                    }
                    throw new JsonRpcError(-32601, "Method not found: " + method);
                }
            };
            if (notification) {
                return null;
            }
            ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", result);
            return response;
        } catch (JsonRpcError e) {
            return notification ? null : error(id, e.code, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("MCP {} failed", method, e);
            return notification ? null : error(id, -32603, "Internal error");
        }
    }

    private JsonNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        ObjectNode r = json.createObjectNode();
        r.put("protocolVersion", PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.getFirst());
        r.putObject("capabilities").putObject("tools").put("listChanged", false);
        r.putObject("serverInfo").put("name", "nirmaan-reimbursements").put("title", "Nirmaan Reimbursements")
                .put("version", "1.0.0");
        r.put("instructions", INSTRUCTIONS);
        return r;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id == null ? json.nullNode() : id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }

    static class JsonRpcError extends RuntimeException {
        final int code;

        JsonRpcError(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
