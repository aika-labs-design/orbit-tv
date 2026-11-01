# Security

## Reporting a problem

If you find a security problem in Orbit (for example a way for a web page, an embedded video or another app on the TV to drive the app's bridge, read stored profiles or reach your keys), please report it privately through GitHub's **Report a vulnerability** button on this repository's Security tab, rather than in a public issue. Include what you found, how to reproduce it and which Fire TV and Fire OS version you used. This is a personal project maintained in spare time, so replies may take a few days.

## Keys

- Orbit uses your own TMDB and YouTube Data API keys. They belong only in your git-ignored `local.properties` and reach the app through the build.
- Never commit a key, paste one into code, data files, docs, issues or pull requests. Run `tools/check-secrets.sh` before every commit and push; it fails if a key value from `local.properties`, a Google API key pattern, or a tracked `local.properties` or `local-profiles.json` appears in any tracked file or commit.
- The keys are built into the APK you make. Do not share that APK: anyone who has it can read the keys out of it.
- Restrict your YouTube key to the YouTube Data API v3 in the Google Cloud console. If a key leaks, revoke it at the service and make a new one.
