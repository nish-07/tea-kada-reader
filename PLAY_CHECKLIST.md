# Before putting the reader on Google Play

Pilot shops can use the APK directly (no Play review). For Play, do all of these first.

- [ ] Public privacy policy page (use PRIVACY.md as the base) and its link in the Play listing and inside the app.
- [ ] Data safety form: collected data = financial info (payment amount), name (payer name); purpose = app functionality only; encrypted in transit = yes; users can ask for deletion = yes; not shared; not sold.
- [ ] Financial features declaration (the app deals with payments).
- [ ] In-app disclosure before the settings screen with Agree and Not now: already in `MainActivity.renderConsent()`. Re-read the wording with the final policy text.
- [ ] Keep `Allowlist.READ_SMS_AND_EMAIL_APPS = false`. Turning it on means reading all SMS or Gmail notifications and will fail review.
- [ ] Prepare a short screen recording showing the disclosure, the permission screen and the app working without the reader, for the review team.
- [ ] Confirm every package name in `Allowlist.kt` on a real phone before adding a new app.
- [ ] Release signing key kept offline; targetSdk kept up to date.
- [ ] Tobacco / age-restricted items: check the listing questionnaire.
