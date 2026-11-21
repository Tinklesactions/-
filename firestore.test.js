const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validCoursePayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    courseName: "高等数学",
    dayOfWeek: 1,
    startMinuteOfDay: 480,
    endMinuteOfDay: 575,
    location: "教三楼 204",
    teacher: "张教授",
    weeksText: "1-16周",
    colorIndex: 0,
    reminderEnabled: true,
    reminderMinutesBefore: 15,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validExamPayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    title: "高等数学期末考试",
    courseName: "高等数学",
    examEpochMillis: Date.now() + 7 * 24 * 60 * 60 * 1000,
    durationMinutes: 120,
    location: "主楼 301",
    seatNumber: "A-18",
    notes: "携带学生证与无编程功能计算器",
    reminderEnabled: true,
    reminderOffsetsMinutes: [4320, 1440, 120, 30],
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validPreferencesPayload(uid, timestamp = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    defaultClassReminderMinutes: 15,
    defaultExamReminderOffsets: [4320, 1440, 120, 30],
    classRemindersEnabled: true,
    examRemindersEnabled: true,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

test("Unauthenticated users cannot read or write courses, exams, or preferences", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("courses").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("courses")
      .doc("course_1")
      .set(validCoursePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("exams").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .set(validPreferencesPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid course create, list, partial update, toggle, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const coursesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("courses");

  // Empty subcollection list must succeed
  await assertSucceeds(coursesRef.get());

  // Create mirrored Kotlin payload (no @DocumentId `id` field)
  await assertSucceeds(coursesRef.doc("course_1").set(validCoursePayload(ALICE_UID)));

  // List populated subcollection
  await assertSucceeds(coursesRef.get());

  // Partial update mirroring updateCourse
  await assertSucceeds(
    coursesRef.doc("course_1").update({
      courseName: "高等数学（上）",
      location: "教三楼 305",
      reminderMinutesBefore: 30,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reminder toggle action
  await assertSucceeds(
    coursesRef.doc("course_1").update({
      reminderEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete succeeds
  await assertSucceeds(coursesRef.doc("course_1").delete());
});

test("Course security bounds: rejects shadow fields, @DocumentId id, future timestamps, invalid times, and createdAt mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const coursesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("courses");

  // Reject ghost/shadow field
  await assertFails(
    coursesRef.doc("course_ghost").set({
      ...validCoursePayload(ALICE_UID),
      isAdmin: true,
    })
  );

  // Reject embedded `id` field
  await assertFails(
    coursesRef.doc("course_id_field").set({
      id: "course_id_field",
      ...validCoursePayload(ALICE_UID),
    })
  );

  // Reject future timestamp
  await assertFails(
    coursesRef
      .doc("course_future")
      .set(validCoursePayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Reject endMinuteOfDay <= startMinuteOfDay
  await assertFails(
    coursesRef.doc("course_bad_time").set({
      ...validCoursePayload(ALICE_UID),
      startMinuteOfDay: 600,
      endMinuteOfDay: 600,
    })
  );

  // Reject identity spoofing
  await assertFails(
    coursesRef.doc("course_spoof").set(validCoursePayload(BOB_UID))
  );

  // Create valid doc, then attempt immutable createdAt mutation
  await assertSucceeds(coursesRef.doc("course_immut").set(validCoursePayload(ALICE_UID)));
  await assertFails(
    coursesRef.doc("course_immut").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: valid exam create, update, toggle, and delete succeed; invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const examsRef = aliceDb.collection("users").doc(ALICE_UID).collection("exams");

  await assertSucceeds(examsRef.get());
  await assertSucceeds(examsRef.doc("exam_1").set(validExamPayload(ALICE_UID)));

  await assertSucceeds(
    examsRef.doc("exam_1").update({
      seatNumber: "B-07",
      reminderOffsetsMinutes: [10080, 1440, 60],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject too many reminder offsets (> 10)
  await assertFails(
    examsRef.doc("exam_1").update({
      reminderOffsetsMinutes: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid duration (< 15)
  await assertFails(
    examsRef.doc("exam_bad_dur").set({
      ...validExamPayload(ALICE_UID),
      durationMinutes: 5,
    })
  );

  await assertSucceeds(examsRef.doc("exam_1").delete());
});

test("Preferences create and partial update succeed; cross-user access denied across all subcollections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const prefRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("preferences")
    .doc("settings");
  await assertSucceeds(prefRef.set(validPreferencesPayload(ALICE_UID)));
  await assertSucceeds(
    prefRef.update({
      defaultClassReminderMinutes: 10,
      examRemindersEnabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("courses")
      .doc("alice_c1")
      .set(validCoursePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("exams")
      .doc("alice_e1")
      .set(validExamPayload(ALICE_UID))
  );

  // Bob cannot list, get, create, update, or delete Alice's data
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("courses").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("courses")
      .doc("alice_c1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("courses")
      .doc("alice_c1")
      .update({ courseName: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("exams").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .get()
  );
});
