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

Test the rules on the Firestore emulator before publishing a change. The emulator needs Java 21 (Android
Studio's bundled JDK works) and the Firebase CLI.

## How sync works

| Action | What happens |
|---|---|
| Open Discover / Try again | `refresh()` asks the server (not Firestore's cache) for pods that haven't ended, and upserts them into Room. Offline, it fails and Room keeps the cached pods. |
| Signed out | Discover asks the user to sign in and doesn't refresh, because the rules wouldn't let it read. |
| Create a pod | Saved to Room first, then handed to Firestore. Firestore keeps the write on the device and sends it when the network is back, even after a restart. |
| Cancel a pod | The host confirms, then a transaction sets `canceled` to true on the server copy. Like joining, it needs the network. Canceled pods drop out of Discover for everyone on their next refresh. |
| Join or leave | A Firestore transaction reads the server's copy, checks capacity and the join cutoff, and adds or removes the user's own entry in `members`. Room changes only after the server agrees. Needs the network: offline, the card shows an error and nothing changes. |
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
| `capacity` | number | 2 to 8 |
| `hostUid` | string | Firebase Auth uid of the host, who made the pod |
| `hostName` | string | the host's Google name, 1 to 40 characters |
| `members` | map of uid to name | everyone going, the host included; at most `capacity` entries |
| `mode` | string | `QUIET`, `COORDINATE` or `TALK` |
| `canceled` | boolean | `false` on create; only the host can set it to `true`, and never back |
