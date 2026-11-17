const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nova_1";
const BOB_UID = "bob_nova_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across all NOVA collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/todos`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/preferences`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/messages`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      title: "Secret",
      content: "Body",
      tag: "General",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: notes CRUD and ghost-field / immutable guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").set({
      userId: ALICE_UID,
      title: "Project Ideas",
      content: "Build bilingual voice workflows",
      tag: "Work",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List subcollection (mirrors Kotlin observeNotes)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).get());

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      title: "Updated Ideas",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost @DocumentId 'id' key on create
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_2").set({
      id: "note_2",
      userId: ALICE_UID,
      title: "Bad Note",
      content: "Has id key",
      tag: "General",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).doc("note_1").delete());
});

test("Authenticated owner: todos, reminders, preferences, and messages parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Todo create + toggle completed
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/todos`).doc("todo_1").set({
      userId: ALICE_UID,
      title: "Buy groceries",
      completed: false,
      priority: "HIGH",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/todos`).doc("todo_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Reject invalid priority enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/todos`).doc("todo_2").set({
      userId: ALICE_UID,
      title: "Invalid priority",
      completed: false,
      priority: "URGENT",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reminder create + deactivate
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").set({
      userId: ALICE_UID,
      title: "Call Mom",
      dueTimeMillis: Date.now() + 3600000,
      active: true,
      category: "Family",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Preference requires approvedByUser == true
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("pref_1").set({
      userId: ALICE_UID,
      prefKey: "Home City",
      prefValue: "New Delhi",
      approvedByUser: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("pref_2").set({
      userId: ALICE_UID,
      prefKey: "Unapproved",
      prefValue: "Secret",
      approvedByUser: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Message create + list + update denied
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/messages`).doc("msg_1").set({
      userId: ALICE_UID,
      sender: "USER",
      text: "Hey NOVA, मेरा फोन बैटरी कितना है?",
      language: "hinglish",
      actionTag: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/messages`).orderBy("createdAt", "asc").get()
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/messages`).doc("msg_1").update({
      text: "Tampered",
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const past = new Date(Date.now() - 5000);
    await ctx.firestore().collection(`users/${ALICE_UID}/notes`).doc("alice_note").set({
      userId: ALICE_UID,
      title: "Private",
      content: "Alice only",
      tag: "Private",
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).doc("alice_note").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/notes`).doc("alice_note").update({ title: "Hijack" })
  );
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/todos`).doc("bob_inject").set({
      userId: BOB_UID,
      title: "Cross-tenant write",
      completed: false,
      priority: "NORMAL",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Future timestamp tampering and oversized strings rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const future = new Date(Date.now() + 120000);
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("future_note").set({
      userId: ALICE_UID,
      title: "Future",
      content: "Bad timestamp",
      tag: "Test",
      createdAt: future,
      updatedAt: future,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/notes`).doc("huge_title").set({
      userId: ALICE_UID,
      title: "x".repeat(200),
      content: "Too long title",
      tag: "Test",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
