# Working on this public repository

Read the roster workflow in README.md before customizing an APK.

- The public default roster is empty. Keep real names only in the ignored local `assets/names.txt`, or in a separate private checkout.
- Use only user-authorized sources. Extract names only, verify uncertain characters and the intended class boundary with the user, and preserve valid same-name entries. Do not infer attendance.
- Never upload user rosters, roster screenshots, customized APKs, attendance exports, databases, signing keys, credentials, or private machine paths to this repository or a public release.
- Do not put real names into source, tests, documentation, commit messages, issue bodies, screenshots, or logs. Synthetic fixtures must be clearly fictional.
- Run `python3 scripts/validate_names.py assets/names.txt` when a local roster exists. Avoid printing the names. Build and verify the embedded asset locally; share a personalized APK only with its user.
- Install the provided local hooks using `git config core.hooksPath .githooks`. Review `git diff --cached` and run the staged snapshot check before committing or pushing. Hooks and ignore rules are guardrails, not proof that arbitrary prose contains no personal data.
- Preserve the existing SQLite database and signing key when maintaining an installed private app. Do not wipe data to apply a roster change. First-install assets seed new databases only; use in-app editing for an already initialized installation.
- Keep downloaded SDK tools and generated/test outputs outside committed source. Never bundle Android SDK components under this project's license.
