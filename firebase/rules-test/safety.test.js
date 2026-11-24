// Checks the report and block rules in ../firestore.rules on the Firestore emulator. Run with `npm test`.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';

// SEMESTER_END in Group.kt: the end of Dec 19, 2026 in Boston.
const SEMESTER_END = Timestamp.fromMillis(1797742800000);

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-reconvene',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

// maya hosts, ana and ben are in, cal is invited. ben wrote m1 and ana wrote m2.
beforeEach(async () => {
  await env.clearFirestore();
  await seed('groups/g1', {
    name: 'Algorithms review',
    course: 'CS 330',
    fromPodId: 'g1',
    hostUid: 'maya',
    keptByUid: 'maya',
    members: { maya: 'Maya', ana: 'Ana', ben: 'Ben' },
    invited: { cal: 'Cal' },
    access: ['ana', 'ben', 'cal', 'maya'],
    handover: null,
    archivesAt: SEMESTER_END,
    disbanded: false,
  });
  await seed('groups/g1/messages/m1', { authorUid: 'ben', authorName: 'Ben', text: 'Buy my answers', sentAt: Timestamp.now() });
  await seed('groups/g1/messages/m2', { authorUid: 'ana', authorName: 'Ana', text: 'Hi', sentAt: Timestamp.now() });
});

// A signed-in, verified BU user.
const as = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@bu.edu`, email_verified: true }).firestore();

async function seed(path, data) {
  await env.withSecurityRulesDisabled((context) => setDoc(doc(context.firestore(), path), data));
}

// What the app writes when uid reports a message (MessageReport.toNewFirestoreReport).
function report(uid, overrides = {}) {
  return {
    groupId: 'g1',
    messageId: 'm1',
    reporterUid: uid,
    targetUid: 'ben',
    reason: 'ACADEMIC_DISHONESTY',
    createdAt: serverTimestamp(),
    ...overrides,
  };
}

const file = (uid, data, id = `${data.messageId}_${uid}`) => setDoc(doc(as(uid), `reports/${id}`), data);

describe('reporting a message', () => {
  test('a member reports another member\'s message', async () => {
    await assertSucceeds(file('ana', report('ana')));
    await assertSucceeds(file('maya', report('maya', { reason: 'SPAM' })));
  });

  test('invited users and everyone else cannot', async () => {
    await assertFails(file('cal', report('cal')));
    await assertFails(file('dan', report('dan')));
  });

  test('not as someone else, or under another id', async () => {
    await assertFails(file('ana', report('maya'), 'm1_maya'));
    await assertFails(file('ana', report('ana'), 'm1_other'));
  });

  test('not their own message, or blaming someone who didn\'t write it', async () => {
    await assertFails(file('ana', report('ana', { messageId: 'm2', targetUid: 'ana' })));
    await assertFails(file('maya', report('maya', { messageId: 'm2', targetUid: 'ben' })));
    await assertFails(file('ana', report('ana', { messageId: 'nope' })));
  });

  test('only with a known reason, the server\'s time and no extra fields', async () => {
    await assertFails(file('ana', report('ana', { reason: 'BORING' })));
    await assertFails(file('ana', report('ana', { createdAt: Timestamp.now() })));
    await assertFails(file('ana', report('ana', { note: 'see screenshot' })));
  });

  test('once, and never changed or deleted', async () => {
    await assertSucceeds(file('ana', report('ana')));
    await assertFails(file('ana', report('ana', { reason: 'SPAM' })));
    await assertFails(updateDoc(doc(as('ana'), 'reports/m1_ana'), { reason: 'SPAM' }));
    await assertFails(deleteDoc(doc(as('ana'), 'reports/m1_ana')));
  });

  test('the reporter can read their own; the person reported and others cannot', async () => {
    await seed('reports/m1_ana', report('ana', { createdAt: Timestamp.now() }));
    await assertSucceeds(getDoc(doc(as('ana'), 'reports/m1_ana')));
    await assertFails(getDoc(doc(as('ben'), 'reports/m1_ana')));
    await assertFails(getDoc(doc(as('maya'), 'reports/m1_ana')));
    await assertFails(getDocs(collection(as('ben'), 'reports')));
  });

  test('anyone can check that a report does not exist yet', async () => {
    await assertSucceeds(getDoc(doc(as('ana'), 'reports/m1_ana')));
  });
});

describe('blocking', () => {
  const block = (uid, blockedUid, data = { name: 'Ben', blockedAt: serverTimestamp() }) =>
    setDoc(doc(as(uid), `users/${uid}/blocked/${blockedUid}`), data);

  test('a user blocks someone, again, and unblocks them', async () => {
    await assertSucceeds(block('ana', 'ben'));
    await assertSucceeds(block('ana', 'ben'));
    await assertSucceeds(deleteDoc(doc(as('ana'), 'users/ana/blocked/ben')));
  });

  test('only in their own list, and not themselves', async () => {
    await assertFails(setDoc(doc(as('ana'), 'users/maya/blocked/ben'), { name: 'Ben', blockedAt: serverTimestamp() }));
    await assertFails(block('ana', 'ana', { name: 'Ana', blockedAt: serverTimestamp() }));
  });

  test('with a name, the server\'s time and nothing else', async () => {
    await assertFails(block('ana', 'ben', { name: '', blockedAt: serverTimestamp() }));
    await assertFails(block('ana', 'ben', { name: 'B'.repeat(41), blockedAt: serverTimestamp() }));
    await assertFails(block('ana', 'ben', { name: 'Ben', blockedAt: Timestamp.now() }));
    await assertFails(block('ana', 'ben', { name: 'Ben', blockedAt: serverTimestamp(), why: 'rude' }));
    await assertFails(block('ana', 'ben', { blockedAt: serverTimestamp() }));
  });

  test('only the user reads their list, so the blocked person is never told', async () => {
    await seed('users/ana/blocked/ben', { name: 'Ben', blockedAt: Timestamp.now() });
    await assertSucceeds(getDocs(collection(as('ana'), 'users/ana/blocked')));
    await assertFails(getDocs(collection(as('ben'), 'users/ana/blocked')));
    await assertFails(getDoc(doc(as('ben'), 'users/ana/blocked/ben')));
    await assertFails(deleteDoc(doc(as('ben'), 'users/ana/blocked/ben')));
  });
});
