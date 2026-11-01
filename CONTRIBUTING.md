# Contributing

Thank you for helping. Orbit is a small personal project, so keep changes focused and explain what they are for.

- **Run both test suites** before you open a pull request:
  ```
  node --test app/src/test/js/*.test.js
  ./gradlew :app:testDebugUnitTest
  ```
  Add a test for a bug you fix or a rule you add. Native tests run against recorded responses in `app/src/test/resources/feeds`, never the live services.
- **Never commit keys.** Keys live only in your git-ignored `local.properties`. Run `tools/check-secrets.sh` before every commit and push; it must say `secrets: clean`. Do not paste keys into issues or pull requests either.
- **Data, not code.** Countries, languages, channels, festivals, hand-offs, streaming apps, loves and place names are data files (see "Make it yours" in [docs/SETUP.md](docs/SETUP.md)). Prefer extending data over adding a special case in code. `interests.json` is generated: change `tools/interests/catalogue_data.py` and run `python3 tools/interests/build_catalogue.py`.
- **Performance matters.** Orbit runs on low-end TVs. If you change a page, measure it on a real TV if you can (`tools/measure.sh`, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)).
- **Assets and licences.** Only add fonts, images, sounds or data whose licence allows redistribution, and add them to [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) with the attribution they require.
- **Style (optional).** User-facing text is plain and short. The project avoids the em dash character in text and code.

By contributing you agree that your contribution is released under the MIT License of this repository.
