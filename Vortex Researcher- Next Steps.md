# Vortex Researcher- Next Steps

> **Document Purpose:** Canonical root-level systems architecture blueprint, database & infrastructure gap analysis, prioritized silo breakdown, cascading roadmap, "greased" end-to-end workflow documentation, and collaborator onboarding playbook for **Vortex Researcher**.
>
> **Scope & Privacy Contract:** Vortex Researcher is a **shared universal Android research platform**. Every database, object storage bucket, vector store, self-hosted router, and MCP server endpoint is strictly **Bring-Your-Own-Infrastructure (BYO-Infra)** and **standby-first** (zero background polling). Server-side controls are the real security boundary; client-side indicators are advisory only. No personal hostnames, IPs, ports, tailnet names, or secrets are hardcoded anywhere in this codebase.

---

## 0. System Architecture, Standby Contract & Privacy Boundary

### Executive Baseline

| Dimension | Verified Hardened Baseline |
| :--- | :--- |
| **Platform** | Android (Kotlin + Jetpack Compose M3, Edge-to-Edge, Adaptive Compact / Expanded) |
| **Core AI Engines** | Firebase AI Logic (`Firebase.ai`, Play Integrity App Check in release / Debug provider in debug) + BYOK OpenAI-Compatible Router + **Self-Hosted Text Router** (`POST /query` + `GET /health`) |
| **Secrets & Local Storage** | Android Keystore AES-256-GCM (`EncryptedEnvVaultStore`, including migrated `EXTERNAL_API_KEY` & `GITHUB_PAT`) + Jetpack DataStore (`LocalPreferencesStore`, non-secret prefs only) + Room (`VortexLocalDatabase` v3). `android:allowBackup="false"` plus explicit `backup_rules.xml` & `data_extraction_rules.xml` exclusions for DataStore, vault, MCP stores, and Room DB |
| **Cloud & Sync Channels** | Firestore (`projects`, `prds`, `roundtable`, `public_shares`), Google Drive Mirror, GitHub Git Data API Mirror, Android SAF Local Folder Sync |
| **Visual & Geospatial Studio** | Mermaid.js Interactive Studio (PNG/SVG export + collaborative sticky-note/drawing layer), LaTeX/KaTeX, Cypher/Apache AGE, PostGIS GeoJSON, NetworkX, D3.js, and PostGIS/PostgreSQL Entity Schema Mapper |
| **Standby Infrastructure & MCP Layer** | Universal DB, Object Storage & MCP Connector Hub (3 Storage/DB tiers + 9 MCP templates + Custom BYO CRUD), direct HTTPS URLs primary (Tailscale `https://<machine>.<tailnet>.ts.net:<port>/mcp` & Cloudflare Access sandboxes), Streamable HTTP JSON-RPC 2.0 MCP Client (`initialize`, `tools/list`, `tools/call`), deny-by-default exact-name allowlists, dual-condition read-only policy, untrusted-data envelope, local SHA-256 audit log, offline UTC↔Local Time Sync, and in-app Handoff Viewer |

### Operating Assumptions & Security Boundary

1. **Standby-First (Zero Background Polling):** External servers and databases remain **OFFLINE** while backend systems are hardened. Vortex Researcher performs zero automatic background polling and assumes nothing is online until you manually tap **Test Endpoint**, **Check /health**, or **Sync `tools/list`**.
2. **Server-Side Controls Are the Real Security Boundary:** Anything enforced on the phone (UI toggles, advisory capability grouping, or confirmation dialogs) is **advisory only**. Real access control, Tailscale tailnet ACLs, Cloudflare Access per-person identity checks, Bearer authentication, and server-side read-only enforcement (such as Memgraph `MCP_READ_ONLY` + a query allowlist proxy) MUST live on the server.
3. **Direct HTTPS URLs Primary (No Single Mandatory Gateway):**
   - **Owner Tailscale Tailnet:** One direct HTTPS URL per server, each on its own port (`https://<machine>.<tailnet>.ts.net:<port>/mcp`), with tailnet membership as the network boundary plus optional Bearer token from the encrypted vault.
   - **Cloudflare Access Sandboxes:** Used for sandbox copies shared with collaborators via per-person identity login (no shared service tokens). Probes and MCP requests disable redirects (`followRedirects = false`) and surface `401`/`403`/`3xx` login challenges cleanly as **`AUTH REQUIRED`**.
   - **Optional Gateway Base URL:** Kept available in Tab 3 for deployments that choose to put connectors behind a reverse proxy, but disabled by default on every connector.
4. **Vendor-Neutral & Bring-Your-Own:** No personal hostnames, IPs, specific port assignments, real tailnet names, non-Firebase personal account/project IDs, or secrets exist in code, docs, defaults, or examples.

