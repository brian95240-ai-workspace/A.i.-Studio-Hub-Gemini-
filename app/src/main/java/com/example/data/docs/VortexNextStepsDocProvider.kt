package com.example.data.docs

/**
 * Single source of truth for the in-app "Vortex Researcher- Next Steps" collaborator handoff viewer
 * and one-tap root repository / GitHub mirror injection.
 * Maintained in verbatim parity with root `/Vortex Researcher- Next Steps.md`.
 */
data class NextStepsSection(
    val id: String,
    val chipLabel: String,
    val title: String,
    val markdown: String
)

object VortexNextStepsDocProvider {

    const val CANONICAL_DOC_TITLE = "Vortex Researcher- Next Steps"
    const val CANONICAL_FILE_NAME = "Vortex Researcher- Next Steps.md"
    const val CANONICAL_REPO_PATH = "Vortex Researcher- Next Steps.md"

    val sections: List<NextStepsSection> = listOf(
        NextStepsSection(
            id = "architecture",
            chipLabel = "0. Architecture",
            title = "0. System Architecture & Privacy Boundary Overview",
            markdown = """
## 0. System Architecture & Privacy Boundary Overview

### Executive Baseline

| Dimension | Verified Baseline Audit |
| :--- | :--- |
| **Platform** | Android (Kotlin + Jetpack Compose M3, Edge-to-Edge, Adaptive Compact / Expanded) |
| **Codebase Footprint** | ~46,500+ LOC across 74+ Kotlin modules (MVVM + Offline-First Room/Firestore) |
| **Core AI Engine** | Firebase AI Logic (`Firebase.ai`, App Check enforced) + optional BYOK OpenRouter/OpenAI external router |
| **Local Persistence** | Room (`VortexLocalDatabase` v3) + Jetpack DataStore (`LocalPreferencesStore`) + Android Keystore AES-256-GCM (`EncryptedEnvVaultStore`) |
| **Cloud & Sync Channels** | Firestore (`projects`, `prds`, `roundtable`, `public_shares`), Google Drive Mirror, GitHub Git Data API Mirror, Android SAF Local Folder Sync |
| **Visual & Geospatial Studio** | Mermaid.js Interactive Studio (PNG/SVG export + collaborative sticky-note/drawing layer), LaTeX/KaTeX, Cypher/Apache AGE, PostGIS GeoJSON, NetworkX, D3.js, and PostGIS/PostgreSQL Entity Schema Mapper |
| **Standby Infrastructure Layer (`vNext`)** | Universal DB, Object Storage & MCP Connector Hub (3 Storage/DB tiers + 9 MCP templates + Custom BYO CRUD), 3-Tier Privacy Isolation (`PRIVATE_SOLO` / `SANDBOXED_READ_ONLY` / `PUBLIC_TEAM`), Client-Side Read-Only Sandbox Guard, Offline UTC↔Local Time Sync Engine, Extended `.env` Vault Targets, and In-App Handoff Viewer + Root GitHub Mirror |

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
│                     DEVICE-LOCAL ZERO-LEAKAGE BOUNDARY                       │
│  ┌─────────────────────────┐     ┌────────────────────────────────────────┐  │
│  │ Encrypted .env Vault    │────▶│ Universal Connector Registry (Local)   │  │
│  │ (AES-256-GCM Keystore)  │     │ • Per-endpoint Visibility Scope:       │  │
│  │ • Never leaves device   │     │   [PRIVATE_SOLO]        (local only)   │  │
│  │ • Auto-fill DB/MCP keys │     │   [SANDBOXED_READ_ONLY] (safe collab)  │  │
│  └─────────────────────────┘     │   [PUBLIC_TEAM]         (opt-in shared)│  │
│                                  │ • HTTPS/SSRF validator + Standby mode  │  │
│                                  │ • Local UTC↔Local Time Sync fallback   │  │
│                                  └──────────────────┬─────────────────────┘  │
└─────────────────────────────────────────────────────┼────────────────────────┘
                                                      │
              ┌───────────────────────────────────────┴───────────────────────┐
              ▼ (Private Solo · Owner-Only)                                   ▼ (Sandboxed Read-Only / Public Team)
┌───────────────────────────────────────────┐       ┌────────────────────────────────────────────┐
│ USER'S PRIVATE INFRASTRUCTURE (STANDBY)   │       │ COLLABORATIVE / PUBLIC ENDPOINTS           │
│ • Neon PostgreSQL (PostGIS + Apache AGE)  │       │ • Forked Read-Only Sandboxed Memgraph MCP  │
│ • S3 / R2 / MinIO Object Storage          │       │   (Mutations stripped client + server side)│
│ • LanceDB Hybrid Vector Store             │       │ • Any Team-Opted Public DB / MCP Endpoint  │
│ • Private MCPs via User's VPS Gateway:    │       │ • Shared Firestore / GitHub Repo Mirror    │
│   Temporal, Letta, Time Sync, Vertex,     │       │   (Root 'Vortex Researcher- Next Steps.md')│
│   Playwright → Browser Use (fallback),    │       │                                            │
│   VPS-Access Launcher, World Mechanic     │       │                                            │
└───────────────────────────────────────────┘       └────────────────────────────────────────────┘
```

### Core Codebase Module Map (Collaborator Orientation)

| Package / Layer | Key Modules | Responsibility |
| :--- | :--- | :--- |
| `data/ai` | `VortexPipelineOrchestrator`, `VortexModelRouter`, `ExternalRouterApi` | 7-stage empirically grounded synthesis, Stage 0 complexity triage, PostGIS entity extraction, BYOK router fallback |
| `data/infra` | `InfrastructureConnectorModels`, `InfrastructureRegistryEngine`, `TimeSyncContextEngine` | Universal BYO DB/Storage/MCP contracts, 3-tier visibility isolation, Read-Only Sandbox Guard, HTTPS/SSRF probe, offline UTC↔Local clock |
| `data/local` | `LocalPreferencesStore`, `VortexLocalDatabase`, `VortexCacheEntities`, `VortexCacheDaos` | Device-local DataStore registry & preferences, Room v3 offline cache + pending sync queue |
| `data/security` | `EncryptedEnvVaultStore`, `EnvSecretModels` | Hardware-backed AES-256-GCM `.env` vault, per-entry AI permission matrix, discrete auto-fill targets |
| `data/sync` & `data/github` | `OfflineFirstRepository`, `GitHubGitSyncEngine`, `GoogleDriveSyncService`, `SafFolderSyncManager` | Quad-channel replication (Room ↔ Firestore, GitHub atomic commits, Drive mirror, zero-permission SAF) |
| `data/docs` | `VortexNextStepsDocProvider` | Structured in-app handoff document & one-tap root GitHub mirror source |
| `ui/components` | `InfrastructureHubSheet`, `NextStepsHandoffDialog`, `MermaidDiagramViewer`, `SpecializedFormatViewer`, `PostGisSchemaMapperView` | Full-screen Obsidian M3 studios, interactive diagrams, collaborative sticky-note/drawing overlays, schema mapper |
| `ui/screens` & `ui/viewmodel` | `ProjectDashboardScreen`, `ResearchScreen`, `RoundtableScreen`, `PrdStudioScreen`, `VortexViewModel` | Adaptive Compact/Expanded workspaces, voice dictation/search, auto-tagging, unified state orchestration |
            """.trimIndent()
        ),
        NextStepsSection(
            id = "db_gaps",
            chipLabel = "1. DB Gaps",
            title = "1. Core Database & Storage Gap Analysis",
            markdown = """
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
- **Foundational Hole:** Android cannot safely maintain direct libpq TCP connection pools against serverless PostgreSQL without an HTTPS SQL proxy or gateway.
- **Required Support:** Universal **Neon / PostgreSQL HTTPS SQL Proxy Connector** (`db_neon_postgres`, bound to `NEON_DATABASE_URL`) supporting parameterized HTTPS queries, transactional dry-run validation (`BEGIN; ... ROLLBACK;`), and strict `PRIVATE_SOLO` default isolation.

### 1.4 Hybrid Vector & Semantic Search Tier — LanceDB (High)
- **Current State:** `MetadataTagEngine.kt` performs fast in-memory keyword and `#tag` scoring across loaded project titles, tags, and chat turns, but cannot run semantic vector retrieval over long synthesis stages, Roundtable files, or source repositories.
- **Foundational Hole:** As workspaces scale beyond 50+ projects, keyword matching misses conceptually related research across projects.
- **Required Support:** Universal **LanceDB Vector Store Connector** (`vector_lancedb`, bound to `LANCEDB_URL`) for hybrid vector + BM25 retrieval, chunked upsert on pipeline completion, and project-scoped RAG context injection.

### 1.5 Live Graph Execution & Collaborative Sandbox Tier — Memgraph (Medium-High)
- **Current State:** `SpecializedFormatEngine.kt` renders Cypher/AGE/NetworkX graphs locally in WebView, without live Memgraph traversal.
- **Foundational Hole:** Collaborative agents need live graph traversal without risking destructive mutations (`CREATE`, `MERGE`, `DELETE`, `DROP`) against an owner's primary graph database.
- **Required Support:** First-class **`SANDBOXED_READ_ONLY`** mode on **Memgraph MCP** (`mcp_memgraph`), directly modeling a forked read-only Memgraph MCP server with both client-side write/mutation capability stripping and server-side read-only enforcement.

### 1.6 Unified Storage Router Policy (Data Placement Contract)

| Data Payload Class | Canonical Storage Tier | Fallback / Sync Mirror |
| :--- | :--- | :--- |
| Project metadata, team ACLs, lightweight status (< 256 KB) | **Firestore + Room Cache** | Offline queue (`pending_sync_ops`) |
| Large artifacts (> 256 KB: GeoJSON, GraphML, PNG/SVG, audio) | **S3 / R2 / MinIO Object Storage** | Lightweight pointer + SHA-256 in Firestore |
| Extracted entities, PostGIS geometries (`SRID 4326`), relational tables | **Neon PostgreSQL (PostGIS + AGE)** | Versioned `.sql` migrations in GitHub / SAF |
| Embeddings, semantic chunks, voice transcripts, Roundtable notes | **LanceDB Vector Store** | Keyword + `#tag` index in `MetadataTagEngine` |
| Live property graphs & collaborative agentic traversals | **Memgraph MCP (`SANDBOXED_READ_ONLY` fork or `PRIVATE_SOLO`)** | Local Cypher / NetworkX studio export |
| Human-readable docs, PRDs, architecture diagrams | **GitHub Mirror + Google Drive + Android SAF** | Local Room snapshot & Bulk ZIP export |
            """.trimIndent()
        ),
        NextStepsSection(
            id = "silos",
            chipLabel = "2. Silos & MCPs",
            title = "2. Prioritized Silo-by-Silo Breakdown & Universal Infrastructure Matrix",
            markdown = """
## 2. Prioritized Silo-by-Silo Breakdown & Universal Infrastructure Matrix

After Databases & Storage (Silo #1), the remaining six silos are prioritized by architectural leverage and operational risk:

### Priority 2 — MCP Protocol & Universal Agent Tooling Silo (9 Canonical Servers + Custom)
**Weaknesses / Voids Identified:**
- Prior to this release, Vortex Researcher had zero MCP JSON-RPC 2.0 / Streamable HTTP transport contracts, zero standby state management, and no universal registry for user-supplied endpoints.
- **Canonical 9-Server Standby Contract** (all default to `STANDBY` + `PRIVATE_SOLO`, zero hardcoded personal hosts):

| # | MCP Server Template | Primary Role in Vortex Researcher | Standby / Privacy / Fallback Contract |
| :--- | :--- | :--- | :--- |
| **1** | **Temporal MCP** | Durable workflow orchestration for 7-stage pipelines, retries, and background sync | Moves execution out of fragile foreground `viewModelScope` |
| **2** | **Letta MCP** | Persistent hierarchical core + archival agent memory across sessions & Roundtable | Project-scoped memory partitions; prevents cross-tenant leakage |
| **3** | **Memgraph MCP** | Live Cypher property graph queries & ontology traversal | Includes 1-tap **`SANDBOXED_READ_ONLY` Fork** preset stripping all write/delete mutations |
| **4** | **Time & Date Sync MCP** | Translates canonical UTC timestamps into user's current local wall-clock timezone | **Local offline engine active immediately**; delegates seamlessly when remote MCP is online |
| **5** | **Vertex Solver MCP** | Mathematical optimization, graph routing & formal constraint verification | Read-only solver invocation over extracted PostGIS/graph payloads |
| **6** | **Playwright MCP** | **Primary** deterministic headless browser automation & structured DOM capture | Read-only web extraction & citation verification |
| **7** | **Browser Use MCP** | **Explicit Fallback immediately after Playwright MCP** (`fallbackAfterId = mcp_playwright`) | Vision-guided agentic browser fallback when SPAs or dynamic layouts block deterministic Playwright selectors |
| **8** | **VPS-Access Launcher MCP** | Remote VPS container health, startup & service control (e.g., Contabo or any BYO VPS) | Strictly **`PRIVATE_SOLO`**; never shared in team or public manifests |
| **9** | **World Mechanic MCP** | Cross-system optimization, process-overlap detection & resource tuning | Consumes non-secret telemetry to recommend pipeline & cache tuning |
| **+** | **Custom DB / Custom MCP** | Universal BYO extensibility for external users' own infrastructure | Full CRUD inside the Hub with HTTPS/SSRF validation & 3-tier visibility scope |

### Priority 3 — Durable Orchestration & Background Execution Silo (`VortexPipelineOrchestrator`)
- **Weaknesses / Voids:**
  - The 7-stage pipeline (`878 LOC`) executes inside `viewModelScope`; OS backgrounding halts in-flight passes until manually retried.
  - Stage prompts previously lacked a canonical UTC↔Local temporal anchor.
- **Resolution Path:**
  - *Shipped Now:* Injects canonical `TimeSyncContextEngine` UTC↔Local header + authorized non-secret infra capabilities via `buildAgentRuntimeContext()`.
  - *Next Phase:* Bind `WorkManager` local checkpointing and delegate long-running workflows to **Temporal MCP** when online.

### Priority 4 — Sync & Replication Fabric Silo (Room + Firestore + Drive + GitHub + SAF)
- **Weaknesses / Voids:**
  - Four separate sync telemetry structures (`OfflineSyncTelemetry`, `DriveHeaderSyncState`, `GitHubHeaderSyncState`, `SafFolderBindings`) duplicate SHA-256 hashing and badge computation.
  - Conflict resolution (`SyncConflictResolutionDialog`) covers Room ↔ Firestore only, not Drive/GitHub/SAF drift.
- **Resolution Path:** Consolidate behind a single `WorkspaceDigestCache` and unified `SyncChannelBadgeState` so one SHA-256 digest serves all channels atomically.

### Priority 5 — Visualization, Authoring & Spatial/Graph Studio Silo
- **Weaknesses / Voids:**
  - `MermaidDiagramEngine.kt` + `MermaidDiagramViewer.kt` and `SpecializedFormatEngine.kt` + `SpecializedFormatViewer.kt` duplicate WebView lifecycle, theme toggling, and PNG/SVG export logic.
  - Collaborative sticky-note & drawing overlay (`MermaidAnnotationOverlay.kt`) is currently Mermaid-only.
- **Resolution Path:** Extract a shared `InteractiveDiagramSurface` and universal `DiagramAnnotationOverlay` spanning Mermaid, LaTeX, Cypher, GeoJSON, NetworkX, and D3.js.

### Priority 6 — Voice Ingestion, Auto-Tagging & Search Silo
- **Weaknesses / Voids:**
  - Voice note transcription, auto-categorization (`Draft`, `Action Item`, `Research Query`), and voice dashboard search reuse `VortexSpeechController` and `MetadataTagEngine` cleanly, but search currently operates only over in-memory loaded Firestore projects.
- **Resolution Path:** Upsert voice-note transcripts and extracted metadata tags into **LanceDB** on save so voice search queries both local tags and semantic vector similarity simultaneously.

### Priority 7 — Security, Privacy & Multi-User Isolation Silo
- **Weaknesses / Voids (Resolved in `vNext`):**
  - Previously, `EnvAutoFillTarget` mapped only GitHub and OpenRouter keys—leaving database, storage, VPS gateway, and MCP tokens unmapped.
  - No explicit isolation existed between private personal endpoints and shareable read-only collaborative forks.
- **Shipped Resolution:**
  - Extended `EnvAutoFillTarget` & `EncryptedEnvVaultStore` for `NEON_DATABASE_URL`, `OBJECT_STORAGE_ENDPOINT`, `LANCEDB_URL`, `VPS_GATEWAY_URL`, and `MCP_AUTH_TOKEN`.
  - Enforced 3-tier visibility (`PRIVATE_SOLO`, `SANDBOXED_READ_ONLY`, `PUBLIC_TEAM`) + HTTPS/SSRF endpoint validation + zero-leakage shareable manifest exporter.
            """.trimIndent()
        ),
        NextStepsSection(
            id = "roadmap",
            chipLabel = "3. Roadmap",
            title = "3. Cascading & Compounding Infrastructure Roadmap",
            markdown = """
## 3. Cascading & Compounding Infrastructure Roadmap

Ordered strictly so each completed phase **unlocks and compounds** the next with zero rework:

1. **[SHIPPED IN THIS VERSION] Phase 0 — Universal Standby Registry, Privacy Isolation & Time Sync Foundation**
   - Shipped `InfrastructureConnectorModels.kt`, `InfrastructureRegistryEngine.kt`, `TimeSyncContextEngine`, device-local DataStore persistence, extended `.env` Vault targets, 3-tab `InfrastructureHubSheet.kt`, and `NextStepsHandoffDialog.kt` (with one-tap Root GitHub Mirror).
   - *Compounding Unlock:* Enables safe public/collaborator app sharing with zero credential leakage, offline UTC↔Local temporal grounding, and instant readiness when servers return online.

2. **Phase 1 — Room Safe Migrations & Firestore Payload Guard**
   - Set `exportSchema = true` on `VortexLocalDatabase`, ship explicit `Migration(3, 4)`, snapshot `pending_sync_ops` before upgrades, and enforce a 256 KB pre-write payload guard before Firestore writes.
   - *Compounding Unlock:* Eliminates offline data loss and creates clean blob-pointer columns for Object Storage.

3. **Phase 2 — Object Storage Blob Offload + Neon PostgreSQL HTTPS SQL Adapter**
   - Activate presigned S3/R2 upload/download for artifacts > 256 KB and wire `PostGisSchemaMapperView` directly to the Neon HTTPS SQL proxy with `BEGIN; ... ROLLBACK;` dry-run validation.
   - *Compounding Unlock:* Permanently removes Firestore's 1 MB ceiling and turns PostGIS/AGE schema mapping into 1-tap live database execution.

4. **Phase 3 — Unified MCP JSON-RPC 2.0 / Streamable HTTP Client + Browser Fallback Chain**
   - Implement shared `McpJsonRpcClient` (`tools/list`, `tools/call`) injecting `Authorization: Bearer` just-in-time from `EncryptedEnvVaultStore`.
   - Activate automatic **Playwright MCP → Browser Use MCP** fallback chain when deterministic selectors fail or return empty DOM payloads.
   - Wire live **Memgraph MCP** (enforcing `SANDBOXED_READ_ONLY` guard) and **Time & Date Sync MCP** remote delegation.
   - *Compounding Unlock:* One compact transport activates all 9 MCP templates plus every user-added custom MCP server.

5. **Phase 4 — LanceDB Hybrid RAG + Letta Persistent Memory + Temporal Durable Pipelines**
   - Vector-index completed research stages, Roundtable critiques, and voice notes into **LanceDB**; sync longitudinal memory with **Letta MCP**; delegate 7-stage pipelines to **Temporal MCP** with local `WorkManager` fallback.
   - *Compounding Unlock:* Elevates Vortex Researcher from single-session research into durable, cross-project institutional memory.

6. **Phase 5 — Modular Refactor & Shared Canvas Polish ("Greasing the Gears")**
   - Split `VortexViewModel.kt` (~3,750 LOC) into 4 cohesive state holders (`WorkspaceSyncController`, `PipelineController`, `RoundtableController`, `InfraHubController`).
   - Unify `MermaidDiagramViewer` and `SpecializedFormatViewer` onto a shared `InteractiveDiagramSurface` + universal `DiagramAnnotationOverlay`.
   - Connect **Vertex Solver MCP**, **VPS-Access Launcher MCP**, and **World Mechanic MCP** optimization hooks.
            """.trimIndent()
        ),
        NextStepsSection(
            id = "greasing",
            chipLabel = "4. Greased Flow",
            title = "4. Greased Workflow Documentation & Overlap Elimination Audit",
            markdown = """
## 4. "Greased" Workflow Documentation & Overlap Elimination Audit

### End-to-End Zero-Overlap Operational Workflow

```text
[1. CAPTURE & SEARCH]  Voice Note / Dictation / Text Query
        │  (Reuses single VortexSpeechController + MetadataTagEngine auto-tagger)
        ▼
[2. UNIFIED CONTEXT]   buildAgentRuntimeContext() — Single Pass Assembler
        │  • Authorized .env Policy (Human-Only secrets excluded)
        │  • Canonical UTC ↔ Local Time Sync Envelope (Offline-first / MCP-ready)
        │  • Non-Secret Infra & MCP Capabilities (PRIVATE_SOLO URLs redacted)
        ▼
[3. SYNTHESIS ENGINE]  Stage 0 Triage → 7-Stage Grounded Pipeline (Firebase AI / BYOK)
        │  • Web Extraction Chain: Playwright MCP (Primary) → Browser Use MCP (Fallback)
        │  • Graph Reasoning: Memgraph MCP (Read-Only Sandbox Guard enforced on forks)
        │  • Future Offload: Temporal MCP durable execution + Letta archival memory
        ▼
[4. SPECIALIZED STUDIOS] Zero-Friction Rendering, Mapping & Brainstorming
        │  • Mermaid.js (+ PNG/SVG Export + Sticky-Note / Freehand Collaborative Layer)
        │  • LaTeX, Cypher/AGE, PostGIS GeoJSON, NetworkX, D3.js Studio
        │  • PostGIS & PostgreSQL Entity Schema Mapper → Neon HTTPS SQL Proxy
        ▼
[5. PERSISTENCE & MIRROR] Tiered Storage & Atomic Sync
           • Room Cache ↔ Firestore (< 256 KB) + Object Storage (> 256 KB blobs)
           • LanceDB Semantic Index + GitHub Atomic Commit + Drive + Android SAF
```

### Overlap, Resource-Sharing & UX Friction Audit Table

| Overlap / Friction Point | Root Cause | "Greased" Optimization Strategy (Zero Quality Compromise) |
| :--- | :--- | :--- |
| **1. Prompt Context Assembly** | Pipeline stages, Roundtable, and PRD synthesis assembled system headers separately. | **Unified `buildAgentRuntimeContext()` (Shipped Now):** Single pass merges Authorized `.env` policy + UTC↔Local Time Sync envelope + non-secret Infra/MCP capabilities once per invocation. |
| **2. Noisy Retries While Servers Are Offline** | Background polling against offline servers drains battery and spams error banners. | **Standby-First Architecture (Shipped Now):** Zero automatic background polling; manual 4-second bounded **Test Endpoint** probe with clean `STANDBY` status pills. |
| **3. Duplicate Browser Automation Triggers** | Switching manually when dynamic SPAs break deterministic Playwright scripts adds UX friction. | **Declarative Fallback Chain (Shipped Now):** `Browser Use MCP` links directly behind `Playwright MCP` (`fallbackAfterId = mcp_playwright`) as one resilient browsing capability. |
| **4. Shared Speech & Auto-Tagger Pipeline** | Voice dictation, voice notes, and dashboard voice search risked maintaining separate recognizers and taggers. | **Single Shared Controller (Active):** One `VortexSpeechController` + `MetadataTagEngine` powers voice search, note categorization (`Draft`, `Action Item`, `Research Query`), and project/chat indexing. |
| **5. Four Parallel Sync Telemetry Models** | Room/Firestore, Drive, GitHub, and SAF independently compute SHA-256 digests and badges. | Consolidate onto one `WorkspaceDigestCache` + `SyncChannelBadgeState` so edits hash once and update all channels atomically. |
| **6. Root Modal Proliferation (`MainActivity.kt`)** | 8+ boolean `show*Sheet` flags increase prop drilling and risk overlapping sheets. | Consolidate into a single sealed `ActiveWorkspaceModal` state guaranteeing flat 1-modal-at-a-time navigation depth. |
| **7. Duplicate WebView & Export Pipelines** | `MermaidDiagramViewer` and `SpecializedFormatViewer` repeat WebView setup and PNG/SVG export code. | Share one `BaseWebDiagramEngine` + universal `DiagramAnnotationOverlay` across Mermaid, LaTeX, Cypher, GeoJSON, NetworkX, and D3.js. |
| **8. ViewModel & Screen God-Objects** | `VortexViewModel.kt` and `ResearchScreen.kt` create wide recomposition scopes. | Decompose into scoped domain controllers with immutable `@Stable` UI slices. |
            """.trimIndent()
        ),
        NextStepsSection(
            id = "deltas",
            chipLabel = "5. Prod & Deltas",
            title = "5. Production Optimization Guarantee, Version Deltas & Collaborator Quickstart",
            markdown = """
## 5. Production Optimization Guarantee, Version Deltas & Collaborator Quickstart

### How This Architecture Hardens Vortex Researcher for Production
1. **Zero-Leakage Multi-User Isolation:** `PRIVATE_SOLO` endpoints stay strictly in device-local DataStore; credentials stay in Android Keystore AES-256-GCM (`EncryptedEnvVaultStore`). Public sharing and team collaboration never leak personal infrastructure or tokens.
2. **Defense-in-Depth Agent Sandboxing:** Collaborative endpoints (such as a forked read-only Memgraph MCP) enforce client-side write/mutation capability stripping (`write`, `create`, `merge`, `delete`, `drop`, `insert`, `update`, `launch`, `restart`, `terminate`) alongside server-side read-only permissions.
3. **Deterministic Temporal Accuracy:** Canonical UTC + User Local Timezone injection prevents agent date/timezone drift even while remote MCP servers are offline.
4. **Transport & SSRF Hygiene:** Endpoint validator enforces `https://` for remote hosts and blocks cloud metadata SSRF addresses (`169.254.169.254`, `metadata.google.internal`, `100.100.100.200`).

### Concrete Deltas Shipped in This Version (`vNext`)
- **Universal Standby Infrastructure & MCP Registry (`InfrastructureConnectorModels.kt`, `InfrastructureRegistryEngine.kt`):**
  - 3 universal storage/DB templates (**Neon/PostgreSQL + PostGIS/AGE**, **S3/R2/MinIO Object Storage**, **LanceDB Vector Store**)
  - 9 universal MCP server templates (**Temporal**, **Letta**, **Memgraph** with 1-tap Sandboxed Read-Only Fork preset, **Time & Date Sync**, **Vertex Solver**, **Playwright**, **Browser Use** as explicit fallback after Playwright, **VPS-Access Launcher**, **World Mechanic**)
  - Full **+ Add Custom DB/Storage** and **+ Add Custom MCP Server** CRUD
  - 3-tier visibility scope (`PRIVATE_SOLO`, `SANDBOXED_READ_ONLY`, `PUBLIC_TEAM`), Read-Only Sandbox Guard, HTTPS/SSRF validator, manual 4s bounded probe, and zero-leakage shareable manifest exporter
- **Offline-Capable UTC ↔ Local Time Sync Engine (`TimeSyncContextEngine`):** Live Hub preview + automatic 7-stage pipeline prompt injection via `buildAgentRuntimeContext()`
- **Extended Encrypted `.env` Vault (`EnvSecretModels.kt`, `EncryptedEnvVaultStore.kt`):** Added auto-detection and discrete auto-fill targets for `NEON_DATABASE_URL`, `OBJECT_STORAGE_ENDPOINT`, `LANCEDB_URL`, `VPS_GATEWAY_URL`, and `MCP_AUTH_TOKEN`
- **Unified 3-Tab `InfrastructureHubSheet` & Interactive `NextStepsHandoffDialog`:** Accessible from the Dashboard Command Menu with section jump chips, Markdown preview, Copy, SAF `.md` export, and one-tap **Save to Project & Mirror to GitHub** targeting the root `Vortex Researcher- Next Steps.md`

### Collaborator Quickstart — Bringing Servers Online & Mirroring to GitHub
1. **Keep Personal Infra Private:** Leave personal DBs and MCP servers on **`PRIVATE SOLO`** in *Dashboard Menu → Universal DB & MCP Hub*. Store all tokens exclusively in *Encrypted .env Secrets Vault*.
2. **Enable Collaborative Read-Only Fork:** On *Memgraph MCP*, tap **Apply Sandboxed Read-Only Fork Preset** to lock `SANDBOXED_READ_ONLY` + `readOnlyEnforced = true`, enter the shared read-only HTTPS endpoint, and tap **Test Endpoint**.
3. **Activate Gateway or Direct Endpoints:** In Tab 3 (*Time & Privacy*), set your optional HTTPS Gateway Base URL (or configure direct per-connector HTTPS URLs) and tap **Test Endpoint** to transition cleanly from `STANDBY` → `ONLINE`.
4. **Mirror Documentation to GitHub:** Open *Dashboard Menu → Vortex Researcher- Next Steps* and tap **Save to Project & Mirror to GitHub** (or push the root `Vortex Researcher- Next Steps.md` directly via AI Studio's GitHub integration).
            """.trimIndent()
        )
    )

    fun fullMarkdown(): String = buildString {
        appendLine("# $CANONICAL_DOC_TITLE")
        appendLine()
        appendLine("> **Document Purpose:** Canonical root-level systems architecture blueprint, database & infrastructure gap analysis, prioritized silo breakdown, cascading roadmap, \"greased\" end-to-end workflow documentation, and collaborator onboarding playbook for **Vortex Researcher**.")
        appendLine(">")
        appendLine("> **Scope & Privacy Contract:** Vortex Researcher is a **shared universal Android research platform**. Every database, object storage bucket, vector store, and MCP server endpoint is strictly **Bring-Your-Own-Infrastructure (BYO-Infra)** with a 3-tier visibility boundary (`PRIVATE_SOLO`, `SANDBOXED_READ_ONLY`, `PUBLIC_TEAM`). No personal hostnames, IPs, credentials, or private infrastructure are hardcoded anywhere in this codebase.")
        appendLine()
        appendLine("---")
        appendLine()
        sections.forEachIndexed { index, section ->
            appendLine(section.markdown)
            if (index < sections.lastIndex) {
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }.trim()
}
