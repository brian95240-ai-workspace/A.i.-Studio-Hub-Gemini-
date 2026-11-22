const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_admin_001";
const BOB_UID = "bob_editor_002";
const CHARLIE_UID = "charlie_viewer_003";
const EVE_UID = "eve_outsider_004";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(ownerUid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    ownerId: ownerUid,
    title: "Solid-State Battery Architecture",
    rootFolderName: "solid-state-battery-arch",
    mode: "PROJECT",
    fileFormat: "MD",
    blueprintId: "SYSTEM_ARCHITECTURE_CANONICAL",
    query: "Design a dendrite-resistant solid-state electrolyte stack.",
    complexityScore: 8,
    detectedDomain: "Systems Architecture",
    routingPlanSummary: "Domain: Systems Architecture (8/10) -> gemini-4-argon",
    pipelineStatus: "COMPLETED",
    gapVerdict: "NEEDS_EMULATION_LAYER",
    stagesJson: "[]",
    filesJson: "[]",
    chatAnswer: "Unified empirical synthesis.",
    approved: false,
    latestPrdVersion: "",
    githubRepoUrl: "",
    githubBranch: "main",
    publicShareCode: "",
    publicShareActive: false,
    memberUids: [ownerUid],
    memberRoles: { [ownerUid]: "ADMIN" },
    memberDisplayNames: { [ownerUid]: "Alice" },
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users cannot read/write private projects or list public shares", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("projects").get());
  await assertFails(unauth.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID)));
  await assertFails(unauth.collection("projectInvites").doc("INVITE1234567890").get());
  await assertFails(unauth.collection("publicShares").get());
});

