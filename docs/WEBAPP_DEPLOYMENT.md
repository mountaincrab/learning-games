# Deploying the web app to Firebase Hosting

The `webapp/` directory contains a web version of Learning Games (React +
TypeScript + Tailwind + Vite — the same stack as the Crab Do webapp). It is a
purely static site: there is no auth, no database and no Firebase SDK in the
app itself, so the only Firebase product it uses is **Hosting**.

`firebase.json` at the repo root is already configured to serve `webapp/dist`
as a single-page app. What's missing is a Firebase **project** to deploy to —
the one-time steps below create it and link it to this repo.

## One-time setup (needs your laptop / Firebase console)

1. **Create a Firebase project**

   Go to the [Firebase console](https://console.firebase.google.com/), click
   **Add project**, and name it (e.g. `learning-games`). You can disable
   Google Analytics — nothing here uses it. No other products need enabling:
   Hosting is provisioned automatically on first deploy.

2. **Install the Firebase CLI and log in** (on your machine):

   ```bash
   npm install -g firebase-tools
   firebase login
   ```

3. **Link this repo to the project** (run from the repo root):

   ```bash
   firebase use --add
   ```

   Pick the project you created and give it the alias `default`. This writes a
   `.firebaserc` file — **commit it**, so future deploys don't need this step:

   ```json
   {
     "projects": {
       "default": "<your-project-id>"
     }
   }
   ```

## Building and deploying

From the repo root:

```bash
cd webapp
npm install        # first time only
npm run build      # type-checks then builds to webapp/dist
cd ..
firebase deploy --only hosting
```

The CLI prints the Hosting URL (`https://<project-id>.web.app`) when it
finishes. That's it — the site is live.

### Useful local commands

```bash
cd webapp && npm run dev        # dev server with hot reload (no Firebase needed)
cd webapp && npx tsc --noEmit   # type check only
cd webapp && npm run preview    # serve the production build locally
firebase hosting:channel:deploy preview   # deploy to a temporary preview URL
```

## Optional: deploy automatically from GitHub Actions

If you'd like pushes to `main` to deploy automatically:

1. From the repo root run:

   ```bash
   firebase init hosting:github
   ```

   This creates a service account, stores it as a GitHub repo secret
   (`FIREBASE_SERVICE_ACCOUNT_<PROJECT_ID>`), and generates workflow files
   under `.github/workflows/`.

2. In the generated workflow, set the build steps to:

   ```yaml
   - run: npm ci && npm run build
     working-directory: webapp
   ```

Until then, deploys are manual via `firebase deploy --only hosting`.

## Notes on the web app itself

- **Versioning** mirrors the Android app: `vite.config.ts` bakes
  `git describe --tags --always` into the bundle at build time, and the
  Settings screen displays it. Build from a real checkout (not a tarball) for
  a meaningful version string.
- **Audio** is synthesised with the Web Audio API (the Android app ships OGG
  files; the web app ships no audio assets). Browsers only allow sound after
  a user gesture, which is satisfied by tapping a game pad.
- The Android app is landscape-locked; the web app shows a gentle
  "best played sideways" hint in portrait instead.
