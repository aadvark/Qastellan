# Qastellan

Free, open-source malware scanner for Android. Qastellan protects the phone and
the integrity and privacy of its software. Everything is checked on the phone;
nothing you have is uploaded.

**Status:** iteration 1 of 3 — engine and definitions pipeline. Not released yet.

## What is here

| Part | Folder | What it does |
| --- | --- | --- |
| Definitions pipeline | `pipeline/` | Builds the signed daily threat list from ClamAV, MalwareBazaar and MVT indicators |
| Daily workflow | `.github/workflows/definitions.yml` | Runs the pipeline every day and publishes to the `definitions` release |
| App | `app/` | Scan engine, risk scoring, real-time protection, update checks (with a test screen) |
| App workflow | `.github/workflows/app.yml` | Unit tests and debug APKs on every push |

The app checks for new definitions and a new app version **every time it
starts**, and checks definitions again once a day in the background.

## Build flavours

- `fdroid` — for F-Droid. Updates come from F-Droid; the app only says when one is waiting.
- `github` — for downloads from GitHub Releases. The app compares its version with the latest release.

## One-time setup

1. `python pipeline/tools/generate_signing_key.py`
2. Put the private key in the repo secret `QASTELLAN_SIGNING_KEY`.
3. Put the public key in `app/src/main/res/values/definitions_key.xml`.
4. Optional: a free [abuse.ch Auth-Key](https://auth.abuse.ch/) in the secret `MB_AUTH_KEY`.
5. Run the "Daily definitions" workflow once from the Actions tab.

## Development

```
cd pipeline && pip install -r requirements.txt && pytest -q
./gradlew testFdroidDebugUnitTest assembleFdroidDebug
```

Third-party data sources and their licences: see [NOTICE.md](NOTICE.md).

## Licence

Qastellan is free software under the [GNU General Public License v3.0](LICENSE) (GPL-3.0-or-later).
© Lee Wen Yang (Mike), Kith + Keen. Made in Singapore.
