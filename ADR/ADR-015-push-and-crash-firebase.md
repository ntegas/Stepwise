# ADR-015: Push & Crash Reporting via Firebase, Alongside Supabase for Data

**Status**: Accepted

**Context**: Needed push notifications (FCM, for reminders and future cross-device notices) and crash reporting, in an architecture whose data backend is Supabase, not Firebase.

**Decision**: Firebase Cloud Messaging and Firebase Crashlytics, used alongside Supabase for data.

**Alternatives considered**: Fully Supabase-adjacent tooling for both (rejected — no mature Supabase-native equivalent to FCM/Crashlytics; would mean building or integrating something less proven for no benefit).

**Reason**: FCM and Crashlytics are mature, free, and orthogonal to the data-backend choice — there's no reason to avoid Google's tooling for these specific concerns just because the data backend is Supabase (ADR-002). These are not mutually exclusive choices.

**Consequences / Risks**: Two vendor dashboards/operational surfaces instead of one.

**Reversibility**: Reversible independently of the data model — these are edge concerns, swappable on their own.

See `ARCHITECTURE.md` §3, §18, §26.
