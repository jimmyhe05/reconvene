// Puts sample pods hosted by made-up students into Firestore, so Discover has something to show in
// a demo. Every id and uid starts with "demo-", so they are easy to spot and remove.
//
// Signs in with Application Default Credentials (gcloud auth application-default login), so there
// is no key file. Admin access skips the security rules, which is the only way to write pods hosted
// by someone else.
//
//   node seed-demo-pods.mjs --project <firebase-project-id> [--at 2026-11-24T14:00] [--clean]
//
// --at  the moment the demo starts, in local time (default: now). Pods are placed around it.
// --clean  deletes the demo pods instead.
// --dry-run  checks and lists the pods without connecting to Firestore.

import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';

const args = process.argv.slice(2);
const option = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

const projectId = option('--project');
if (!projectId) {
  console.error('Usage: node seed-demo-pods.mjs --project <firebase-project-id> [--at 2026-11-24T14:00] [--clean]');
  process.exit(1);
}
const anchor = option('--at') ? new Date(option('--at')) : new Date();
if (Number.isNaN(anchor.getTime())) {
  console.error(`--at must be a date and time like 2026-11-24T14:00, not "${option('--at')}"`);
  process.exit(1);
}

const students = {
  priya: 'Priya Nair',
  marcus: 'Marcus Reid',
  ava: 'Ava Chen',
  diego: 'Diego Morales',
  hana: 'Hana Kim',
  leo: 'Leo Okafor',
  sofia: 'Sofia Rossi',
  kwame: 'Kwame Mensah',
  emma: 'Emma Walsh',
  yuki: 'Yuki Tanaka',
  omar: 'Omar Haddad',
  grace: 'Grace Liu',
};

const minutesFromAnchor = (minutes) => new Date(anchor.getTime() + minutes * 60_000);

// A day after the anchor's date, at a local clock time.
const dayAt = (days, hour, minute = 0) => {
  const d = new Date(anchor);
  d.setDate(d.getDate() + days);
  d.setHours(hour, minute, 0, 0);
  return d;
};

// Buildings and meeting points match the create form's suggestions, so walking times work.
const pods = [
  {
    id: 'demo-01-dp-pset',
    title: 'Problem set 6: dynamic programming',
    course: 'CS 330',
    building: 'Mugar Library',
    meetingPoint: '2nd floor study tables',
    startsAt: minutesFromAnchor(-40),
    minutes: 120,
    capacity: 5,
    host: 'priya',
    members: ['diego', 'hana'],
    mode: 'TALK',
  },
  {
    id: 'demo-02-linear-algebra',
    title: 'Linear algebra midterm review',
    course: 'MA 242',
    building: 'CDS',
    meetingPoint: '3rd floor commons, near the windows',
    startsAt: minutesFromAnchor(20),
    minutes: 90,
    capacity: 8,
    host: 'marcus',
    members: ['ava', 'leo', 'sofia'],
    mode: 'TALK',
  },
  {
    id: 'demo-03-wr-paper',
    title: 'Quiet writing for the research paper',
    course: 'WR 150',
    building: 'Mugar Library',
    meetingPoint: '3rd floor quiet study',
    startsAt: minutesFromAnchor(45),
    minutes: 120,
    capacity: 6,
    host: 'emma',
    members: ['yuki'],
    mode: 'QUIET',
  },
  {
    id: 'demo-04-physics-lab',
    title: 'Physics lab report write-up',
    course: 'PY 212',
    building: 'Science & Engineering Library',
    meetingPoint: '1st floor group tables',
    startsAt: minutesFromAnchor(75),
    minutes: 90,
    capacity: 4,
    host: 'omar',
    members: ['grace', 'kwame', 'diego'],
    mode: 'TALK',
  },
  {
    id: 'demo-05-finance-case',
    title: 'Corporate finance case prep',
    course: 'QST FE 323',
    building: 'Questrom',
    meetingPoint: '1st floor atrium',
    startsAt: minutesFromAnchor(150),
    minutes: 60,
    capacity: 4,
    host: 'sofia',
    members: ['leo'],
    mode: 'TALK',
  },
  {
    id: 'demo-06-orgo-flashcards',
    title: 'Organic chemistry reactions flashcards',
    course: 'CH 203',
    building: 'GSU',
    meetingPoint: '2nd floor lounge',
    startsAt: minutesFromAnchor(240),
    minutes: 60,
    capacity: 6,
    host: 'hana',
    members: ['ava'],
    mode: 'TALK',
  },
  {
    id: 'demo-07-deep-work',
    title: 'Deep work, phones away',
    course: null,
    building: 'Kilachand Center',
    meetingPoint: 'Lower level study area',
    startsAt: minutesFromAnchor(360),
    minutes: 180,
    capacity: 10,
    host: 'kwame',
    members: ['grace', 'yuki', 'emma'],
    mode: 'QUIET',
  },
  {
    id: 'demo-08-data-structures',
    title: 'Data structures final warm-up',
    course: 'CS 112',
    building: 'CDS',
    meetingPoint: '2nd floor, by the big stairs',
    startsAt: dayAt(1, 10),
    minutes: 120,
    capacity: 20,
    host: 'ava',
    members: ['marcus', 'priya', 'diego', 'leo', 'omar', 'yuki', 'hana'],
    mode: 'TALK',
  },
  {
    id: 'demo-09-psych-review',
    title: 'Intro psych chapter 9 review',
    course: 'PS 101',
    building: 'CAS',
    meetingPoint: 'Basement study tables',
    startsAt: dayAt(1, 14),
    minutes: 90,
    capacity: 6,
    host: 'grace',
    members: ['sofia'],
    mode: 'QUIET',
  },
  {
    id: 'demo-10-probability',
    title: 'Probability homework 8',
    course: 'EK 381',
    building: 'Photonics Center',
    meetingPoint: '2nd floor atrium tables',
    startsAt: dayAt(2, 16),
    minutes: 90,
    capacity: 5,
    host: 'leo',
    members: ['omar', 'kwame'],
    mode: 'TALK',
  },
];

