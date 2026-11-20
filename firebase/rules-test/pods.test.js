// Checks the pod rules in ../firestore.rules on the Firestore emulator. Run with `npm test`.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, Timestamp } from 'firebase/firestore';

const MINUTE = 60 * 1000;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-reconvene',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

beforeEach(() => env.clearFirestore());

// A signed-in, verified BU user.
const as = (uid) =>
  env.authenticatedContext(uid, { email: `${uid}@bu.edu`, email_verified: true }).firestore();

// What the app writes when maya creates a pod (Pod.toFirestore).
function newPod(overrides = {}) {
  const now = Date.now();
  return {
    title: 'Algorithms review',
    course: 'CS 330',
    building: 'Mugar Library',
    meetingPoint: '2nd floor',
    startsAt: Timestamp.fromMillis(now + 10 * MINUTE),
    endsAt: Timestamp.fromMillis(now + 70 * MINUTE),
    capacity: 8,
    hostUid: 'maya',
    hostName: 'Maya',
    members: { maya: 'Maya' },
    mode: 'QUIET',
    canceled: false,
    ...overrides,
  };
}

describe('a new pod\'s vibe', () => {
  test('Quiet and Open to chat are allowed', async () => {
    await assertSucceeds(setDoc(doc(as('maya'), 'pods/quiet'), newPod({ mode: 'QUIET' })));
    await assertSucceeds(setDoc(doc(as('maya'), 'pods/talk'), newPod({ mode: 'TALK' })));
  });

  test('the old Okay to coordinate and anything else are not', async () => {
    for (const mode of ['COORDINATE', 'LOUD', '', null]) {
      await assertFails(setDoc(doc(as('maya'), `pods/${mode || 'none'}`), newPod({ mode })));
    }
  });

  test('a pod saved as COORDINATE before still reads', async () => {
    await env.withSecurityRulesDisabled((context) =>
      setDoc(doc(context.firestore(), 'pods/old'), newPod({ mode: 'COORDINATE' })));
    await assertSucceeds(getDoc(doc(as('ana'), 'pods/old')));
  });
});
