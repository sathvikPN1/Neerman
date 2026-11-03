package com.nirmaan.reimburse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nirmaan.reimburse.category.CategoryService;
import com.nirmaan.reimburse.category.CategoryView;
import com.nirmaan.reimburse.claim.ClaimDetailView;
import com.nirmaan.reimburse.claim.ClaimForm;
import com.nirmaan.reimburse.claim.ClaimRepository;
import com.nirmaan.reimburse.claim.ClaimRow;
import com.nirmaan.reimburse.claim.ClaimService;
import com.nirmaan.reimburse.claim.ClaimStatus;
import com.nirmaan.reimburse.claim.ClaimWarning;
import com.nirmaan.reimburse.claim.CommentService;
import com.nirmaan.reimburse.common.exception.BusinessRuleException;
import com.nirmaan.reimburse.common.exception.NotFoundException;
import com.nirmaan.reimburse.common.money.Money;
import com.nirmaan.reimburse.common.security.AppUserPrincipal;
import com.nirmaan.reimburse.common.security.CurrentUser;
import com.nirmaan.reimburse.common.time.TimeFormats;
import com.nirmaan.reimburse.document.DocumentService;
import com.nirmaan.reimburse.document.DocumentType;
import com.nirmaan.reimburse.document.DocumentView;
import com.nirmaan.reimburse.team.BudgetSnapshot;
import com.nirmaan.reimburse.team.TeamDetail;
import com.nirmaan.reimburse.team.TeamService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The tools offered to AI assistants. Each runs as the signed-in team member through the normal services, so
 * team scoping, validation, budget warnings and audit logging all apply. There is deliberately no submit tool:
 * submitting a money claim stays a human action in the web app.
 */
@Component
public class McpTools {

    /** MCP tool definition as returned by tools/list. */
    public record ToolDef(String name, String title, String description, Map<String, Object> inputSchema,
                          Map<String, Object> annotations) {
    }

    private record Tool(ToolDef def, Function<JsonNode, Object> handler) {
    }

    /** A tool failure the model should read and act on (shown as isError=true). */
    static class ToolError extends RuntimeException {
        ToolError(String message) {
            super(message);
        }
    }

    static final long MAX_UPLOAD_BYTES = DocumentService.MAX_BYTES;

    private final ClaimService claims;
    private final ClaimRepository claimRepository;
    private final DocumentService documents;
    private final CommentService comments;
    private final CategoryService categories;
    private final TeamService teams;
    private final OAuthService oauth;
    private final Validator validator;
    private final ObjectMapper json;
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public McpTools(ClaimService claims, ClaimRepository claimRepository, DocumentService documents,
                    CommentService comments, CategoryService categories, TeamService teams, OAuthService oauth,
                    Validator validator, ObjectMapper json) {
        this.claims = claims;
        this.claimRepository = claimRepository;
        this.documents = documents;
        this.comments = comments;
        this.categories = categories;
        this.teams = teams;
        this.oauth = oauth;
        this.validator = validator;
        this.json = json.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        register();
    }

    public List<ToolDef> definitions() {
        return tools.values().stream().map(Tool::def).toList();
    }

