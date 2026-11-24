// Checks the rules in ../firestore.rules for proposing, voting on and scheduling a group's next
// session, on the Firestore emulator. Run with `npm test`.
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';

// SEMESTER_END in Group.kt: the end of Dec 19, 2026 in Boston.
const SEMESTER_END = Timestamp.fromMillis(1797742800000);
const MINUTE = 60 * 1000;

// Fixed for the whole run, so a proposal written in a test matches the one seeded before it.
const NOW = Date.now();

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

const names = { maya: 'Maya', ana: 'Ana', ben: 'Ben', cal: 'Cal', dan: 'Dan' };

// maya hosts, ana and ben are in, cal is invited, dan is someone else.
function group(overrides = {}) {
  return {
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
    ...overrides,
  };
}

// What the app writes when uid proposes tomorrow-ish, an hour long (Group.toFirestore).
function proposal({ by = 'ana', startsIn = 24 * 60, length = 60, yes, no = {}, ...overrides } = {}) {
  return {
    id: 'next-1',
    startsAt: Timestamp.fromMillis(NOW + startsIn * MINUTE),
    endsAt: Timestamp.fromMillis(NOW + (startsIn + length) * MINUTE),
    building: 'Mugar Library',
    meetingPoint: '2nd floor',
    mode: 'QUIET',
    byUid: by,
    yes: yes ?? { [by]: names[by] },
    no,
    ...overrides,
  };
}

// What the app writes for the pod maya schedules from p (Group.scheduledPod).
function scheduledPod(p, overrides = {}) {
  const members = { maya: 'Maya', ...p.yes };
  return {
    title: 'Algorithms review',
    course: 'CS 330',
    building: p.building,
    meetingPoint: p.meetingPoint,
    startsAt: p.startsAt,
    endsAt: p.endsAt,
    capacity: 3,
    hostUid: 'maya',
    hostName: 'Maya',
    members,
    mode: p.mode,
    canceled: false,
    groupId: 'g1',
    ...overrides,
  };
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled((context) => setDoc(doc(context.firestore(), path), data));
}

const propose = (uid, p) => updateDoc(doc(as(uid), 'groups/g1'), { proposal: p });

// The schedule transaction's two writes, as one batch.
function schedule(uid, pod, { clear = true, podId = 'next-1' } = {}) {
  const db = as(uid);
  const batch = writeBatch(db);
  if (pod) batch.set(doc(db, `pods/${podId}`), pod);
  if (clear) batch.update(doc(db, 'groups/g1'), { proposal: null });
  return batch.commit();
}

describe('proposing the next session', () => {
  test('a member proposes and counts as coming', async () => {
    await seed('groups/g1', group());
    await assertSucceeds(propose('ana', proposal()));
    await assertSucceeds(propose('maya', proposal({ by: 'maya', id: 'next-2' })));
  });

  test('it replaces an earlier proposal and its votes', async () => {
    await seed('groups/g1', group({ proposal: proposal({ yes: { ana: 'Ana', ben: 'Ben' }, no: { maya: true } }) }));
    await assertSucceeds(propose('ben', proposal({ by: 'ben', id: 'next-2' })));
  });

  test('invited users and everyone else cannot', async () => {
    await seed('groups/g1', group());
    await assertFails(propose('cal', proposal({ by: 'cal' })));
    await assertFails(propose('dan', proposal({ by: 'dan' })));
  });

  test('only as themselves, with only their own yes', async () => {
    await seed('groups/g1', group());
    await assertFails(propose('ana', proposal({ by: 'ben' })));
    await assertFails(propose('ana', proposal({ yes: { ana: 'Ana', ben: 'Ben' } })));
    await assertFails(propose('ana', proposal({ yes: { ana: 'Annie' } })));
    await assertFails(propose('ana', proposal({ yes: {} })));
    await assertFails(propose('ana', proposal({ no: { ben: true } })));
  });

  test('within a new pod\'s limits', async () => {
    await seed('groups/g1', group());
    for (const p of [
      proposal({ length: 20 }),
      proposal({ length: 5 * 60 }),
      proposal({ startsIn: -120 }),
      proposal({ startsIn: 8 * 24 * 60 }),
      proposal({ mode: 'COORDINATE' }),
      proposal({ meetingPoint: 'ab' }),
      proposal({ building: '' }),
      proposal({ extra: true }),
    ]) {
      await assertFails(propose('ana', p));
    }
  });

  test('not with the id of a pod that exists', async () => {
    await seed('groups/g1', group());
    await seed('pods/next-1', scheduledPod(proposal()));
    await assertFails(propose('ana', proposal()));
  });

  test('not together with another change, or once the group is disbanded', async () => {
    await seed('groups/g1', group());
    await assertFails(updateDoc(doc(as('ana'), 'groups/g1'), { proposal: proposal(), name: 'Renamed' }));
    await seed('groups/g1', group({ disbanded: true }));
    await assertFails(propose('ana', proposal()));
  });
});

