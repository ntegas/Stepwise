# Google Play Release Checklist

Verified against Google's currently published policy (checked live, since these requirements change year to year — do not rely on memory when revisiting this doc).

## Account & identity

- Google Play Console developer account: one-time $25 fee, identity verification.
- The user has published apps before, so Play Console account setup itself doesn't need hand-holding here — listed for completeness/tracking only.

## Application identity (decide before the first build — irreversible after upload)

- **Application ID / package name**, e.g. `com.ntegas.stepwise`. Cannot be changed once published.

## Target API level

- New apps must **target Android 16 (API level 36)** starting **August 31, 2026** to be accepted for submission (an extension to **November 1, 2026** is available on request).
- Apps already live must target at least **API level 35** to remain available to new users on devices running a newer OS.
- Missing the deadline blocks publishing updates entirely.
- Source: [Target API level requirements for Google Play apps](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)

## Closed testing (new personal developer accounts)

- Accounts created after **November 13, 2023** must run a closed test with **at least 12 testers**, opted in **continuously for 14 days**, with genuine engagement (installs that go unused don't count; emulators/bots don't count) — before production access is granted.
- This is a real scheduling constraint: line up ~12 real testers in advance so the 14-day clock isn't the thing blocking the release date.
- Organization accounts (registered legal entity) are exempt.
- Source: [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

## Data Safety & privacy

- Stepwise stores personal data: email (auth), and per the concept potentially weight and financial goal values. The **Data Safety** form must declare this accurately.
- A **privacy policy URL is mandatory** for the store listing.
- Account/data deletion: Play policy requires apps with account creation to offer a way to delete the account and associated data, both in-app and via a web path — needs a plan (e.g. a Supabase Edge Function + a hosted deletion-request page), not just an in-app "delete account" button.

## Content rating

- Complete the content rating questionnaire (Stepwise is a personal productivity app — expect the lowest rating tier, but the questionnaire must still be filled out).

## Store listing assets

- App icon, feature graphic, phone screenshots (current minimum counts/sizes are set in Play Console at listing time — verify there, not here, since asset specs change more often than policy).
- Short description, full description — in every language the app ships in (ties to the i18n decision — plan for at least Russian + English listings).

## Pricing & distribution

- Deferred per the user's decision (monetization decided post-build) — but the listing still requires *some* declaration (free at launch) and a target country list before submission.

## Not yet decided (tracked, not blocking this phase)

- Final monetization model (possible ads later).
- Legal entity / jurisdiction for the privacy policy and terms — to be resolved before the public production release, not before development starts.