    /** Runs a tool and wraps the outcome as an MCP CallToolResult. */
    public ObjectNode call(String name, JsonNode arguments) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new McpController.JsonRpcError(-32602, "Unknown tool: " + name);
        }
        JsonNode args = arguments == null || arguments.isNull() ? json.createObjectNode() : arguments;
        try {
            Object result = tool.handler().apply(args);
            ObjectNode out = json.createObjectNode();
            JsonNode structured = json.valueToTree(result);
            out.putArray("content").addObject().put("type", "text").put("text", structured.toPrettyString());
            out.set("structuredContent", structured);
            out.put("isError", false);
            return out;
        } catch (ToolError | BusinessRuleException e) {
            return errorResult(e.getMessage());
        } catch (AccessDeniedException | NotFoundException e) {
            return errorResult("That claim was not found in your team, or you are not allowed to change it.");
        }
    }

    private ObjectNode errorResult(String message) {
        ObjectNode out = json.createObjectNode();
        out.putArray("content").addObject().put("type", "text").put("text", message);
        out.put("isError", true);
        return out;
    }

    // ================================================================== tool registry

    private void register() {
        add("get_my_team", "My team and budget",
                "Shows the signed-in member's startup team, program (Pratham/Akshar), cohort, members and budget "
                        + "(total, approved, paid, pending review, remaining). Amounts in INR.",
                schema(Map.of(), List.of()), true, a -> myTeam());

        add("list_expense_categories", "Expense category rules",
                "Lists expense categories from the Nirmaan Student Guide: which are reimbursable, which need "
                        + "pre-approval, and the rule text. Use this to pick the category for a claim.",
                schema(Map.of(), List.of()), true, a -> Map.of("categories", categoryList()));

        add("list_my_claims", "List my team's claims",
                "Lists the team's claims, newest first, with status. Optionally filter by status.",
                schema(Map.of("status", enumProp("Only claims in this status",
                        java.util.Arrays.stream(ClaimStatus.values()).map(Enum::name).toList())), List.of()),
                true, this::listClaims);

        add("get_claim", "Get a claim",
                "Full details of one claim by its code (e.g. NRM-2026-00042): status, amounts, documents and which "
                        + "are still missing, warnings, recent messages and the next step.",
                schema(Map.of("claim_code", str("Claim code, e.g. NRM-2026-00042")), List.of("claim_code")),
                true, a -> claimSummary(resolve(a)));

        add("check_claim", "Check a planned claim",
                "Before creating a claim, checks a category and amount against the rules and the team's remaining "
                        + "budget. Returns blocking problems and notes.",
                schema(Map.of("category", str("Category name, e.g. 'Consumables & prototype parts'"),
                        "amount", num("Amount in INR")), List.of("category")),
                true, this::checkClaim);

        add("create_draft_claim", "Create a draft claim",
                "Creates a DRAFT reimbursement claim for the team. Read the user's invoice/receipt first and confirm "
                        + "the extracted values with them. Returns the claim code, warnings, a one-time upload link for "
                        + "the invoice and payment proof, and the link where the user reviews and submits. "
                        + "This does not submit the claim.",
                schema(claimFieldsSchema(), List.of("category", "amount", "expense_date", "vendor_name", "justification")),
                false, this::createDraft);

        Map<String, Object> updateProps = new LinkedHashMap<>();
        updateProps.put("claim_code", str("Code of a DRAFT or RETURNED claim"));
        updateProps.putAll(claimFieldsSchema());
        add("update_draft_claim", "Update a draft claim",
                "Changes fields of a DRAFT or RETURNED claim (e.g. to fix what staff asked for). Only the fields you "
                        + "pass are changed. Does not resubmit.",
                schema(updateProps, List.of("claim_code")), false, this::updateDraft);

        add("get_upload_link", "Get a document upload link",
                "Returns a one-time browser link (valid 30 minutes) where the user can attach the invoice and payment "
                        + "proof files to a DRAFT or RETURNED claim. Use this when you cannot send the file bytes yourself.",
                schema(Map.of("claim_code", str("Claim code")), List.of("claim_code")), false, this::uploadLink);

        add("upload_document", "Upload a document",
                "Attaches a file to a DRAFT or RETURNED claim. Send the file as base64. Allowed: PDF, JPG, PNG, HEIC, "
                        + "max 10 MB. The invoice must be a real tax invoice (pro-forma invoices are not accepted).",
                schema(Map.of("claim_code", str("Claim code"),
                        "document_type", enumProp("What the file is", List.of("INVOICE", "PAYMENT_PROOF", "OTHER")),
                        "filename", str("Original file name, e.g. invoice.pdf"),
                        "content_base64", str("File contents, base64-encoded")),
                        List.of("claim_code", "document_type", "filename", "content_base64")),
                false, this::uploadDocument);

        add("add_claim_message", "Message Nirmaan staff",
                "Posts a message on a claim's thread (visible to the team and Nirmaan staff), e.g. to answer a "
                        + "question from staff.",
                schema(Map.of("claim_code", str("Claim code"), "message", str("Message text (max 4000 characters)")),
                        List.of("claim_code", "message")), false, this::addMessage);
    }

    private void add(String name, String title, String description, Map<String, Object> inputSchema, boolean readOnly,
                     Function<JsonNode, Object> handler) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("title", title);
        annotations.put("readOnlyHint", readOnly);
        annotations.put("destructiveHint", false);
        annotations.put("openWorldHint", false);
        tools.put(name, new Tool(new ToolDef(name, title, description, inputSchema, annotations), handler));
    }

    // ================================================================== handlers

    private Map<String, Object> myTeam() {
        AppUserPrincipal me = CurrentUser.require();
        TeamDetail d = teams.get(me.teamId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("team", d.summary().name());
        m.put("program", d.summary().program().label());
        m.put("cohort", d.summary().cohortName());
        m.put("you", me.name());
        m.put("members", d.members().stream().map(TeamDetail.Member::name).toList());
        m.put("budget", budget(d.summary().budget()));
        m.put("app_link", oauth.issuer() + "/team");
        return m;
    }

    private Map<String, Object> listClaims(JsonNode a) {
        AppUserPrincipal me = CurrentUser.require();
        String status = text(a, "status");
        List<Map<String, Object>> rows = claims.teamClaims(me.teamId()).stream()
                .filter(r -> status == null || r.status().name().equalsIgnoreCase(status))
                .map(this::row).toList();
        return Map.of("claims", rows, "count", rows.size());
    }

    private Map<String, Object> checkClaim(JsonNode a) {
        AppUserPrincipal me = CurrentUser.require();
        CategoryView c = category(required(a, "category"));
        BigDecimal amount = a.hasNonNull("amount") ? amount(a, "amount") : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", c.name());
        m.put("reimbursable", c.allowed());
        m.put("rule", c.description());
        m.put("problems", warnings(claims.warningsFor(c.id(), amount)));
        m.put("remaining_budget", Money.formatInr(teams.get(me.teamId()).summary().budget().remaining()));
        return m;
    }

    private Map<String, Object> createDraft(JsonNode a) {
        ClaimForm form = new ClaimForm(category(required(a, "category")).id(), amount(a, "amount"),
                date(a, "expense_date", true), required(a, "vendor_name"), upper(text(a, "vendor_gstin")),
                text(a, "invoice_number"), date(a, "invoice_date", false), required(a, "justification"), null);
        validate(form);
        Long id = claims.createDraft(form);
        Map<String, Object> m = claimSummary(id);
        m.put("upload_link", oauth.createUploadLink(id));
        m.put("upload_link_expires_in_minutes", OAuthService.UPLOAD_LINK_TTL.toMinutes());
        return m;
    }

    private Map<String, Object> updateDraft(JsonNode a) {
        Long id = resolve(a);
        ClaimForm cur = claims.formFor(id);
        ClaimForm form = new ClaimForm(
                a.hasNonNull("category") ? category(a.get("category").asText()).id() : cur.categoryId(),
                a.hasNonNull("amount") ? amount(a, "amount") : cur.claimedAmount(),
                a.hasNonNull("expense_date") ? date(a, "expense_date", true) : cur.expenseDate(),
                a.hasNonNull("vendor_name") ? a.get("vendor_name").asText() : cur.vendorName(),
                a.hasNonNull("vendor_gstin") ? upper(text(a, "vendor_gstin")) : cur.vendorGstin(),
                a.hasNonNull("invoice_number") ? text(a, "invoice_number") : cur.invoiceNumber(),
                a.hasNonNull("invoice_date") ? date(a, "invoice_date", false) : cur.invoiceDate(),
                a.hasNonNull("justification") ? a.get("justification").asText() : cur.justification(),
                null);
        validate(form);
        claims.updateDraft(id, form);
        return claimSummary(id);
    }

    private Map<String, Object> uploadLink(JsonNode a) {
        Long id = resolve(a);
        ClaimDetailView v = claims.detail(id);
        if (!v.editable()) {
            throw new ToolError(v.publicCode() + " is " + v.status().label().toLowerCase(Locale.ROOT)
                    + "; documents can only be added to draft or returned claims.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claim_code", v.publicCode());
        m.put("upload_link", oauth.createUploadLink(id));
        m.put("expires_in_minutes", OAuthService.UPLOAD_LINK_TTL.toMinutes());
        m.put("missing_documents", missingDocuments(v));
        return m;
    }

    private Map<String, Object> uploadDocument(JsonNode a) {
        Long id = resolve(a);
        DocumentType type;
        try {
            type = DocumentType.valueOf(required(a, "document_type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ToolError("document_type must be INVOICE, PAYMENT_PROOF or OTHER.");
        }
        String b64 = required(a, "content_base64");
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) {
            b64 = b64.substring(comma + 1); // accept data URLs
        }
        if (b64.length() > (MAX_UPLOAD_BYTES / 3 + 1) * 4 + 16) {
            throw new ToolError("Files must be 10 MB or smaller.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new ToolError("content_base64 is not valid base64.");
        }
        DocumentView doc = documents.upload(id, type, required(a, "filename"), bytes);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uploaded", doc.originalFilename());
        m.put("document_type", doc.type().label());
        m.put("size", doc.sizeLabel());
        m.put("sha256", doc.sha256());
        m.put("claim", claimSummary(id));
        return m;
    }

    private Map<String, Object> addMessage(JsonNode a) {
        Long id = resolve(a);
        comments.add(id, required(a, "message"));
        return Map.of("posted", true, "claim_code", claims.detail(id).publicCode());
    }

    // ================================================================== views

    private Map<String, Object> claimSummary(Long id) {
        ClaimDetailView v = claims.detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claim_code", v.publicCode());
        m.put("status", v.status().name());
        m.put("vendor", v.vendorName());
        m.put("category", v.categoryName());
        m.put("claimed_amount", Money.formatInr(v.claimedAmount()));
        if (v.approvedAmount() != null) {
            m.put("approved_amount", Money.formatInr(v.approvedAmount()));
        }
        m.put("expense_date", String.valueOf(v.expenseDate()));
        m.put("invoice_number", v.invoiceNumber());
        m.put("invoice_date", v.invoiceDate() == null ? null : v.invoiceDate().toString());
        m.put("vendor_gstin", v.vendorGstin());
        m.put("justification", v.justification());
        m.put("priority", v.priority() || v.teamPriority());
        if (v.decisionReason() != null) {
            m.put("reason_from_nirmaan", v.decisionReason());
        }
        m.put("documents", v.documents().stream().map(d -> d.type().label() + ": " + d.originalFilename()).toList());
        m.put("missing_documents", missingDocuments(v));
        m.put("warnings", warnings(v.warnings()));
        m.put("recent_messages", v.comments().stream().skip(Math.max(0, v.comments().size() - 10))
                .map(c -> c.authorName() + " (" + c.authorRole() + ", " + TimeFormats.dateTime(c.createdAt()) + "): " + c.body())
                .toList());
        if (v.payment() != null) {
            m.put("payment", "Paid " + Money.formatInr(v.payment().amount()) + " on " + v.payment().paidOn()
                    + " via " + v.payment().mode() + ", UTR " + v.payment().utr());
        }
        m.put("next_step", nextStep(v));
        m.put("view_link", oauth.issuer() + "/claims/" + v.id());
        if (v.editable()) {
            m.put("edit_link", oauth.issuer() + "/claims/" + v.id() + "/edit");
        }
        return m;
    }

    private Map<String, Object> row(ClaimRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claim_code", r.publicCode());
        m.put("status", r.status().name());
        m.put("vendor", r.vendorName());
        m.put("category", r.categoryName());
        m.put("claimed_amount", Money.formatInr(r.claimedAmount()));
        if (r.approvedAmount() != null) {
            m.put("approved_amount", Money.formatInr(r.approvedAmount()));
        }
        m.put("expense_date", String.valueOf(r.expenseDate()));
        m.put("priority", r.priority());
        return m;
    }

    private static String nextStep(ClaimDetailView v) {
        List<String> missing = missingDocuments(v);
        return switch (v.status()) {
            case DRAFT, RETURNED -> (missing.isEmpty() ? "" : "Attach the " + String.join(" and ", missing).toLowerCase(Locale.ROOT)
                    + " (use get_upload_link or upload_document). ")
                    + "Then the user must open the edit_link, review the claim and press "
                    + (v.status() == ClaimStatus.RETURNED ? "Resubmit" : "Submit") + " themselves.";
            case SUBMITTED -> "Waiting for Nirmaan staff to verify it.";
            case VERIFIED -> "Verified; waiting for the COO's approval.";
            case APPROVED -> "Approved; waiting for Finance to pay it.";
            case PAID -> "Paid. Nothing more to do.";
            case REJECTED -> "Rejected by Nirmaan. See reason_from_nirmaan.";
        };
    }

    private static List<String> missingDocuments(ClaimDetailView v) {
        Set<DocumentType> have = v.documents().stream().map(DocumentView::type).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        if (!have.contains(DocumentType.INVOICE)) missing.add("Invoice");
        if (!have.contains(DocumentType.PAYMENT_PROOF)) missing.add("Payment proof");
        return missing;
    }

    private static List<Map<String, Object>> warnings(List<ClaimWarning> list) {
        return list.stream().map(w -> Map.<String, Object>of("blocks_submission", w.blocking(), "message", w.message())).toList();
    }

    private static Map<String, Object> budget(BudgetSnapshot b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", Money.formatInr(b.total()));
        m.put("approved_unpaid", Money.formatInr(b.approved()));
        m.put("paid", Money.formatInr(b.paid()));
        m.put("pending_review", Money.formatInr(b.pending()));
        m.put("remaining", Money.formatInr(b.remaining()));
        return m;
    }

    private List<Map<String, Object>> categoryList() {
        return categories.listActive().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("reimbursable", c.allowed());
            m.put("requires_pre_approval", c.requiresPreApproval());
            m.put("pre_approval_recommended", c.preApprovalRecommended());
            m.put("detailed_justification_required", c.justificationRequired());
            m.put("rule", c.description());
            return m;
        }).toList();
    }

    // ================================================================== argument helpers

    private Long resolve(JsonNode a) {
        String code = required(a, "claim_code").trim().toUpperCase(Locale.ROOT);
        return claimRepository.findByPublicCode(code).map(c -> c.getId())
                .orElseThrow(() -> new ToolError("No claim " + code + " found in your team. Use list_my_claims to see codes."));
    }

    private CategoryView category(String name) {
        List<CategoryView> all = categories.listActive();
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (CategoryView c : all) {
            if (c.name().toLowerCase(Locale.ROOT).equals(n)) {
                return c;
            }
        }
        List<CategoryView> partial = all.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(n)).toList();
        if (partial.size() == 1) {
            return partial.getFirst();
        }
        throw new ToolError("Unknown category '" + name + "'. Use one of: "
                + all.stream().map(CategoryView::name).collect(Collectors.joining(", ")));
    }

    private void validate(ClaimForm form) {
        Set<ConstraintViolation<ClaimForm>> violations = validator.validate(form);
        if (!violations.isEmpty()) {
            throw new ToolError(violations.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
        }
    }

    private static String text(JsonNode a, String field) {
        JsonNode n = a.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String required(JsonNode a, String field) {
        String s = text(a, field);
        if (s == null) {
            throw new ToolError(field + " is required.");
        }
        return s;
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal amount(JsonNode a, String field) {
        String s = required(a, field).replace(",", "").replace("₹", "").replace("Rs", "").trim();
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ToolError(field + " must be a number of rupees, e.g. 12500.50");
        }
    }

    private static LocalDate date(JsonNode a, String field, boolean required) {
        String s = required ? required(a, field) : text(a, field);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new ToolError(field + " must be a date in YYYY-MM-DD format.");
        }
    }

    // ================================================================== JSON schema helpers

    private static Map<String, Object> claimFieldsSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("category", str("Expense category name (see list_expense_categories)"));
        p.put("amount", num("Amount paid, in INR"));
        p.put("expense_date", str("Date of the expense, YYYY-MM-DD (not in the future)"));
        p.put("vendor_name", str("Seller / vendor name as on the invoice"));
        p.put("vendor_gstin", str("Seller GSTIN if on the invoice, e.g. 33ABCDE1234F1Z5"));
        p.put("invoice_number", str("Invoice number"));
        p.put("invoice_date", str("Invoice date, YYYY-MM-DD"));
        p.put("justification", str("How this expense benefits the startup's project (required by the Nirmaan guide)"));
        return p;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        s.put("additionalProperties", false);
        return s;
    }

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> num(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> enumProp(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
