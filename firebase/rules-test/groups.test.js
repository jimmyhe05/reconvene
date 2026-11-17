// Checks the group rules in ../firestore.rules on the Firestore emulator. Run with `npm test`.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';

// SEMESTER_END in Group.kt: the end of Dec 19, 2026 in Boston.
const SEMESTER_END = Timestamp.fromMillis(1797742800000);
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

const names = { maya: 'Maya', ana: 'Ana', ben: 'Ben', cal: 'Cal' };

function pod({ host = 'maya', members = ['maya', 'ana', 'ben'], startsIn = -10, endsIn = 50, canceled = false } = {}) {
  const now = Date.now();
  return {
    title: 'Algorithms review',
    course: 'CS 330',
    building: 'Mugar Library',
    meetingPoint: '2nd floor',
    startsAt: Timestamp.fromMillis(now + startsIn * MINUTE),
    endsAt: Timestamp.fromMillis(now + endsIn * MINUTE),
    capacity: 8,
    hostUid: host,
    hostName: names[host],
    members: Object.fromEntries(members.map((uid) => [uid, names[uid]])),
    mode: 'QUIET',
    canceled,
  };
}

// What the app writes when maya keeps pod p1 with ana and ben in it (Group.toNewFirestoreGroup).
function newGroup(overrides = {}) {
  return {
    name: 'Algorithms review',
    course: 'CS 330',
    fromPodId: 'p1',
    hostUid: 'maya',
    keptByUid: 'maya',
    members: { maya: 'Maya' },
    invited: { ana: 'Ana', ben: 'Ben' },
    access: ['ana', 'ben', 'maya'],
    handover: null,
    archivesAt: SEMESTER_END,
    disbanded: false,
    ...overrides,
  };
}

// A group that's been going for a while: maya hosts, ana is in, ben is invited.
function existingGroup(overrides = {}) {
  return newGroup({
    members: { maya: 'Maya', ana: 'Ana' },
    invited: { ben: 'Ben' },
    ...overrides,
  });
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled((context) => setDoc(doc(context.firestore(), path), data));
}

describe('keeping a pod as a group', () => {
  test('the host keeps a running pod and invites everyone else in it', async () => {
    await seed('pods/p1', pod());
    await assertSucceeds(setDoc(doc(as('maya'), 'groups/p1'), newGroup()));
  });

  test('someone else in the pod cannot', async () => {
    await seed('pods/p1', pod());
    await assertFails(setDoc(doc(as('ana'), 'groups/p1'), newGroup({ hostUid: 'ana', keptByUid: 'ana' })));
  });

  test('not before it starts, after it ends, or once canceled', async () => {
    for (const [id, data] of [
      ['early', pod({ startsIn: 10, endsIn: 70 })],
      ['ended', pod({ startsIn: -70, endsIn: -10 })],
      ['canceled', pod({ canceled: true })],
    ]) {
      await seed(`pods/${id}`, data);
      await assertFails(setDoc(doc(as('maya'), `groups/${id}`), newGroup({ fromPodId: id })));
    }
  });

  test('not when nobody else is in the pod', async () => {
    await seed('pods/p1', pod({ members: ['maya'] }));
    await assertFails(setDoc(doc(as('maya'), 'groups/p1'), newGroup({ invited: {}, access: ['maya'] })));
  });

  test('only once per pod', async () => {
    await seed('pods/p1', pod());
    await assertSucceeds(setDoc(doc(as('maya'), 'groups/p1'), newGroup()));
    await assertFails(setDoc(doc(as('maya'), 'groups/p1'), newGroup({ name: 'Second try' })));
  });

  test('invites only the people in the pod, under their names there, and adds nobody', async () => {
    await seed('pods/p1', pod());
    const maya = as('maya');
    await assertFails(setDoc(doc(maya, 'groups/p1'),
      newGroup({ invited: { ana: 'Ana', ben: 'Ben', cal: 'Cal' }, access: ['ana', 'ben', 'cal', 'maya'] })));
    await assertFails(setDoc(doc(maya, 'groups/p1'), newGroup({ invited: { ana: 'Ana', ben: 'Benny' } })));
    await assertFails(setDoc(doc(maya, 'groups/p1'),
      newGroup({ members: { maya: 'Maya', ana: 'Ana' }, invited: { ben: 'Ben' } })));
  });

  test('a group lasts the semester, starts with no handover, and has no extra fields', async () => {
    await seed('pods/p1', pod());
    const maya = as('maya');
    await assertFails(setDoc(doc(maya, 'groups/p1'), newGroup({ archivesAt: Timestamp.fromMillis(SEMESTER_END.toMillis() + MINUTE) })));
    await assertFails(setDoc(doc(maya, 'groups/p1'), newGroup({ handover: { uid: 'ana', name: 'Ana' } })));
    await assertFails(setDoc(doc(maya, 'groups/p1'), newGroup({ proposal: null })));
  });

  test('the group id must be the pod id', async () => {
    await seed('pods/p1', pod());
    await assertFails(setDoc(doc(as('maya'), 'groups/other'), newGroup()));
  });
});

