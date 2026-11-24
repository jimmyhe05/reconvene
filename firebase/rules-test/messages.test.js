// Checks the group chat rules in ../firestore.rules on the Firestore emulator. Run with `npm test`.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  orderBy,
  query,
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

beforeEach(async () => {
  await env.clearFirestore();
  await seed('groups/g1', group());
});

// A signed-in, verified BU user.
const as = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@bu.edu`, email_verified: true }).firestore();

// maya hosts, ana is in, ben is invited, cal is someone else.
function group(overrides = {}) {
  return {
    name: 'Algorithms review',
    course: 'CS 330',
    fromPodId: 'g1',
    hostUid: 'maya',
    keptByUid: 'maya',
    members: { maya: 'Maya', ana: 'Ana' },
    invited: { ben: 'Ben' },
    access: ['ana', 'ben', 'maya'],
    handover: null,
    archivesAt: SEMESTER_END,
    disbanded: false,
    ...overrides,
  };
}

// What the app writes for a new message (GroupMessage.toNewFirestoreMessage).
function message(overrides = {}) {
  return { authorUid: 'ana', authorName: 'Ana', text: 'Same time next week?', sentAt: serverTimestamp(), ...overrides };
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled((context) => setDoc(doc(context.firestore(), path), data));
}

const send = (uid, id, data) => setDoc(doc(as(uid), `groups/g1/messages/${id}`), data);

// The query the app listens with.
const readChat = (uid, groupId = 'g1') =>
  getDocs(query(collection(as(uid), `groups/${groupId}/messages`), orderBy('sentAt')));

describe('reading a group chat', () => {
  test('members can', async () => {
    await seed('groups/g1/messages/m1', message({ sentAt: Timestamp.now() }));
    await assertSucceeds(readChat('maya'));
    await assertSucceeds(readChat('ana'));
  });

  test('invited users and everyone else cannot', async () => {
    await assertFails(readChat('ben'));
    await assertFails(readChat('cal'));
  });

  test('members still can once the group is disbanded or archived', async () => {
    await seed('groups/g1', group({ disbanded: true }));
    await assertSucceeds(readChat('ana'));
    await seed('groups/g1', group({ archivesAt: Timestamp.fromMillis(Date.now() - 1000) }));
    await assertSucceeds(readChat('ana'));
  });

  test('not a group that does not exist', async () => {
    await assertFails(readChat('ana', 'nope'));
  });
});

describe('sending a message', () => {
  test('a member sends as themselves, stamped by the server', async () => {
    await assertSucceeds(send('ana', 'm1', message()));
    await assertSucceeds(send('maya', 'm2', message({ authorUid: 'maya', authorName: 'Maya' })));
  });

  test('up to 500 characters', async () => {
    await assertSucceeds(send('ana', 'm1', message({ text: 'a'.repeat(500) })));
    await assertFails(send('ana', 'm2', message({ text: 'a'.repeat(501) })));
  });

  test('invited users and everyone else cannot', async () => {
    await assertFails(send('ben', 'm1', message({ authorUid: 'ben', authorName: 'Ben' })));
    await assertFails(send('cal', 'm1', message({ authorUid: 'cal', authorName: 'Cal' })));
  });

  test('not as someone else, or under another name', async () => {
    await assertFails(send('ana', 'm1', message({ authorUid: 'maya', authorName: 'Maya' })));
    await assertFails(send('ana', 'm2', message({ authorName: 'Maya' })));
  });

  test('not with the phone\'s own time', async () => {
    await assertFails(send('ana', 'm1', message({ sentAt: Timestamp.fromMillis(Date.now() - 60 * 60 * 1000) })));
  });

  test('not empty, blank, or with other fields', async () => {
    await assertFails(send('ana', 'm1', message({ text: '' })));
    await assertFails(send('ana', 'm2', message({ text: '   ' })));
    await assertFails(send('ana', 'm3', message({ text: 42 })));
    await assertFails(send('ana', 'm4', message({ pinned: true })));
    const { text, ...withoutText } = message();
    await assertFails(send('ana', 'm5', withoutText));
  });

  test('not once the group is disbanded or archived', async () => {
    await seed('groups/g1', group({ disbanded: true }));
    await assertFails(send('ana', 'm1', message()));
    await seed('groups/g1', group({ archivesAt: Timestamp.fromMillis(Date.now() - 1000) }));
    await assertFails(send('ana', 'm2', message()));
  });

  test('not to a group that does not exist', async () => {
    await assertFails(setDoc(doc(as('ana'), 'groups/nope/messages/m1'), message()));
  });

  test('not with an id longer than 64 characters', async () => {
    await assertFails(send('ana', 'm'.repeat(65), message()));
  });
});

describe('a sent message', () => {
  test('cannot be changed or deleted, even by its author', async () => {
    await seed('groups/g1/messages/m1', message({ sentAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(as('ana'), 'groups/g1/messages/m1'), { text: 'Edited' }));
    await assertFails(deleteDoc(doc(as('ana'), 'groups/g1/messages/m1')));
    await assertFails(deleteDoc(doc(as('maya'), 'groups/g1/messages/m1')));
  });

  test('cannot be sent again under the same id', async () => {
    await assertSucceeds(send('ana', 'm1', message()));
    await assertFails(send('ana', 'm1', message()));
  });
});