### System Topology & Zero-Leakage Boundary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                 VORTEX RESEARCHER (SHARED UNIVERSAL APP)                     │
│  ┌──────────────┐  ┌───────────────────┐  ┌───────────────┐  ┌────────────┐ │
│  │  Dashboard   │  │ Research & Studio │  │ Universal DB  │  │ Next Steps │ │
│  │ Voice Search │  │ (Mermaid / LaTeX /│  │ & MCP Hub     │  │ Handoff +  │ │
│  │ & Auto-Tags  │  │  PostGIS / Graph) │  │ (BYO-Infra)   │  │ GitHub Sync│ │
│  └──────┬───────┘  └─────────┬─────────┘  └───────┬───────┘  └─────┬──────┘ │
└─────────┼────────────────────┼────────────────────┼────────────────┼────────┘
          │                    │                    │                │
          ▼                    ▼                    ▼                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│          DEVICE-LOCAL ZERO-LEAKAGE BOUNDARY (BACKUP-EXCLUDED)                │
│  ┌─────────────────────────┐     ┌────────────────────────────────────────┐  │
│  │ Encrypted .env Vault    │────▶│ Universal Connector Registry (Local)   │  │
│  │ (AES-256-GCM Keystore)  │     │ • Per-connector Direct HTTPS Primary   │  │
│  │ • EXTERNAL_API_KEY      │     │ • Visibility Scope:                    │  │
│  │ • GITHUB_PAT            │     │   [PRIVATE_SOLO] (name anonymized by   │  │
│  │ • DB/MCP Bearer & Header│     │     default in cloud AI prompts)       │  │
│  │   refs (never backed up)│     │   [SANDBOXED_READ_ONLY] (server RO)    │  │
│  │ • Explicit confirmation │     │   [PUBLIC_TEAM] (opt-in descriptor)    │  │
│  │   for plaintext export  │     │ • Streamable HTTP MCP Client + Local   │  │
│  └─────────────────────────┘     │   SHA-256 Audit Log + Full Result Ref  │  │
│                                  └──────────────────┬─────────────────────┘  │
└─────────────────────────────────────────────────────┼────────────────────────┘
                                                      │
         ┌────────────────────────────────────────────┴─────────────────────────┐
         ▼ (Direct Tailscale HTTPS · Owner Tailnet)                             ▼ (Cloudflare Access / Shared Sandbox)
