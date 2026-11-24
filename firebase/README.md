# Firebase setup

Reconvene reads, creates and joins pods in Cloud Firestore, and only signed-in users with a verified
@bu.edu Google account can do any of that. Room stays the source of truth on the device: Discover always
shows what Room has, and a refresh copies upcoming pods from Firestore into Room.

Without the settings below, the app builds and runs with sample pods instead (this is what CI does).
Sign-in is then turned off and you create and join pods as a local "You"; nothing leaves the device.

## 1. Create the project (once per team)

1. In the [Firebase console](https://console.firebase.google.com/), create a project.
2. Build → Firestore Database → Create database. Choose **production mode** and a US location such as `nam5`.
3. Project settings → General → Add app → Android, package name `com.reconvene`. You can download
   `google-services.json` to read the values below from it, but keep it outside the repository. Skip the
   "Add Firebase SDK" Gradle steps; the app doesn't use the Google Services Gradle plugin.
4. Security → Authentication → Sign-in method → Google → Enable, pick a support email, Save.
   This creates the OAuth web client that sign-in needs.

## 2. Point your build at it (each developer)

1. Register your debug signing certificate, or Google sign-in fails on your builds. Print its fingerprints
   (the debug keystore password is always `android`):

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```

   Then Project settings → General → Your apps → the Android app → Add fingerprint, once for SHA-1 and
   once for SHA-256. Every developer's machine has its own debug certificate.
2. Download `google-services.json` again after that (keep it outside the repository) and copy four values
   into `local.properties` in the repository root. That file is gitignored, so the values never reach GitHub.

   | Property | Where it is in `google-services.json` |
   |---|---|
   | `firebase.projectId` | `project_info.project_id` |
   | `firebase.appId` | `client[].client_info.mobilesdk_app_id` |
   | `firebase.apiKey` | `client[].api_key[].current_key` |
   | `firebase.webClientId` | `client[].oauth_client[]` entry with `client_type` 3, its `client_id` |

   ```properties
   firebase.projectId=your-project-id
   firebase.appId=1:1234567890:android:abc123
   firebase.apiKey=AIza...
   firebase.webClientId=1234567890-abc.apps.googleusercontent.com
   ```

Rebuild. `AppContainer` uses Firestore and Firebase Auth when `firebase.projectId` is set, and sample pods
with sign-in turned off otherwise. Never commit `google-services.json` or these values.

## 3. Deploy the security rules

The rules live in `firestore.rules` next to this file. Either paste them into Firestore → Rules in the
console and publish, or use the Firebase CLI from this folder:

```bash
firebase deploy --only firestore:rules --project your-project-id
```

Only verified @bu.edu users can read pods. A user can create a pod they host, and add or remove only
themselves as a member: joining stops at capacity and 10 minutes before the end, and the host can't
leave. Only the host can cancel a pod, before it ends. Nobody can delete a pod. See the comment at the
top of the file.

A group's id is its pod's id, so a pod can be kept as a group only once. Only the pod's host can keep it,
while it's running and not canceled, with someone else in it; everyone else in the pod is invited and
nobody is added without accepting. Only people in a group or invited to it can read it. Each change is
checked on its own: users accept, decline or leave only for themselves, the host can't leave but can hand
the role to a member, who becomes host when they accept while the old host leaves in the same write. Only
the host disbands, which marks the group and keeps it. Nothing changes after a group is disbanded or after
the semester ends. Nobody can delete a group.

A group's chat is readable by its members only, also after it's disbanded or archived; invited users see
it once they accept. A member sends a message as themselves, under their name in the group, while the group
is open. The server stamps its time. Nobody can change or delete a message.

Any member can propose the next session, within a new pod's limits; it replaces the last proposal and its
votes, and the proposer counts as coming. Each member votes only for themselves, and leaving or handing
over takes back the leaver's vote. Only the host schedules it, once at least one other member can come:
one write makes the pod (the group's name and course, the proposal's time, place and vibe, the host plus
everyone who said yes) and clears the proposal. The pod is public in Discover like any other.

Test the rules on the Firestore emulator before publishing a change. The tests are in `rules-test/` and
need Node, plus Java 21 or newer for the emulator (Android Studio's bundled JDK works):

```bash
cd rules-test
npm install
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" npm test
```

`npm install` brings the Firebase CLI with it, and the tests run against a local emulator with a demo
project, so nothing touches the real database.

## Demo pods

`demo-data/seed-demo-pods.mjs` puts 10 pods hosted by made-up students into Firestore, so Discover
has something to show in a demo. Their ids and uids start with `demo-`. Times are placed around
`--at` (default: now): one already running, several in the next two hours, more over the next two
days. Running it again resets them, which also removes anyone who joined.

```bash
gcloud auth application-default login
cd firebase/demo-data && npm install
node seed-demo-pods.mjs --project <firebase-project-id> --at 2026-11-24T14:00 --dry-run
node seed-demo-pods.mjs --project <firebase-project-id> --at 2026-11-24T14:00
node seed-demo-pods.mjs --project <firebase-project-id> --clean
```

It writes with admin access from your Google login, which skips the security rules; there is no
key file. Only run it against our own project.

## How sync works

| Action | What happens |
|---|---|
| Open Discover / Try again | `refresh()` asks the server (not Firestore's cache) for pods that haven't ended, and upserts them into Room. Offline, it fails and Room keeps the cached pods. |
| Signed out | Discover asks the user to sign in and doesn't refresh, because the rules wouldn't let it read. |
| Create a pod | Saved to Room first, then handed to Firestore. Firestore keeps the write on the device and sends it when the network is back, even after a restart. |
| Cancel a pod | The host confirms, then a transaction sets `canceled` to true on the server copy. Like joining, it needs the network. Canceled pods drop out of Discover for everyone on their next refresh. |
| Join or leave | A Firestore transaction reads the server's copy, checks capacity and the join cutoff, and adds or removes the user's own entry in `members`. Room changes only after the server agrees. Needs the network: offline, the card shows an error and nothing changes. |
| Groups tab open | A live listener on groups whose `access` holds the user's uid keeps Room in step. A group the user leaves or declines drops out of the results and out of Room. Offline, the listener answers from Firestore's own cache. |
| Keep, answer an invite, leave, hand over, disband, propose, vote | A transaction reads the server's copy (for keeping, the pod too), checks it with the rules in `Group.kt`, and writes only the fields that change. Needs the network. |
| Group screen open | A live listener on the group's latest 300 messages, ordered by the server's time, upserts them into Room. One the server doesn't have yet shows as sending, at the phone's estimate of the time. |
| Send a message | Saved to Room as sending, then written to Firestore with its own id. A plain write, not a transaction, so it queues offline and lands when the network is back, even after a restart. Refused by the rules (for example after leaving), it shows as failed and can be retried; a retry of one the server already has counts as sent. |
| Schedule | One transaction reads the group, checks it with `scheduleProblem` in `Group.kt`, makes the pod with the proposal's id, and clears the proposal. The pod is saved to Room too, so it shows in Discover at once. Needs the network. |
| Sign out or switch account | Cached groups and chat are deleted from the phone. Pods stay, since every BU student can read them. |
| Malformed document | Skipped with a log line under the `FirestorePods` tag, so one bad document can't break the list. Pods made before sign-in (with `memberNames` and no `hostUid`) are skipped this way too. |

## Data model

Collection `pods`, document id = pod id (a UUID made on the device).

| Field | Type | Notes |
|---|---|---|
| `title` | string | 3 to 60 characters |
| `course` | string or null | up to 20 characters |
| `building` | string | |
| `meetingPoint` | string | 3 to 80 characters |
| `startsAt`, `endsAt` | timestamp | 30 minutes to 4 hours apart; start within 7 days |
| `capacity` | number | 2 to 50 |
| `hostUid` | string | Firebase Auth uid of the host, who made the pod |
| `hostName` | string | the host's Google name, 1 to 40 characters |
| `members` | map of uid to name | everyone going, the host included; at most `capacity` entries |
| `mode` | string | `QUIET` (Quiet) or `TALK` (Open to chat). Older pods can have `COORDINATE`, which the app reads as `TALK` |
| `canceled` | boolean | `false` on create; only the host can set it to `true`, and never back |
| `groupId` | string or null | the group that scheduled it; `null` for a pod made in Discover, missing on older pods |

Collection `groups`, document id = the id of the pod it was kept from.

| Field | Type | Notes |
|---|---|---|
| `name` | string | 3 to 40 characters |
| `course` | string or null | the pod's |
| `fromPodId` | string | same as the document id |
| `hostUid` | string | the current host, always in `members` |
| `keptByUid` | string | who kept the pod; never changes, and can always read the group |
| `members` | map of uid to name | everyone who accepted, the host included |
| `invited` | map of uid to name | invited and hasn't answered; together with `members`, at most 50 |
| `access` | array of uid | everyone in `members` or `invited`, sorted; the Groups tab queries it |
| `handover` | map `{uid, name}` or null | the member the host offered the role to |
| `archivesAt` | timestamp | end of Dec 19, 2026 in Boston; read-only after |
| `disbanded` | boolean | `false` on create; only the host sets it to `true` |
| `disbandedAt` | timestamp | set by the server when disbanded |
| `proposal` | map or null | the next session someone proposed (below); missing on older groups |

A group's `proposal` map:

| Field | Type | Notes |
|---|---|---|
| `id` | string | also the id of the pod it becomes; no pod may have it yet |
| `startsAt`, `endsAt` | timestamp | same limits as a new pod |
| `building`, `meetingPoint`, `mode` | string | same as a pod's |
| `byUid` | string | who proposed it |
| `yes` | map of uid to name | members who can come; starts as the proposer |
| `no` | map of uid to `true` | members who can't |

Subcollection `groups/{groupId}/messages`, document id = message id (a UUID made on the device).

| Field | Type | Notes |
|---|---|---|
| `authorUid` | string | the sender, a member when sent |
| `authorName` | string | the sender's name in the group's `members` |
| `text` | string | 1 to 500 characters, not just spaces |
| `sentAt` | timestamp | set by the server |