// The same limits the app and the security rules use, so a demo pod is one a student could make.
function check(pod) {
  const people = 1 + pod.members.length;
  const problems = [];
  if (pod.title.length < 3 || pod.title.length > 60) problems.push('title must be 3 to 60 characters');
  if (pod.course && pod.course.length > 20) problems.push('course must be up to 20 characters');
  if (pod.meetingPoint.length < 3 || pod.meetingPoint.length > 80) problems.push('meeting point must be 3 to 80 characters');
  if (pod.minutes < 30 || pod.minutes > 240) problems.push('must last 30 minutes to 4 hours');
  if (pod.capacity < 2 || pod.capacity > 50) problems.push('capacity must be 2 to 50');
  if (people > pod.capacity) problems.push(`${people} people but room for ${pod.capacity}`);
  if (!['QUIET', 'TALK'].includes(pod.mode)) problems.push('mode must be QUIET or TALK');
  if (problems.length) throw new Error(`${pod.id}: ${problems.join('; ')}`);
}

function toDocument(pod) {
  const uid = (key) => `demo-${key}`;
  const members = { [uid(pod.host)]: students[pod.host] };
  for (const key of pod.members) members[uid(key)] = students[key];
  return {
    title: pod.title,
    course: pod.course,
    building: pod.building,
    meetingPoint: pod.meetingPoint,
    startsAt: Timestamp.fromDate(pod.startsAt),
    endsAt: Timestamp.fromDate(new Date(pod.startsAt.getTime() + pod.minutes * 60_000)),
    capacity: pod.capacity,
    hostUid: uid(pod.host),
    hostName: students[pod.host],
    members,
    mode: pod.mode,
    canceled: false,
  };
}

const time = (d) => d.toLocaleString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' });
const list = () => pods.forEach((pod) => {
  const people = 1 + pod.members.length;
  console.log(`  ${time(pod.startsAt).padEnd(16)} ${(pod.course ?? '—').padEnd(11)} ${pod.mode.padEnd(5)} ${people}/${pod.capacity}  ${pod.title}`);
});

if (args.includes('--dry-run')) {
  pods.forEach(check);
  console.log(`Would write ${pods.length} demo pods to ${projectId}, around ${time(anchor)}:`);
  list();
  process.exit(0);
}

initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();
const batch = db.batch();

if (args.includes('--clean')) {
  for (const pod of pods) batch.delete(db.collection('pods').doc(pod.id));
  await batch.commit();
  console.log(`Deleted ${pods.length} demo pods from ${projectId}.`);
} else {
  pods.forEach(check);
  // set() replaces the whole document, so running this again also resets who has joined.
  for (const pod of pods) batch.set(db.collection('pods').doc(pod.id), toDocument(pod));
  await batch.commit();
  console.log(`Wrote ${pods.length} demo pods to ${projectId}, around ${time(anchor)}:`);
  list();
}