describe('voting', () => {
  const vote = (uid, yes, no) => updateDoc(doc(as(uid), 'groups/g1'), { proposal: proposal({ yes, no }) });

  beforeEach(() => seed('groups/g1', group({ proposal: proposal() })));

  test('a member says they can come, then that they can\'t', async () => {
    await assertSucceeds(vote('ben', { ana: 'Ana', ben: 'Ben' }, {}));
    await assertSucceeds(vote('ben', { ana: 'Ana' }, { ben: true }));
    await assertSucceeds(vote('ana', {}, { ana: true, ben: true }));
  });

  test('not for someone else, or under another name', async () => {
    await assertFails(vote('ben', { ana: 'Ana', maya: 'Maya' }, {}));
    await assertFails(vote('ben', {}, {}));
    await assertFails(vote('ben', { ana: 'Ana', ben: 'Benny' }, {}));
  });

  test('not both yes and no', async () => {
    await assertFails(vote('ben', { ana: 'Ana', ben: 'Ben' }, { ben: true }));
  });

  test('invited users and everyone else cannot', async () => {
    await assertFails(vote('cal', { ana: 'Ana', cal: 'Cal' }, {}));
    await assertFails(vote('dan', { ana: 'Ana', dan: 'Dan' }, {}));
  });

  test('not changing the proposal itself', async () => {
    const moved = { ...proposal({ yes: { ana: 'Ana', ben: 'Ben' } }), building: 'GSU' };
    await assertFails(updateDoc(doc(as('ben'), 'groups/g1'), { proposal: moved }));
  });

  test('not without a proposal, or once it has ended', async () => {
    await seed('groups/g1', group());
    await assertFails(vote('ben', { ana: 'Ana', ben: 'Ben' }, {}));
    await seed('groups/g1', group({ proposal: proposal({ startsIn: -90 }) }));
    const ended = proposal({ startsIn: -90, yes: { ana: 'Ana', ben: 'Ben' } });
    await assertFails(updateDoc(doc(as('ben'), 'groups/g1'), { proposal: ended }));
  });
});

describe('scheduling', () => {
  const voted = proposal({ yes: { ana: 'Ana', ben: 'Ben' } });

  beforeEach(() => seed('groups/g1', group({ proposal: voted })));

  test('the host makes the pod and clears the proposal in one write', async () => {
    await assertSucceeds(schedule('maya', scheduledPod(voted)));
  });

  test('the host counts as going even without voting, so one yes is enough', async () => {
    const one = proposal({ yes: { ana: 'Ana' } });
    await seed('groups/g1', group({ proposal: one }));
    await assertSucceeds(schedule('maya', scheduledPod(one, { capacity: 2 })));
  });

  test('not by anyone but the host', async () => {
    await assertFails(schedule('ana', scheduledPod(voted, { hostUid: 'ana', hostName: 'Ana' })));
  });

  test('not the pod without clearing the proposal, or the other way round', async () => {
    await assertFails(schedule('maya', scheduledPod(voted), { clear: false }));
    await assertFails(schedule('maya', null));
  });

  test('the pod has exactly the host and who said yes', async () => {
    await assertFails(schedule('maya', scheduledPod(voted, { members: { maya: 'Maya', ana: 'Ana' } })));
    await assertFails(schedule('maya', scheduledPod(voted, { members: { maya: 'Maya', ana: 'Ana', ben: 'Ben', cal: 'Cal' }, capacity: 4 })));
    await assertFails(schedule('maya', scheduledPod(voted, { members: { maya: 'Maya', ana: 'Ana', ben: 'Benny' } })));
  });

  test('the pod matches the proposal and the group', async () => {
    for (const overrides of [
      { building: 'GSU' },
      { startsAt: Timestamp.fromMillis(voted.startsAt.toMillis() + 30 * MINUTE) },
      { mode: 'TALK' },
      { title: 'Something else' },
      { capacity: 2 },
      { capacity: 51 },
      { groupId: 'g2' },
    ]) {
      await assertFails(schedule('maya', scheduledPod(voted, overrides)));
    }
    await assertFails(schedule('maya', scheduledPod(voted), { podId: 'other-id' }));
  });

  test('not with nobody but the host going', async () => {
    const nobody = proposal({ by: 'maya', yes: { maya: 'Maya' } });
    await seed('groups/g1', group({ proposal: nobody }));
    await assertFails(schedule('maya', scheduledPod(nobody, { capacity: 2 })));
  });

  test('only once', async () => {
    await assertSucceeds(schedule('maya', scheduledPod(voted)));
    await assertFails(schedule('maya', scheduledPod(voted), { podId: 'next-1' }));
  });

  test('not once the group is disbanded', async () => {
    await seed('groups/g1', group({ proposal: voted, disbanded: true }));
    await assertFails(schedule('maya', scheduledPod(voted)));
  });
});