test("Owner can create project, run Kotlin memberUids query, and reject invalid/future payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID))
  );

  // Matches Kotlin VortexFirestoreRepository.observeUserProjects(uid)
  await assertSucceeds(
    aliceDb.collection("projects").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Unfiltered list must fail
  await assertFails(aliceDb.collection("projects").get());

  // Reject @DocumentId `id` key in payload
  await assertFails(
    aliceDb.collection("projects").doc("proj_bad_id").set({
      id: "proj_bad_id",
      ...validProjectPayload(ALICE_UID),
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("projects").doc("proj_future").set(
      validProjectPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );
});

test("Cross-user isolation: non-member cannot read or modify another user's project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_private").set(validProjectPayload(ALICE_UID))
  );

  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(eveDb.collection("projects").doc("proj_private").get());
  await assertFails(
    eveDb.collection("projects").doc("proj_private").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Invite-based RBAC self-join allows Editor/Viewer join and blocks ADMIN self-escalation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_team").set(validProjectPayload(ALICE_UID))
  );

  const inviteCode = "INVITE_EDITOR_123456";
  await assertSucceeds(
    aliceDb.collection("projectInvites").doc(inviteCode).set({
      projectId: "proj_team",
      projectTitle: "Solid-State Battery Architecture",
      assignedRole: "EDITOR",
      createdByUid: ALICE_UID,
      active: true,
      createdAt: new Date(Date.now() - 2000),
    })
  );

  // Eve cannot list invites
  await assertFails(eveDb.collection("projectInvites").get());

  // Bob can fetch specific invite and self-join as EDITOR (mirroring Kotlin joinProjectWithInvite)
  await assertSucceeds(bobDb.collection("projectInvites").doc(inviteCode).get());
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_team").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberRoles: { [ALICE_UID]: "ADMIN", [BOB_UID]: "EDITOR" },
      memberDisplayNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastJoinedInviteCode: inviteCode,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Eve tries to self-escalate to ADMIN using the same invite -> MUST FAIL
  await assertFails(
    eveDb.collection("projects").doc("proj_team").update({
      memberUids: [ALICE_UID, BOB_UID, EVE_UID],
      memberRoles: { [ALICE_UID]: "ADMIN", [BOB_UID]: "EDITOR", [EVE_UID]: "ADMIN" },
      memberDisplayNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [EVE_UID]: "Eve" },
      lastJoinedInviteCode: inviteCode,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("RBAC enforcement across Editor, Viewer, immutable PRDs, and AI Roundtable", async () => {
  const past = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("projects").doc("proj_rbac").set(
      validProjectPayload(ALICE_UID, {
        memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
        memberRoles: {
          [ALICE_UID]: "ADMIN",
          [BOB_UID]: "EDITOR",
          [CHARLIE_UID]: "VIEWER",
        },
        memberDisplayNames: {
          [ALICE_UID]: "Alice",
          [BOB_UID]: "Bob",
          [CHARLIE_UID]: "Charlie",
        },
      })
    );
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Editor (Bob) CAN update project content + roundtableFolders, create PRD, and create/update Roundtable item
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_rbac").update({
      title: "Updated by Editor",
      roundtableFolders: ["Claude", "ChatGPT-o3", "DeepSeek-R1", "Grok-3"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("projects").doc("proj_rbac").update({
      roundtableFolders: "not-a-list",
      updatedAt: new Date(Date.now() - 900),
    })
  );
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_rbac").collection("prds").doc("prd_v1").set({
      projectId: "proj_rbac",
      version: "v1.0",
      majorVersion: 1,
      minorVersion: 0,
      title: "PRD v1.0",
      deltaSummary: "Initial baseline PRD.",
      revisionRequest: "",
      markdownContent: "# PRD v1.0",
      modelUsed: "gemini-3.8-flash",
      authorUid: BOB_UID,
      authorName: "Bob",
      createdAt: past,
    })
  );
  // PRDs are strictly immutable
  await assertFails(
    bobDb.collection("projects").doc("proj_rbac").collection("prds").doc("prd_v1").update({
      title: "Mutated PRD",
    })
  );
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_rbac").collection("roundtableItems").doc("rt_1").set({
      projectId: "proj_rbac",
      modelFolder: "Claude",
      fileName: "solver.rs",
      extension: "rs",
      detectedLanguage: "Rust",
      content: "fn main() {}",
      byteSize: 12,
      queueStatus: "QUEUED",
      scrutinyVerdict: "",
      outboundRelayPrompt: "",
      groundingSourcesJson: "[]",
      scrutinizedByModel: "",
      authorUid: BOB_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Editor (Bob) CANNOT modify memberRoles or delete the project
  await assertFails(
    bobDb.collection("projects").doc("proj_rbac").update({
      memberRoles: { [ALICE_UID]: "ADMIN", [BOB_UID]: "ADMIN", [CHARLIE_UID]: "VIEWER" },
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("projects").doc("proj_rbac").delete());

  // Viewer (Charlie) CAN read project, PRDs, and Roundtable items, but CANNOT write
  await assertSucceeds(charlieDb.collection("projects").doc("proj_rbac").get());
  await assertSucceeds(charlieDb.collection("projects").doc("proj_rbac").collection("prds").get());
  await assertSucceeds(charlieDb.collection("projects").doc("proj_rbac").collection("roundtableItems").get());
  await assertFails(
    charlieDb.collection("projects").doc("proj_rbac").update({
      title: "Viewer edit attempt",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    charlieDb.collection("projects").doc("proj_rbac").collection("roundtableItems").doc("rt_v").set({
      projectId: "proj_rbac",
      modelFolder: "GPT",
      fileName: "note.md",
      extension: "md",
      detectedLanguage: "Markdown",
      content: "Viewer write",
      byteSize: 12,
      queueStatus: "QUEUED",
      scrutinyVerdict: "",
      outboundRelayPrompt: "",
      groundingSourcesJson: "[]",
      scrutinizedByModel: "",
      authorUid: CHARLIE_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Public Read-Only Share allows unauthenticated single-doc get and blocks list/write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const shareCode = "PubShareCode1234567890AB";

  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_pub").set(validProjectPayload(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb.collection("publicShares").doc(shareCode).set({
      projectId: "proj_pub",
      title: "Public Research Demo",
      rootFolderName: "public-demo",
      mode: "PROJECT",
      fileFormat: "MD",
      blueprintId: "SYSTEMS_ADR",
      query: "Public query",
      detectedDomain: "Systems Architecture",
      stagesJson: "[]",
      filesJson: "[]",
      chatAnswer: "Clean summary",
      latestPrdVersion: "v1.0",
      latestPrdMarkdown: "# PRD v1.0",
      prdIndexMarkdown: "| v1.0 | Initial |",
      publishedAt: new Date(Date.now() - 1000),
    })
  );

  // Guest without Google Sign-In CAN read the exact unguessable shareCode
  await assertSucceeds(unauthDb.collection("publicShares").doc(shareCode).get());
  // Guest CANNOT list or overwrite publicShares
  await assertFails(unauthDb.collection("publicShares").get());
  await assertFails(
    unauthDb.collection("publicShares").doc(shareCode).update({ title: "Defaced" })
  );
});