describe('reading groups', () => {
  beforeEach(() => seed('groups/p1', existingGroup()));

  test('members and invited users read it; others do not', async () => {
    await assertSucceeds(getDoc(doc(as('ana'), 'groups/p1')));
    await assertSucceeds(getDoc(doc(as('ben'), 'groups/p1')));
    await assertFails(getDoc(doc(as('cal'), 'groups/p1')));
  });

  test('whoever kept it can still read it after leaving', async () => {
    await seed('groups/p1', existingGroup({ hostUid: 'ana', members: { ana: 'Ana' }, access: ['ana', 'ben'] }));
    await assertSucceeds(getDoc(doc(as('maya'), 'groups/p1')));
  });

  test('a group that does not exist can be looked up', async () => {
    await assertSucceeds(getDoc(doc(as('cal'), 'groups/nothing')));
  });

  test('the Groups tab query works for your own uid only', async () => {
    const ana = as('ana');
    await assertSucceeds(getDocs(query(collection(ana, 'groups'), where('access', 'array-contains', 'ana'))));
    await assertFails(getDocs(query(collection(ana, 'groups'), where('access', 'array-contains', 'maya'))));
    await assertFails(getDocs(collection(ana, 'groups')));
  });

  test('a user without a verified BU email reads nothing', async () => {
    const outsider = env.authenticatedContext('ana', { email: 'ana@gmail.com', email_verified: true }).firestore();
    await assertFails(getDoc(doc(outsider, 'groups/p1')));
  });
});

describe('invites', () => {
  beforeEach(() => seed('groups/p1', existingGroup()));

  test('the invited user accepts', async () => {
    await assertSucceeds(updateDoc(doc(as('ben'), 'groups/p1'), {
      members: { maya: 'Maya', ana: 'Ana', ben: 'Ben' },
      invited: {},
    }));
  });

  test('nobody accepts for someone else, or under another name', async () => {
    await assertFails(updateDoc(doc(as('ana'), 'groups/p1'), {
      members: { maya: 'Maya', ana: 'Ana', ben: 'Ben' },
      invited: {},
    }));
    await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), {
      members: { maya: 'Maya', ana: 'Ana', ben: 'Boss' },
      invited: {},
    }));
  });

  test('the invited user declines and loses access', async () => {
    await assertSucceeds(updateDoc(doc(as('ben'), 'groups/p1'), { invited: {}, access: ['ana', 'maya'] }));
  });

  test('declining cannot remove anyone else', async () => {
    await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), { invited: {}, access: ['maya'] }));
  });
});

describe('leaving', () => {
  test('a member leaves', async () => {
    await seed('groups/p1', existingGroup());
    await assertSucceeds(updateDoc(doc(as('ana'), 'groups/p1'), { members: { maya: 'Maya' }, access: ['ben', 'maya'] }));
  });

  test('the host cannot leave', async () => {
    await seed('groups/p1', existingGroup());
    await assertFails(updateDoc(doc(as('maya'), 'groups/p1'), { members: { ana: 'Ana' }, access: ['ana', 'ben'] }));
  });

  test('nobody removes someone else', async () => {
    await seed('groups/p1', existingGroup());
    await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), { members: { maya: 'Maya' }, access: ['ben', 'maya'] }));
  });

  test('leaving calls off a handover offered to you, and only yours', async () => {
    await seed('groups/p1', existingGroup({ handover: { uid: 'ana', name: 'Ana' } }));
    await assertSucceeds(updateDoc(doc(as('ana'), 'groups/p1'),
      { members: { maya: 'Maya' }, access: ['ben', 'maya'], handover: null }));

    await seed('groups/p2', existingGroup({
      members: { maya: 'Maya', ana: 'Ana', cal: 'Cal' },
      access: ['ana', 'ben', 'cal', 'maya'],
      handover: { uid: 'cal', name: 'Cal' },
    }));
    await assertFails(updateDoc(doc(as('ana'), 'groups/p2'),
      { members: { maya: 'Maya', cal: 'Cal' }, access: ['ben', 'cal', 'maya'], handover: null }));
  });
});

