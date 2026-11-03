# AI connector (MCP) for startup teams

Startup team members can connect **ChatGPT** or **Claude** to the reimbursement platform. They share a photo or PDF of a receipt in chat; the assistant reads it, creates the **draft** claim, attaches the invoice and payment proof, and replies with a link. The student reviews the claim and presses **Submit** themselves.

## What the assistant can and cannot do

| Tool | What it does | Changes data? |
|---|---|---|
| `get_my_team` | Team, program, cohort, members, budget (total, approved, paid, pending, remaining) | No |
| `list_expense_categories` | Category rules from the Nirmaan Student Guide | No |
| `list_my_claims` | The team's claims and statuses, optional status filter | No |
| `get_claim` | One claim: details, documents still missing, warnings, recent messages, next step, links | No |
| `check_claim` | Checks a category + amount against the rules and the remaining budget before creating | No |
| `create_draft_claim` | Creates a DRAFT claim. Returns its code, warnings, a one-time upload link and the edit link | Yes (draft only) |
| `update_draft_claim` | Changes fields of a DRAFT or RETURNED claim | Yes (draft only) |
| `get_upload_link` | One-time browser link (30 minutes) to attach files to a draft | No |
| `upload_document` | Attaches a file sent as base64 (PDF, JPG, PNG, HEIC, max 10 MB) | Yes (draft only) |
| `add_claim_message` | Posts on the claim's message thread | Yes |

**Not possible through the assistant:**
- submitting or resubmitting a claim;
- verifying, approving or paying;
- seeing other teams' data;
- staff or COO use (connecting is limited to team members).

Every tool runs **as the signed-in student, through the same services as the web app**, so team isolation, validation, category and budget rules, and file checks all apply. Changes appear in the audit log as done by the student, with `"via": "AI connector: <app name>"`.

## How it works

```
ChatGPT / Claude ──(1) POST /mcp ─────────────────▶ 401 + WWW-Authenticate: resource_metadata=…
                 ──(2) GET /.well-known/oauth-protected-resource  → which login server
                 ──(3) GET /.well-known/oauth-authorization-server → endpoints, PKCE S256
                 ──(4) POST /oauth/register (RFC 7591)             → client_id
  student browser ─(5) GET /oauth/authorize → Nirmaan login → consent screen → Allow
                 ◀────────────────── redirect with a one-time code
                 ──(6) POST /oauth/token (code + PKCE verifier)    → access token (1 h) + refresh token (30 d)
                 ──(7) POST /mcp  Authorization: Bearer …          → tools/list, tools/call
```

- **Transport:** MCP Streamable HTTP, stateless. Each POST to `/mcp` is one JSON-RPC message and gets a JSON response. Supported methods: `initialize`, `ping`, `tools/list`, `tools/call`. Protocol versions: 2025-06-18, 2025-03-26, 2024-11-05.
- **OAuth 2.1:**
  - authorization-code flow with mandatory **PKCE S256**;
  - public clients only, registered through dynamic client registration;
  - redirect URIs must be HTTPS, or HTTP on localhost for desktop and CLI clients;
  - codes are single-use and valid for 5 minutes. Reusing a code revokes the grant;
  - refresh tokens **rotate**: reusing an old one revokes the whole grant;
  - only SHA-256 hashes of codes and tokens are stored.
- **Revocation:** students see the connected assistants under **AI assistant** in the menu and can **Revoke** them. Deactivating a user also stops their tokens working.
- **Code:** `src/main/java/com/nirmaan/reimburse/mcp/`. Schema: `V3__mcp_oauth.sql`. Tests: `McpConnectorIT`, `SecretsTest`.

## Running it with ChatGPT or claude.ai (needs a public HTTPS address)

ChatGPT and claude.ai connect from their servers, so `localhost` won't work. For a demo, expose your laptop with a free Cloudflare quick tunnel:

```bash
# one-time: download cloudflared (Apple Silicon Mac)
mkdir -p ~/.local/bin
curl -L -o /tmp/cloudflared.tgz https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz
tar -xzf /tmp/cloudflared.tgz -C ~/.local/bin && chmod +x ~/.local/bin/cloudflared

# start the tunnel to the running app; it prints a URL like https://random-words.trycloudflare.com
~/.local/bin/cloudflared tunnel --url http://localhost:8091
```

Then restart the app with that URL as its public address, so the login links and metadata use it:

```bash
cd /Users/sathvik/Neerman
APP_PORT=8091 APP_BASE_URL=https://random-words.trycloudflare.com docker compose up -d app
```

The connector URL is then `https://random-words.trycloudflare.com/mcp`, and it's shown on the **AI assistant** page.

Notes:
- A quick-tunnel URL changes every time you restart `cloudflared`, and connected assistants must then be re-added. For a stable address, use a named Cloudflare tunnel or deploy the app to a server.
- While the tunnel runs, **your app is reachable from the internet**. Use strong passwords, not the demo `Demo@1234` accounts, for anything beyond a short demo. Stop it with Ctrl+C.

### ChatGPT
1. Go to **Settings → Apps & Connectors → Advanced settings** and turn on **Developer mode**.
2. Click **Create**. Name: `Nirmaan`. URL: `https://…/mcp`. Authentication: **OAuth**.
3. Sign in with the Nirmaan team-member account and click **Allow**.
4. In a chat, enable the connector and say: *"Here's my invoice (attach it), prepare a Nirmaan claim."*

### Claude (claude.ai, desktop or mobile)
1. Go to **Settings → Connectors → Add custom connector**. Name: `Nirmaan`. URL: `https://…/mcp`.
2. Click **Connect**, sign in, and click **Allow**.

### Claude Code (works on localhost, no tunnel needed)

```bash
claude mcp add --transport http nirmaan http://localhost:8091/mcp
# then, inside Claude Code: /mcp → nirmaan → Authenticate
```

## Example conversation

> **Student:** *(attaches invoice.pdf)* Prepare a Nirmaan claim for this. It's sensors for our soil prototype.
>
> **Assistant:** I read the invoice: **Robu.in**, invoice **RB-2291** dated **24 Oct 2026**, total **₹18,450.00**. Category: *Consumables & prototype parts*. Justification: "Soil moisture sensors for field prototype v2". Shall I create the draft?
>
> **Student:** Yes.
>
> **Assistant:** Created **NRM-2026-00012** (draft). I attached the invoice. Still missing: **payment proof**. Upload it here (link valid 30 min): https://…/upload/… Then review and press **Submit**: https://…/claims/12/edit