┌─────────────────────────────────────────────┐       ┌──────────────────────────────────────────────┐
│ OWNER PRIVATE INFRASTRUCTURE (STANDBY)      │       │ COLLABORATIVE / PUBLIC ENDPOINTS             │
│ • Neon PostgreSQL (PostGIS + Apache AGE)    │       │ • Forked Read-Only Memgraph Sandbox          │
│ • S3 / R2 / MinIO Object Storage            │       │   (Server MCP_READ_ONLY + query allowlist)   │
│ • LanceDB Hybrid Vector Store               │       │ • Self-Hosted Text Router (/query, /health)  │
│ • Direct Per-Server MCP Endpoints           │       │ • Shared Firestore / GitHub Repo Mirror      │
│   https://<machine>.<tailnet>.ts.net:<port> │       │   (Root 'Vortex Researcher- Next Steps.md')  │
└─────────────────────────────────────────────┘       └──────────────────────────────────────────────┘
```

### Core Codebase Module Map (Collaborator Orientation)

| Package / Layer | Key Modules | Responsibility |
| :--- | :--- | :--- |
| `auth` | `AppCheckInit`, `debug/AppCheckVariantInstaller`, `release/AppCheckVariantInstaller`, `GoogleAuthHelper` | Variant-split Firebase App Check (Play Integrity in release, Debug provider in debug) and Google Credential Manager sign-in |
| `data/ai` | `VortexPipelineOrchestrator`, `VortexModelRouter`, `ExternalRouterApi` | 7-stage grounded synthesis, stage-scoped context injection, OpenAI-compatible router, and Self-Hosted Text Router (`POST /query`, `GET /health`, 4,000-char cap, lane/latency telemetry) |
| `data/infra` | `InfrastructureConnectorModels`, `InfrastructureRegistryEngine`, `McpJsonRpcClient`, `McpLocalStores`, `TimeSyncContextEngine` | Universal BYO DB/Storage/MCP contracts, universal HTTPS + SSRF validation, `AUTH_REQUIRED` probe, Streamable HTTP JSON-RPC 2.0 MCP client, dual-condition read-only policy, untrusted-data envelope, local SHA-256 audit log, offline UTC↔Local clock |
| `data/local` | `LocalPreferencesStore`, `VortexLocalDatabase`, `VortexCacheEntities`, `VortexCacheDaos` | Non-secret DataStore preferences & registry, Room v3 offline cache + pending sync queue |
| `data/security` | `EncryptedEnvVaultStore`, `SecretVaultMigration`, `EnvSecretModels` | Hardware-backed AES-256-GCM `.env` vault, one-time verified migration of legacy DataStore secrets, per-entry AI permission matrix, explicit unencrypted-export confirmation gate |
| `data/sync` & `data/github` | `OfflineFirstRepository`, `GitHubGitSyncEngine`, `GoogleDriveSyncService`, `SafFolderSyncManager` | Quad-channel replication (Room ↔ Firestore, GitHub atomic commits, Drive mirror, zero-permission SAF) |
| `data/docs` | `VortexNextStepsDocProvider` | Structured in-app handoff document & one-tap root GitHub mirror source (byte-for-byte identical to root Markdown) |
| `ui/components` | `InfrastructureHubSheet`, `EnvSecretsVaultSheet`, `SheetsAndDialogs`, `NextStepsHandoffDialog`, `MermaidDiagramViewer`, `SpecializedFormatViewer`, `PostGisSchemaMapperView` | Full-screen Obsidian M3 studios, MCP live tool policy & invoker dialog, Self-Hosted router payload preview, interactive diagrams, schema mapper |

---

## 1. Core Database & Storage Gap Analysis

Databases are the load-bearing foundation of Vortex Researcher. Auditing `firestore.rules`, `VortexCacheEntities.kt`, `VortexLocalDatabase.kt`, `OfflineFirstRepository.kt`, and `PostGisSchemaMapperEngine.kt` exposes **six foundational gaps**:

### 1.1 Firestore 1 MB Hard Document Ceiling Collision (Critical)
- **Current State:** `projects/{projectId}` embeds `filesJson` (450 KB cap), `stagesJson` (300 KB cap), `chatAnswer` (200 KB cap), and `chatHistoryJson` (220 KB cap) inside a **single Firestore document**.
- **Foundational Hole:** Combined upper bounds equal **1.17 MB** before counting metadata, tags, and team arrays—exceeding Firestore's strict **1,048,576-byte (1 MB)** hard limit. Projects containing rich GeoJSON layers, GraphML graphs, Mermaid brainstorm boards, or PostGIS migration SQL can silently fail Firestore writes.
- **Required Support:**
  1. **S3 / R2 / MinIO Object Storage Blob Offload Tier** (`storage_object_s3`) with a **256 KB offload threshold**. Files > 256 KB write to Object Storage via presigned HTTPS URLs and store only a lightweight pointer (`blobUri`, `sha256`, `byteSize`) in Firestore.
  2. **Subcollection Split (Next DB Milestone):** Move `files` and `chatHistory` out of root `projects/{projectId}` into dedicated subcollections (`projects/{id}/files/{fileId}` and `projects/{id}/chats/{turnId}`).

### 1.2 Room Destructive Migration Risk (High)
- **Current State:** `VortexLocalDatabase.kt` runs at `version = 3`, `exportSchema = false`, with `.fallbackToDestructiveMigration(dropAllTables = true)`.
- **Foundational Hole:** Any future schema bump (e.g., adding blob pointer columns, LanceDB chunk hashes, or Temporal workflow IDs) will **wipe `pending_sync_ops`**, destroying queued offline edits before they sync to Firestore.
- **Required Support:** Enable `exportSchema = true`, author explicit `Migration(3, 4)` classes, and add a pre-migration guard that snapshots `pending_sync_ops` prior to schema upgrade.

### 1.3 Relational & Spatial Execution Tier — Neon PostgreSQL + PostGIS + Apache AGE (High)
- **Current State:** `PostGisSchemaMapperEngine.kt` generates normalized PostgreSQL + PostGIS (`SRID 4326`, `GIST` spatial indexes) + Apache AGE Cypher migration SQL locally, but cannot execute migrations or query Neon DB directly.
- **Foundational Hole:** Android cannot safely maintain direct libpq TCP connection pools against serverless PostgreSQL without an HTTPS SQL proxy.
- **Required Support:** Universal **Neon / PostgreSQL HTTPS SQL Proxy Connector** (`db_neon_postgres`, bound to `NEON_DATABASE_URL`) supporting parameterized HTTPS queries, transactional dry-run validation (`BEGIN; ... ROLLBACK;`), and strict `PRIVATE_SOLO` default isolation.

### 1.4 Hybrid Vector & Semantic Search Tier — LanceDB (High)
- **Current State:** `MetadataTagEngine.kt` performs fast in-memory keyword and `#tag` scoring across loaded project titles, tags, and chat turns, but cannot run semantic vector retrieval over long synthesis stages, Roundtable files, or source repositories.
- **Foundational Hole:** As workspaces scale beyond 50+ projects, keyword matching misses conceptually related research across projects.
- **Required Support:** Universal **LanceDB Vector Store Connector** (`vector_lancedb`, bound to `LANCEDB_URL`) for hybrid vector + BM25 retrieval, chunked upsert on pipeline completion, and project-scoped RAG context injection.