describe('handing over the host role', () => {
  test('the host offers it to a member', async () => {
    await seed('groups/p1', existingGroup());
    await assertSucceeds(updateDoc(doc(as('maya'), 'groups/p1'), { handover: { uid: 'ana', name: 'Ana' } }));
  });

  test('not to someone only invited, not under another name, and not by a member', async () => {
    await seed('groups/p1', existingGroup());
    await assertFails(updateDoc(doc(as('maya'), 'groups/p1'), { handover: { uid: 'ben', name: 'Ben' } }));
    await assertFails(updateDoc(doc(as('maya'), 'groups/p1'), { handover: { uid: 'ana', name: 'Annie' } }));
    await assertFails(updateDoc(doc(as('ana'), 'groups/p1'), { handover: { uid: 'ana', name: 'Ana' } }));
  });

  test('the host or the chosen member calls it off; nobody else', async () => {
    await seed('groups/p1', existingGroup({
      members: { maya: 'Maya', ana: 'Ana', cal: 'Cal' },
      access: ['ana', 'ben', 'cal', 'maya'],
      handover: { uid: 'ana', name: 'Ana' },
    }));
    await assertFails(updateDoc(doc(as('cal'), 'groups/p1'), { handover: null }));
    await assertSucceeds(updateDoc(doc(as('ana'), 'groups/p1'), { handover: null }));
  });

  test('the chosen member accepts, and the old host leaves in the same write', async () => {
    await seed('groups/p1', existingGroup({ handover: { uid: 'ana', name: 'Ana' } }));
    await assertSucceeds(updateDoc(doc(as('ana'), 'groups/p1'), {
      hostUid: 'ana',
      handover: null,
      members: { ana: 'Ana' },
      access: ['ana', 'ben'],
    }));
  });

  test('accepting cannot keep the old host or be done by anyone else', async () => {
    await seed('groups/p1', existingGroup({ handover: { uid: 'ana', name: 'Ana' } }));
    await assertFails(updateDoc(doc(as('ana'), 'groups/p1'), { hostUid: 'ana', handover: null }));
    await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), {
      hostUid: 'ben',
      handover: null,
      members: { ana: 'Ana' },
      access: ['ana', 'ben'],
    }));
  });
});

describe('disbanding', () => {
  beforeEach(() => seed('groups/p1', existingGroup()));

  test('the host disbands, and the group stays on record', async () => {
    await assertSucceeds(updateDoc(doc(as('maya'), 'groups/p1'), { disbanded: true, disbandedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(as('ana'), 'groups/p1')));
  });

  test('a member cannot', async () => {
    await assertFails(updateDoc(doc(as('ana'), 'groups/p1'), { disbanded: true, disbandedAt: serverTimestamp() }));
  });

  test('after disbanding nothing changes', async () => {
    await updateDoc(doc(as('maya'), 'groups/p1'), { disbanded: true, disbandedAt: serverTimestamp() });
    await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), {
      members: { maya: 'Maya', ana: 'Ana', ben: 'Ben' },
      invited: {},
    }));
    await assertFails(updateDoc(doc(as('maya'), 'groups/p1'), { disbanded: false }));
  });

  test('nobody deletes a group', async () => {
    await assertFails(deleteDoc(doc(as('maya'), 'groups/p1')));
  });
});

test('after the semester a group is read-only', async () => {
  await seed('groups/p1', existingGroup({ archivesAt: Timestamp.fromMillis(Date.now() - MINUTE) }));
  await assertFails(updateDoc(doc(as('ben'), 'groups/p1'), {
    members: { maya: 'Maya', ana: 'Ana', ben: 'Ben' },
    invited: {},
  }));
  await assertSucceeds(getDoc(doc(as('ana'), 'groups/p1')));
});