describe('votes of people who leave', () => {
  const voted = proposal({ yes: { ana: 'Ana', ben: 'Ben' }, no: {} });

  test('leaving takes back the vote, and must', async () => {
    await seed('groups/g1', group({ proposal: voted }));
    const left = { members: { maya: 'Maya', ana: 'Ana' }, access: ['ana', 'cal', 'maya'] };
    await assertFails(updateDoc(doc(as('ben'), 'groups/g1'), left));
    await assertSucceeds(updateDoc(doc(as('ben'), 'groups/g1'), { ...left, proposal: proposal({ yes: { ana: 'Ana' } }) }));
  });

  test('leaving without a vote leaves the proposal alone', async () => {
    await seed('groups/g1', group({ proposal: proposal() }));
    await assertSucceeds(updateDoc(doc(as('ben'), 'groups/g1'), { members: { maya: 'Maya', ana: 'Ana' }, access: ['ana', 'cal', 'maya'] }));
  });

  test('taking over as host takes back the old host\'s vote', async () => {
    const hostVoted = proposal({ yes: { ana: 'Ana', maya: 'Maya' } });
    await seed('groups/g1', group({ proposal: hostVoted, handover: { uid: 'ana', name: 'Ana' } }));
    const takeover = {
      hostUid: 'ana',
      handover: null,
      members: { ana: 'Ana', ben: 'Ben' },
      access: ['ana', 'ben', 'cal'],
    };
    await assertFails(updateDoc(doc(as('ana'), 'groups/g1'), takeover));
    await assertSucceeds(updateDoc(doc(as('ana'), 'groups/g1'), { ...takeover, proposal: proposal({ yes: { ana: 'Ana' } }) }));
  });
});

describe('pods and groups as the app now writes them', () => {
  test('a pod made in Discover has a null groupId', async () => {
    const now = Date.now();
    const pod = {
      title: 'Algorithms review', course: null, building: 'Mugar Library', meetingPoint: '2nd floor',
      startsAt: Timestamp.fromMillis(now + 10 * MINUTE), endsAt: Timestamp.fromMillis(now + 70 * MINUTE),
      capacity: 4, hostUid: 'maya', hostName: 'Maya', members: { maya: 'Maya' }, mode: 'QUIET', canceled: false,
    };
    await assertSucceeds(setDoc(doc(as('maya'), 'pods/a'), { ...pod, groupId: null }));
    await assertFails(setDoc(doc(as('maya'), 'pods/b'), { ...pod, groupId: 'g1' }));
  });

  test('a new group has a null proposal', async () => {
    const now = Date.now();
    await seed('pods/g1', {
      title: 'Algorithms review', course: 'CS 330', building: 'Mugar Library', meetingPoint: '2nd floor',
      startsAt: Timestamp.fromMillis(now - 10 * MINUTE), endsAt: Timestamp.fromMillis(now + 50 * MINUTE),
      capacity: 8, hostUid: 'maya', hostName: 'Maya', members: { maya: 'Maya', ana: 'Ana' }, mode: 'QUIET', canceled: false,
    });
    const kept = group({ members: { maya: 'Maya' }, invited: { ana: 'Ana' }, access: ['ana', 'maya'] });
    await assertFails(setDoc(doc(as('maya'), 'groups/g1'), { ...kept, proposal: proposal({ by: 'maya' }) }));
    await assertSucceeds(setDoc(doc(as('maya'), 'groups/g1'), { ...kept, proposal: null }));
  });
});