### 1.5 Live Graph Execution & Collaborative Sandbox Tier — Memgraph (Medium-High)
- **Current State:** `SpecializedFormatEngine.kt` renders Cypher/AGE/NetworkX graphs locally in WebView; live Memgraph traversal is wired via the Streamable HTTP MCP client once a server comes online.
- **Foundational Hole:** Real Memgraph tool names such as `run_cypher_query` can mutate a graph without containing words like `write` or `delete`, so client-side substring filtering is **never** a security control.
- **Required Support:** Pair the **Memgraph Sandboxed Read-Only Preset** (`serverEnforcesReadOnly = true`, `SANDBOXED_READ_ONLY` visibility) with server-side **`MCP_READ_ONLY`** enforcement and a query allowlist proxy, plus exact-name allowlisting from live `tools/list`.

### 1.6 Unified Storage Router Policy (Data Placement Contract)

| Data Payload Class | Canonical Storage Tier | Fallback / Sync Mirror |
| :--- | :--- | :--- |
| Project metadata, team ACLs, lightweight status (< 256 KB) | **Firestore + Room Cache** | Offline queue (`pending_sync_ops`) |
| Large artifacts (> 256 KB: GeoJSON, GraphML, PNG/SVG, audio) | **S3 / R2 / MinIO Object Storage** | Lightweight pointer + SHA-256 in Firestore |
| Extracted entities, PostGIS geometries (`SRID 4326`), relational tables | **Neon PostgreSQL (PostGIS + AGE)** | Versioned `.sql` migrations in GitHub / SAF |
| Embeddings, semantic chunks, voice transcripts, Roundtable notes | **LanceDB Vector Store** | Keyword + `#tag` index in `MetadataTagEngine` |
| Live property graphs & collaborative agentic traversals | **Memgraph MCP (server-side `MCP_READ_ONLY` + allowlist proxy)** | Local Cypher / NetworkX studio export |
| Human-readable docs, PRDs, architecture diagrams | **GitHub Mirror + Google Drive + Android SAF** | Local Room snapshot & Bulk ZIP export |

---

## 2. Prioritized Silo Breakdown & Accurate MCP Connector Contracts

After Databases & Storage (Silo #1), the remaining six silos are prioritized by architectural leverage and operational risk:

### Priority 2 — MCP Protocol & Universal Agent Tooling Silo (9 Canonical Templates + Custom)
All connector templates default to **`STANDBY`**, **`PRIVATE_SOLO`**, and **`useGatewayProxy = false`** (direct per-server HTTPS URLs primary). All template tool lists are explicitly marked **"expected; confirm with `tools/list` when online"**; live calls use deny-by-default exact-name allowlists discovered from `tools/list` (never substring matching).

| # | MCP Server Template | Real Role & Expected Tools (*confirm with `tools/list` when online*) | Isolation, Phone-Callability & Policy Contract |
| :--- | :--- | :--- | :--- |
| **1** | **Temporal MCP** | Durable workflow orchestration for long-running pipelines, retries, and status inspection (`start_workflow`, `describe_workflow`, `query_workflow`, `signal_workflow`) | Enabled in standby; stage-scoped once ONLINE and allowlisted |
| **2** | **Letta MCP** | Persistent hierarchical core + archival agent memory across sessions & Roundtable (`read_core_memory`, `search_archival_memory`, `insert_archival_memory`) | Project-scoped memory partitions; prevents cross-tenant leakage |
| **3** | **Memgraph MCP** | Live Cypher property graph queries & schema introspection (`list_databases`, `use_database`, `run_cypher_query`, `search_schema`, `get_node_schema`, `get_relationship_schema`, `get_enum_schema`) | Production Memgraph is NEVER exposed; 1-tap Sandboxed Read-Only Preset sets `serverEnforcesReadOnly = true` + `SANDBOXED_READ_ONLY` (real enforcement is server-side `MCP_READ_ONLY` + query allowlist proxy) |
| **4** | **Time & Date Sync MCP** | Canonical server time lookup (`get_current_time`) | Local offline UTC↔Local engine active immediately; delegates when ONLINE |
| **5** | **Vertex Solver MCP** | Mathematical optimization, routing & formal constraint verification (`solve_linear_program`, `solve_routing`, `verify_constraints`) | Read-only solver invocation over extracted PostGIS/graph payloads |
| **6** | **Playwright MCP** | **Primary** deterministic headless browser automation & structured DOM capture (`browser_navigate`, `browser_snapshot`, `browser_click`, `browser_evaluate`) | Primary web extraction & citation verification |
| **7** | **Browser Use MCP** | **Explicit Fallback immediately after Playwright** (`fallbackAfterId = mcp_playwright`, editable `fallbackTrigger`) for dynamic SPAs/vision tasks (`browser.agent_navigate`, `browser.visual_extract`, `browser.fallback_crawl`) | **Server-side isolated sandbox tier only (`phoneDirectCallable = false`)** — never called directly from phone; treat output as untrusted data |
| **8** | **VPS-Access Launcher MCP** | Remote host/container health & service control (`vps.health_check`, `vps.list_services`, `vps.launch_service`, `vps.restart_container`) | **Disabled by default**, locked strictly to **`PRIVATE_SOLO`**, **`requirePerCallConfirmation = true`** (confirms every call regardless of hints) |
| **9** | **World Mechanic ("Stage") MCP** | Sandboxed code-execution server ("Stage") with port-bound server-side trust (`stage_exec`, `stage_files`, `stage_session`, `stage_audit`, `stage_browse` refusing stub) | **Disabled by default**, cannot be `PUBLIC_TEAM`, **`phoneDirectCallable = false`** (phone-originated calls count as untrusted) |
| **+** | **Custom DB / Custom MCP** | Universal BYO extensibility for any user-supplied HTTPS database or MCP server | Full CRUD with universal HTTPS + SSRF validation, custom header vault refs, and 3-tier visibility scope |

### Priority 3 — Durable Orchestration & Context Hygiene (`VortexPipelineOrchestrator`)
- **Shipped Hardening:**
  - `buildAuthorizedAgentInfraContext(state, currentStageId)` injects **only** enabled, `ONLINE`, phone-callable MCP connectors that have at least one allowlisted tool and match the active stage (`selectedStages`).
  - `PRIVATE_SOLO` connector names are anonymized in cloud prompts unless the user explicitly enables `shareNameWithAi` (OFF by default).
  - Hard token budget enforced (default **150 tokens**, configurable 40–1000).
- **Next Phase:** Bind `WorkManager` local checkpointing and delegate multi-minute workflows to **Temporal MCP** when online.

### Priority 4 — Self-Hosted Text Router Integration (`ExternalRouterApi` & `VortexModelRouter`)
- **Shipped Hardening:**
  - Added `ExternalRouterType.SELF_HOSTED_TEXT` alongside `OPENAI_COMPATIBLE`.
  - Sends `POST <baseUrl>/query` with exact JSON body `{"prompt": "<string>"}` and parses `{"response": "...", "lane": "...", "latency": 1.23}`.
  - Enforces a **4,000-character prompt cap** with live ResearchScreen counter, Trim helper, and exact JSON payload preview.
  - **Grounding is OFF by default** for self-hosted queries; users can opt in to append an approved short summary.
  - Supports `GET <baseUrl>/health` status check, explicit `200`/`400`/`401`/`3xx` handling (redirects disabled), Bearer token from Keystore vault ref, and `lane` + `latency` chips on Stage cards and Chat turns.

### Priority 5 — Sync & Replication Fabric Silo (Room + Firestore + Drive + GitHub + SAF)
- Consolidate duplicate SHA-256 digest and badge state across Room/Firestore, Drive, GitHub, and SAF into a unified `WorkspaceDigestCache` + `SyncChannelBadgeState`.

### Priority 6 — Visualization, Authoring & Spatial/Graph Studio Silo
- Extract a shared `InteractiveDiagramSurface` and universal `DiagramAnnotationOverlay` spanning Mermaid, LaTeX, Cypher, GeoJSON, NetworkX, and D3.js.

### Priority 7 — Security, Release Hardening & App Check Silo
- **Shipped Hardening:**
  - Migrated `EXTERNAL_API_KEY` and `GITHUB_PAT` out of plain DataStore into `EncryptedEnvVaultStore` (Android Keystore AES-256-GCM) via one-time verified `SecretVaultMigration`.
  - Locked down backups (`android:allowBackup="false"` + `backup_rules.xml` + `data_extraction_rules.xml`) and gated plaintext `.env` export behind an explicit unencrypted-export confirmation dialog (unchecked by default).
  - Enabled R8 minification (`isMinifyEnabled = true`) with explicit ProGuard keep rules (`proguard-rules.pro`) and `res/raw/keep.xml` (`isShrinkResources = false` kept safely until on-device WebView/resource smoke tests confirm zero stripped assets).
  - Switched release Firebase App Check from reCAPTCHA Enterprise to **Play Integrity** (`releaseImplementation(libs.firebase.appcheck.playintegrity)` + `debugImplementation(libs.firebase.appcheck.debug)`) and removed unused `recaptcha_site_key` from `firebase_applet_config.xml`.

---

## 3. Cascading & Compounding Infrastructure Roadmap

Ordered strictly so each completed phase **unlocks and compounds** the next with zero rework:

1. **[SHIPPED] Phase 0 — Universal Standby Registry, Privacy Isolation & Time Sync Foundation**
   - Shipped device-local connector registry, 3 storage/DB + 9 MCP templates, Custom BYO CRUD, 3-tier visibility scope, offline UTC↔Local Time Sync, and in-app Next Steps Handoff Viewer.

2. **[SHIPPED IN THIS HARDENING PASS] Phase 0.5 — Secrets Hardening, Release R8/Play Integrity, Self-Hosted Router & Streamable HTTP MCP Client**
   - Verified Keystore migration of `EXTERNAL_API_KEY` & `GITHUB_PAT`, backup exclusions, plaintext `.env` export warning gate.
   - Replaced misleading "Read-Only Sandbox Guard" with **Advisory Capability Display** + `serverEnforcesReadOnly` badge + accurate 5 corrected connector templates.
   - Universal HTTPS endpoint validation + `AUTH_REQUIRED` probe classification + stage-scoped 150-token prompt context with private-name anonymization.
   - Self-Hosted Text Router (`POST /query`, `GET /health`, 4,000-char cap, lane/latency UI chips).
   - Release R8 minification (`proguard-rules.pro`, `res/raw/keep.xml`) + Play Integrity App Check split.
   - Streamable HTTP JSON-RPC 2.0 MCP Client (`initialize`, `tools/list`, `tools/call`), dual-condition read-only classification (`annotations.readOnlyHint == true` AND user Hub toggle), untrusted-data envelope (2,000-char cap + local full-result reference store), and local SHA-256 audit log.

3. **Phase 1 — Room Safe Migrations & Firestore Payload Guard**
   - Set `exportSchema = true` on `VortexLocalDatabase`, ship explicit `Migration(3, 4)`, snapshot `pending_sync_ops` before upgrades, and enforce a 256 KB pre-write payload guard before Firestore writes.

4. **Phase 2 — Object Storage Blob Offload + Neon PostgreSQL HTTPS SQL Adapter**
   - Activate presigned S3/R2 upload/download for artifacts > 256 KB and wire `PostGisSchemaMapperView` to a Neon HTTPS SQL proxy with `BEGIN; ... ROLLBACK;` dry-run validation.

5. **Phase 3 — Live Server Bring-Up & On-Device Release Verification**
   - Run live `tools/list` against each online Tailscale/Cloudflare Access MCP server to confirm exact tool names and `annotations.readOnlyHint` values, curate exact-name allowlists, and complete on-device release APK smoke checks (launch, Play Integrity App Check, Firestore read, WebView Mermaid/LaTeX rendering).

6. **Phase 4 — LanceDB Hybrid RAG + Letta Persistent Memory + Temporal Durable Pipelines**
   - Vector-index completed research stages, Roundtable critiques, and voice notes into **LanceDB**; sync longitudinal memory with **Letta MCP**; delegate 7-stage pipelines to **Temporal MCP** with local `WorkManager` fallback.

7. **Phase 5 — Modular Refactor & Shared Canvas Polish ("Greasing the Gears")**
   - Split `VortexViewModel.kt` into cohesive state holders (`WorkspaceSyncController`, `PipelineController`, `RoundtableController`, `InfraHubController`) and unify `MermaidDiagramViewer` + `SpecializedFormatViewer` onto a shared `InteractiveDiagramSurface`.

---

## 4. "Greased" Workflow Documentation & Overlap Elimination Audit

### End-to-End Zero-Overlap Operational Workflow

```text
[1. CAPTURE & SEARCH]  Voice Note / Dictation / Text Query (4,000-char counter on Self-Hosted)
        │  (Reuses single VortexSpeechController + MetadataTagEngine auto-tagger)
        ▼
[2. UNIFIED CONTEXT]   buildAgentRuntimeContext(stageId) — Stage-Scoped Single Pass
        │  • Authorized .env Policy (Human-Only secrets excluded; raw secrets never leave vault)
        │  • Canonical UTC ↔ Local Time Sync Envelope (Offline-first / MCP-ready)
        │  • Online Callable Allowlisted MCPs for Current Stage ONLY (<= 150 tokens;
        │    PRIVATE_SOLO names anonymized unless shareNameWithAi == true)
        ▼
[3. SYNTHESIS ENGINE]  Stage 0 Triage → 7-Stage Pipeline
        │  • Firebase AI Logic (Play Integrity App Check in release)
        │  • BYOK OpenAI-Compatible Router OR Self-Hosted Text Router (POST /query -> lane + latency)
        │  • Manual Streamable HTTP MCP Tool Calls: exact-name allowlist + dual-condition RO
        │    + [UNTRUSTED_MCP_TOOL_DATA] 2,000-char envelope + local SHA-256 audit log
        ▼
[4. SPECIALIZED STUDIOS] Zero-Friction Rendering, Mapping & Brainstorming
        │  • Mermaid.js (+ PNG/SVG Export + Sticky-Note / Freehand Collaborative Layer)
        │  • LaTeX, Cypher/AGE, PostGIS GeoJSON, NetworkX, D3.js Studio
        │  • PostGIS & PostgreSQL Entity Schema Mapper
        ▼
[5. PERSISTENCE & MIRROR] Tiered Storage & Atomic Sync
           • Room Cache ↔ Firestore (< 256 KB target) + Object Storage (> 256 KB planned)
           • GitHub Atomic Commit + Drive + Android SAF + Keystore Vault (backup-excluded)
```

### Overlap, Resource-Sharing & UX Friction Audit Table

| Overlap / Friction Point | Root Cause | "Greased" Optimization Strategy |
| :--- | :--- | :--- |
| **1. Prompt Context Bloat & Leakage** | Injecting offline or private connectors into every LLM call wastes tokens and leaks private infra names. | **Stage-Scoped `buildAgentRuntimeContext(stageId)` (Shipped):** Emits only enabled + ONLINE + callable + allowlisted MCPs matching the stage, within a 150-token cap, anonymizing `PRIVATE_SOLO` names by default. |
| **2. Noisy Retries While Servers Are Offline** | Background polling against offline servers drains battery and spams errors. | **Standby-First Architecture (Shipped):** Zero background polling; bounded manual **Test Endpoint**, **Check /health**, and **Sync `tools/list`** actions with explicit `AUTH REQUIRED` classification. |
| **3. Non-Idempotent Tool Retries & Prompt Injection** | Auto-retrying mutating MCP calls or feeding raw tool blobs to an LLM risks duplicate side effects and injection. | **Hardened `McpJsonRpcClient` (Shipped):** No batching/auto-retry on non-read calls, dual-condition RO confirmation, `[UNTRUSTED_MCP_TOOL_DATA]` 2,000-char wrapper, local full-result reference store, and SHA-256 audit log. |
| **4. Plaintext Credential Drift** | Storing `EXTERNAL_API_KEY` or `GITHUB_PAT` in DataStore duplicated vault storage without Keystore encryption. | **Verified Keystore Migration (Shipped):** One-time verified read-back migration into `EncryptedEnvVaultStore` before deleting DataStore entries; all secrets live in one AES-256-GCM store. |
| **5. Four Parallel Sync Telemetry Models** | Room/Firestore, Drive, GitHub, and SAF independently compute SHA-256 digests and badges. | Consolidate onto one `WorkspaceDigestCache` + `SyncChannelBadgeState` (Phase 5). |
| **6. Root Modal Proliferation (`MainActivity.kt`)** | Multiple boolean `show*Sheet` flags increase prop drilling. | Consolidate into a single sealed `ActiveWorkspaceModal` state (Phase 5). |
| **7. Duplicate WebView & Export Pipelines** | `MermaidDiagramViewer` and `SpecializedFormatViewer` repeat WebView setup and PNG/SVG export code. | Share one `BaseWebDiagramEngine` + universal `DiagramAnnotationOverlay` (Phase 5). |
| **8. ViewModel & Screen God-Objects** | `VortexViewModel.kt` and `ResearchScreen.kt` have wide recomposition scopes. | Decompose into scoped domain controllers with immutable `@Stable` UI slices (Phase 5). |

---

## 5. Hardening Summary, Collaborator Security Note & Bring-Up Playbook

### Summary of Hardening Corrections Shipped
1. **Secrets at Rest & Backup Lockdown:** `EXTERNAL_API_KEY` and `GITHUB_PAT` migrated via verified one-time `SecretVaultMigration` into `EncryptedEnvVaultStore` (Android Keystore AES-256-GCM). Set `android:allowBackup="false"` and excluded DataStore, vault, MCP audit/result files, and Room DB in `backup_rules.xml` & `data_extraction_rules.xml`. Plaintext `.env` export now requires an explicit unencrypted-export acknowledgment (unchecked by default).
2. **Honest Read-Only Model:** Renamed substring grouping to **Advisory Capability Display** (explicitly marked NOT a security control). Added per-connector **`serverEnforcesReadOnly`** badge. Live MCP calls enforce a deny-by-default exact-name allowlist from `tools/list` and dual-condition read-only classification (`annotations.readOnlyHint == true` **AND** user read-only toggle in the Hub; both values shown per tool).
3. **Accurate Connector Templates:** Corrected World Mechanic ("Stage"), Time Sync (`get_current_time`), Memgraph (7 real tools + server-side `MCP_READ_ONLY` + query allowlist preset), VPS-Access (disabled, locked `PRIVATE_SOLO`, confirm every call), and Browser Use (server-side isolated sandbox only + editable fallback trigger). All expected tool lists are marked *"expected; confirm with `tools/list` when online"*.
4. **Universal HTTPS & Auth-Aware Probing:** Direct per-connector HTTPS URLs are primary (`useGatewayProxy = false`). Any valid HTTPS host (`*.ts.net:<port>`, Cloudflare Access, Neon, S3/R2/MinIO) is allowed; HTTP is loopback-only; cloud metadata IPs are SSRF-blocked; redirects are disabled; `401`/`403`/`3xx` map to **`AUTH REQUIRED`**. Supports Bearer token + custom header vault refs resolved just-in-time.
5. **Stage-Scoped, Budgeted Agent Context:** `buildAuthorizedAgentInfraContext(state, currentStageId)` emits only enabled + `ONLINE` + callable + allowlisted MCPs for the active stage, capped at 150 tokens, with `PRIVATE_SOLO` names anonymized unless `shareNameWithAi == true`.
6. **Self-Hosted Text Router:** `POST <baseUrl>/query` (`{"prompt": "..."}`, hard 4,000-char cap that fails with a clear user-facing error instead of silently truncating + ResearchScreen counter/trim/preview, grounding OFF by default + opt-in summary) and `GET <baseUrl>/health`, surfacing `lane` and `latency` chips on Stage cards and Chat turns.
7. **Release Hardening & Play Integrity App Check:** Enabled R8 minification (`isMinifyEnabled = true`) with explicit ProGuard rules and `res/raw/keep.xml` (`isShrinkResources = false` retained for asset safety until on-device WebView verification). Split App Check into `firebase-appcheck-playintegrity` (release) and `firebase-appcheck-debug` (debug), removing unused `recaptcha_site_key`.
8. **Streamable HTTP JSON-RPC 2.0 MCP Client:** Implemented `initialize` (`Mcp-Session-Id`) followed immediately by the `notifications/initialized` notification (no JSON-RPC `id`, same `Mcp-Session-Id`), `tools/list` (with template mismatch warnings), and `tools/call` (deny-by-default allowlist, dual-condition RO, no batching/no auto-retry on non-read, `[UNTRUSTED_MCP_TOOL_DATA]` 2,000-char cap + local full-result reference store, local SHA-256 audit log).

### Collaborator Security Note — Firebase API Key Restrictions (IMPORTANT)
- The Firebase client configuration files (`firebase-applet-config.json`, `app/src/main/res/values/firebase_applet_config.xml`, `firebase.json`, `firebase-blueprint.json`, `app/google-services.json`) hold public client settings required to connect to Firebase and are **not** private secrets.
- **Required Google Cloud Console Action:** Before distributing any release build, **restrict the Firebase API key in Google Cloud Console** to this app's Android package name (`com.aistudio.vortexresearcher.k9m4px`) and your **release signing-certificate SHA-256 fingerprint**, and enforce **Firebase App Check (Play Integrity)** on Firestore and Firebase AI Logic.

### Collaborator Bring-Up Playbook (When Servers Come Online)
1. **Store Credentials Locally Only:** Add Bearer tokens, custom header secrets, `EXTERNAL_API_KEY`, and `GITHUB_PAT` exclusively in *Dashboard Menu → Secure .env Secrets Vault*. Never commit `.env` exports.
2. **Configure Direct HTTPS Endpoints:** In *Universal DB & MCP Hub*, enter each server's direct HTTPS URL (`https://<machine>.<tailnet>.ts.net:<port>/mcp` for owner Tailscale servers, or your Cloudflare Access sandbox URL for shared sandboxes), bind its Vault Secret Ref, and tap **Test Endpoint**.
3. **Sync `tools/list` & Curate Exact Allowlists:** Once a connector probes `ONLINE`, tap **Sync `tools/list`** to inspect live tools, review any template mismatch warnings, allowlist only required exact tool names, and toggle **User RO** only on tools whose server `annotations.readOnlyHint` is `true` and whose read-only behavior is enforced server-side.
4. **Verify Memgraph Sandbox Server-Side:** On collaborative Memgraph forks, tap **Apply Memgraph Sandboxed Read-Only Preset** and confirm the backend enforces `MCP_READ_ONLY` plus a query allowlist proxy.
5. **Test Self-Hosted Text Router (Optional):** In *Model Router & GitHub Mirror*, choose **Self-Hosted Text Router**, enter the HTTPS base URL, set the Vault Ref, tap **Check /health**, and use **Preview JSON** on ResearchScreen to inspect the exact `{"prompt": "..."}` payload before sending.
6. **Mirror Documentation to GitHub:** Open *Dashboard Menu → Vortex Researcher- Next Steps* and tap **Save to Project & Mirror to GitHub** so the repository root `Vortex Researcher- Next Steps.md` stays in sync.
