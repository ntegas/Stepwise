---
name: android-kotlin-compose-pitfalls
description: >
  Hard-won checklist of real recurring mistake classes in Kotlin/Jetpack Compose/Room/Gradle
  Android development, distilled from months of incident post-mortems on a production app.
  Use this whenever writing, reviewing, or fixing Android/Kotlin/Compose/Gradle/Room code,
  not just when something has already broken — most of these are cheaper to avoid than to
  debug. Trigger on: writing or editing any .kt/.kts file in an Android project; adding or
  changing a Room @Entity/@Dao/migration; writing or reviewing Jetpack Compose UI (screens,
  dialogs, bottom sheets, lists); touching Gradle/version-catalog/dependency config; doing a
  mass find-and-replace across a Kotlin codebase; writing backup/restore/sync/import-export
  logic; writing a BroadcastReceiver, WorkManager worker, notification, or any code reachable
  from outside the main Activity; fixing "the same kind of bug a second time"; or reviewing a
  diff for an Android app before calling it done. Also use it proactively before saying a
  change is finished — it is as much a pre-submission checklist as a set of code rules.
---

# Android / Kotlin / Compose — recurring-mistake checklist

Every rule below comes from a real bug that actually shipped or wasted real debugging time —
not a hypothetical. The point isn't to follow them as superstition; it's that each one names a
specific, checkable thing worth looking for, cheaper to check now than to debug later. Skim the
section headers for the kind of work you're doing, then actually look for the thing, don't just
pattern-match the words.

## 1. Kotlin / K2 compiler traps

These compile-time traps waste a full edit-compile cycle each if you guess instead of check.

```kotlin
// ❌ java.time.ChronoUnit                 ✅ java.time.temporal.ChronoUnit
// ❌ localDate1 < localDate2              ✅ localDate1.isBefore(localDate2) / .isAfter(...)
//    (LocalDate has no </> operators)
// ❌ "${-nullableLong}"                   ✅ val x: Long = nullableLong; "${-x}"
//    (unary minus on a nullable is an ambiguous overload)
// ❌ PaddingValues(vertical = 8.dp,       ✅ pick ONE axis form: either (vertical=, horizontal=)
//      bottom = 4.dp)                       or (top=, bottom=, start=, end=) — not mixed
// ❌ Modifier.padding(horizontal = 8.dp,  ✅ Modifier.padding(horizontal = 8.dp)
//      bottom = 4.dp)                        .padding(bottom = 4.dp)   (chain two calls, or
//    (same mixed-axis restriction as         pick one consistent axis form — the overload set
//     PaddingValues above, but easier         is (start/top/end/bottom), (horizontal/vertical),
//     to miss because the call reads          (all), or (PaddingValues); none of them accept
//     like a single natural sentence)         a horizontal/vertical name plus one side name)
// ❌ Icon(Icons.Filled.ArrowBack, ...)    ✅ import ...icons.automirrored.filled.ArrowBack
//    on a recent Compose BOM                 Icons.AutoMirrored.Filled.ArrowBack
//    (ArrowBack/Send/List/Reply/ExitToApp/Sort/FormatListBulleted/Label and similar
//     directional icons are deprecated non-mirrored; check whether the pinned BOM
//     still exposes the plain one before assuming either form compiles)
// ❌ FlowRow { ... } with no opt-in       ✅ @file:OptIn(ExperimentalLayoutApi::class) before
//    (K2 treats a missing opt-in on an       `package`, or @OptIn(...) on the function —
//     experimental API as a hard error,      same for DatePicker/DatePickerDialog and any
//     not a warning)                         other @ExperimentalMaterial3Api component
```

- **Every Compose icon needs its own import.** `Icons.Filled.X` / `Icons.AutoMirrored.Filled.X`
  is not auto-resolved from a wildcard you already have elsewhere in the file. Each icon is an
  *extension property* on `Icons.Filled`/`Icons.Outlined`/etc, not a real member — so the import
  is still required even though the call site writes it in qualified form (`Icons.Filled.
  CheckCircle`), and it is required *per icon name*, independently, for every style it's used in
  (`Icons.Filled.CheckCircle` and `Icons.Outlined.CheckCircle` are two separate extension
  properties needing two separate imports, both named `CheckCircle`). Don't delete an icon
  import just because grep shows the simple name `CheckCircle` never appears unqualified in the
  file — the qualified form `Icons.Filled.CheckCircle` still depends on that same import, and
  removing it breaks the build with "Unresolved reference" at every qualified use site (this
  exact mistake was made once already distilling this skill's own first draft).
- **`material-icons-core` only bundles a small curated subset of icon names** (`Home`, `Add`,
  `Search`, `List`, `Star`, `CheckCircle`, `ArrowBack`, `Settings`, `Person`, and a few dozen
  others like them) — it is NOT "all the Material icons minus the extended styling." A name
  that sounds just as common (`Flag`, `Edit`... check per-icon) can still be unresolved with
  "Unresolved reference 'X'" even though `Icons.Filled.Home` right next to it compiles fine,
  because `Home` is in core and `Flag` only exists in `material-icons-extended`. Before using a
  new icon name, either confirm it against the module's already-working icon imports, or add
  `material-icons-extended` deliberately (it is a much larger artifact — weigh that against
  just picking a different icon from the set already proven to resolve in this codebase).
- **Extension functions need their own import even when they feel obvious in context.** A
  function that reads like a member (`view.positionInParent()`, `date.toDisplayString()`) may
  be a top-level extension living in a completely different package.
- **Don't call `Object.function()` from memory.** Check whether the function is actually a
  member of that object/companion, or a top-level function in the same file/package, before
  writing the call — guessing wrong produces `Unresolved reference` that looks like a typo but
  isn't one.
- **Don't recall enum values from memory.** Open the actual enum definition before writing
  `EnumType.VALUE` — a value that "should" exist per the feature's intent may not actually be
  in the enum (it may have been renamed, or never added).
- **A `ColorScheme` builder (or anything similar) with ~30+ named arguments can hit a real
  Kotlin compiler limit on named-argument count**, failing with errors like "Named arguments
  are prohibited for function types" scattered across unrelated-looking parameters. If you're
  about to enumerate every documented field of a huge builder call, first check whether there's
  already a working example of that exact call in the repo and match its field count/shape
  instead of trusting the full API surface from docs/memory.
- **`kotlinOptions { }` is the legacy Gradle DSL.** Current Kotlin Gradle plugin versions use
  `kotlin { compilerOptions { } }` — check the project's actual Kotlin/Gradle plugin version
  before assuming either form is current.

## 2. Compose / Material3 UI pitfalls

- **A `LazyColumn`/`Column` that already sets `verticalArrangement = spacedBy(N.dp)` already
  adds that spacing before and after every item.** Before giving a newly-added `item{}` its own
  `Modifier.padding(top=/bottom=)`, read the parent's `verticalArrangement`/`contentPadding` —
  stacking your own padding on top of `spacedBy` doubles the gap.
- **Divider/separator/outline-style color tokens are for lines and borders only.** They're
  typically a very low-alpha tint and are nearly invisible as text color. For muted-but-readable
  text, use whatever your design system calls its secondary/tertiary label/text token instead —
  never a divider token, even if it "looks close enough" in a quick preview.
- **A `ModalBottomSheet` with scrollable content inside it (a `LazyColumn`, a wheel/number
  picker) can have the sheet's own drag-to-dismiss gesture steal the drag from that content.**
  The sheet's gesture detector gets first refusal on the scroll event (it runs before the child
  can consume it), so wrapping the child in your own `NestedScrollConnection` to "give back" the
  gesture does not fix this — the dismiss gesture never reached the child's connection to begin
  with. The real fix is `confirmValueChange = { it != SheetValue.Hidden }` on the sheet state,
  which blocks the dismiss outright. That also silently blocks the scrim tap and the system back
  button (both route through the same state-change path, not a separate `onDismissRequest`), so
  once you do this, an explicit Cancel/Close button inside the sheet becomes mandatory, not
  optional.
- **`rememberModalBottomSheetState()` defaults to `skipPartiallyExpanded = false`.** Tall sheet
  content (a custom keypad, a long form) can get stuck in a half-open `PartiallyExpanded` state
  that clips the bottom rows/buttons. This can surface in an *old*, previously-fine sheet
  component the first time its content happens to get tall enough to expose the default.
- **A global modifier fix on a shared wrapper (an app-wide `NavHost`, an outer `Scaffold`) has
  to be checked against every other permanently-visible thing in that same tree, not just the
  screen that motivated the fix.** `imePadding()` added to fix a form whose fields hid behind
  the keyboard can simultaneously push visible space above a bottom nav bar that doesn't itself
  move with the keyboard, exposing a growing gray gap. Ask explicitly: what else lives in this
  shared tree, and does it react to this modifier the same way the target screen does?
- **Nested `Scaffold`s (an outer app-level one plus a per-screen one) can both apply the same
  top-bar/status-bar inset**, doubling the padding at the top of every screen. Exactly one level
  should own a given inset — set the other to a zero/empty `WindowInsets` explicitly rather than
  leaving both on defaults.
- **A decorative, no-op `onClick = {}` / `onCheckedChange = {}` is worse than no control at all** —
  it promises an action that silently does nothing. Treat "grep this screen for an empty
  lambda handler" as part of reviewing *existing* controls during any screen revision, not only
  something to avoid when writing brand-new ones; these survive multiple review passes
  unnoticed specifically because they compile and look finished.
- **When a control becomes visible more often or for longer than it used to, as a side effect of
  an unrelated change, re-verify its actual tap target and z-order.** "It was already there and
  nobody complained" is not evidence it worked — it may have been positioned under another
  element the whole time, and simply wasn't visible/tappable long enough for anyone to notice.

## 3. Gradle / build / dependency pitfalls

- **Any Room `@Entity` or schema change needs a version bump + migration — even a no-op
  migration if the SQL text didn't actually change.** Room computes an identity hash from the
  schema; a mismatch crashes with "Room cannot verify data integrity" regardless of whether the
  underlying SQL text changed.
- **After a large, system-wide change (a theme rewrite, a DI graph change, anything touching
  many files in one pass), do a clean or `--rerun-tasks` compile before calling it done.** An
  incremental compile that returns empty output after a long series of small edits is not proof
  the project builds from scratch — caching can hide a real error that a clean build (or CI, or
  the next person's machine) will hit immediately.
- **A mass find-and-replace (`sed`/regex) across a codebase needs a second pass before you trust
  it.** If some call sites already wrote the target function/class fully-qualified
  (`androidx.compose.foundation.shape.RoundedCornerShape(...)`), a naive replacement of the bare
  name leaves an orphaned, now-broken prefix in front of the new reference at exactly those
  sites — the ones that were already "more correct" than average get silently broken. Before
  calling a bulk replacement finished, grep for existing fully-qualified usages of the same
  symbol that your pattern might match as a substring.
- **Know whether your build environment can reach the network before adding a new dependency.**
  A new `implementation(...)` that isn't already in the local/offline cache fails with "Could
  not find" and a red build that has nothing to do with the code you wrote.
- **`--rerun` on a specific task vs. `--rerun-tasks` for the whole project** are very different
  costs when debugging a hung or flaky test — know which one you actually need before reaching
  for the expensive one.

## 4. The discipline that actually prevents repeating a bug

This is the part that matters most and generalizes furthest. Individually these read like
common sense; the value is in actually running the checklist instead of skipping straight to
the fix that's already in your head.

**Before writing the fix:**

1. **Don't trust a comment or a status claim without checking the code it describes.**
   "Single source of truth," "all cases covered," "this path is no longer used" are statements
   of intent, not fact — grep the actual definitions and call sites before relying on them.
2. **Find every entry point that shares the same semantics before fixing just the one that was
   reported.** Create / edit / import / restore / merge / filter / search / delete / complete —
   if one of these has a bug, the sibling operations on the same data usually share the same
   gap. State ONE invariant that must hold across all of them, then check each path against it.
3. **If you can't write "what must always be true after this fix" as one sentence, the task
   isn't understood well enough yet to write the fix.**
4. **When behavior depends on a type or state, write the full type × condition → behavior
   matrix first, get it right, then implement from that one matrix.** Scattered local
   `if`/`else` per call site for the same decision inevitably drifts into contradictions between
   call sites over time.
5. **Never create a second source of truth for one concept.** One canonical registry/list;
   anything else derived from it mechanically is fine, a second independently-maintained copy is
   not.
6. **"Works for every case I tried" isn't the same as "works for a case that doesn't exist
   yet."** For anything meant to generalize (a new type, a new language, a new entity kind),
   explicitly ask what production code would have to change if one more were added — if the
   answer is "nothing, it's data-driven," that's the proof; if it's "update this other list
   too," you have a second source of truth you haven't noticed yet.
7. **A test must not validate the system against itself.** If production code builds its list
   from source X, and the test iterates that same X to build its expectations, the test cannot
   ever catch X being incomplete — the expected value has to come from somewhere independent of
   the mechanism under test.
8. **Any critical fix gets a negative/adversarial test, not just a happy-path one.**
9. **Prove a new test actually catches the bug it's meant to catch**: temporarily revert the
   fix and confirm the test goes red. A test that passes with or without the fix proves nothing
   and will mask a real regression later.
10. **A failing test is not automatically a wrong test.** Before changing it, work out whether
    it's actually wrong, has found a real defect, has state leakage between tests, is
    order-dependent, or is a genuine regression. Resetting shared/global state only inside test
    `setup()` can make the suite green while leaving the exact same lifecycle bug in production
    (which never resets that state) — don't let a green suite stand in for that investigation.

**While designing the fix:**

11. **Trace the full lifecycle of any singleton, global, or cache you're touching**: where it's
    created, where (if ever) it's reset, what happens on a fresh app launch vs. a relaunch vs.
    logout/reset/import/restore, and whether tests actually isolate it between runs. Counters,
    caches, and coroutine/job queues are the usual place this bites.
12. **Any cache needs an invalidation story, and a check for "can this be built before the data
    it depends on even exists yet" (early init racing real data).**
13. **Don't infer where a piece of data came from based on its displayed text.** If the schema
    doesn't store an explicit origin/type/source field, the "is this one of ours or imported"
    rule has to be written down as an explicit, intentional product rule — not derived
    implicitly from string matching, which breaks the moment the string is localized or edited.
14. **A local fix must not leave a different rule in a different code path for the same
    concept.** If you fixed how one call site decides something, every sibling call site for
    the same decision needs the identical rule, not an independently-reasoned approximation of
    it.
15. **A UI promise (a button label, a toast, a status word like "Scheduled"/"Saved"/"Synced", a
    checkmark/badge) is a contract with the actual data state, not just a string to display.**
    Before shipping it, write down "what does the user seeing this imply about data state" and
    verify the code produces exactly that — not something merely adjacent. "Scheduled" has to
    mean the underlying alarm/job genuinely exists, not just that the chosen time is in the
    future; launching a dialer/SMS/mail intent is not the same event as a call or message having
    actually happened; a successful `schedule()` call, a launched intent, a function returning
    `true`, or even a green CI run are *signals*, not proof of the business event itself.
16. **One action or rule needs one identical semantics at every entry point it's reachable
    from** — every screen, tab, shortcut/cheat-sheet, notification action, widget, quick-action,
    import path, restore path, and background receiver that can trigger it. Before changing such
    an action, build the table of entry point → handler → side effect across literally all of
    them, and search for the handler by its *name*, not with a naive `Class.method(` text grep —
    a call that's wrapped or split across lines can dodge that exact substring.
17. **Any path that can start somewhere other than your app's normal entry point — a
    `BroadcastReceiver`, boot-completed, a notification action, a scheduled worker, a widget, a
    deep link, a share-intent, a cold process restart — has to be designed as a cold start.**
    Don't assume any in-memory singleton, cache, or one-time `init()` has already run; trace the
    actual chain from "process not running" through "component starts" to "data operation" to
    "write confirmed." Async work inside a `BroadcastReceiver` specifically needs `goAsync()`
    with `finish()` called in a `finally` block, or the OS can kill the process mid-coroutine
    before the work finishes.
18. **A denormalized copy of data baked into something long-lived (`PendingIntent` extras, a
    notification's text, an export file, a cached row, a column mirrored into another table)
    can go stale by the time it's actually used.** Ask explicitly: do I need the value as of the
    moment I'm saving this, or the current value as of the moment this is eventually used? If
    the latter, store a stable id and re-read the canonical value at use time instead of a
    frozen copy.
19. **An automatic default-selection rule (which phone/email/contact method is "primary," which
    entity is the default, which language/format to assume) must live in exactly one policy
    function.** A `primary ?: first` fallback copy-pasted into five call sites will eventually
    diverge between them — and the automatic rule must never silently override something the
    user explicitly, deliberately chose.
20. **Anything your app controls outside its own database — OS alarms/scheduled jobs, shown
    notifications, files on disk, a linked system calendar or contacts entry — is still "your
    data."** Every create/update/complete/delete/wipe/restore/merge path needs to keep that
    external state in sync too. The right test isn't "are the rows gone from the database," it's
    "is there nothing left over that will later show something stale or wrong."
21. **Correct business logic can still produce wrong behavior purely from platform
    constraints**: notification action-button count limits, receiver lifecycle, `PendingIntent`
    identity semantics (extras typically don't participate in equality checks), process death,
    background-execution limits, runtime permissions, timezone changes, battery/Doze
    restrictions. Check each one explicitly before shipping anything that runs outside the
    foreground UI.

**Testing and process hygiene:**

22. **Flaky-looking test infrastructure deserves real diagnosis before being dismissed as
    "flaky."** A framework that hangs, never reaches idle, passes alone but fails inside a
    suite, or only fails after a specific other test ran first — get a thread dump, check
    stderr, check timing, before assuming it's infra noise. Equally, don't change production
    code to chase an unproven infra theory; if it really is an infra limitation, write that down
    as a known finding rather than silently working around it in prod code.
23. **Any long-running operation — a build, a test suite, an instrumented/UI test, an emulator
    boot — needs an explicit timeout or watchdog.** Don't let something hang silently and
    unnoticed for hours.
24. **After closing a small fix, go back through the ORIGINAL list of findings from whatever
    audit or batch prompted it and mark each one explicitly**: fixed, confirmed passing, moved to
    a different batch, needs a product decision, or still open. Don't silently start the next
    unrelated unit of work with some of that list unresolved and untracked.
25. **Anything noticed outside the current task's scope while working gets written down — short
    description, severity, which future piece of work it belongs to — never left as something
    you noticed but didn't record anywhere.**

**PRE-FIX, as one checklist to actually run through before touching code:** What invariant am I
fixing? Which other paths share it? Is there a second, duplicate source of truth hiding here? What
are the conflict cases? How does this behave with a brand-new type/value that doesn't exist yet?
What's the adversarial case? What test would fail against the *current*, unfixed code? Does my
planned test accidentally validate the system against itself? Am I about to "fix" this by only
touching test setup, which would mask a production bug rather than fix it?

**POST-FIX, before calling it done:** List every production file you actually changed. Confirm
there's no incidental extra edit riding along. Review every usage of whatever API you changed.
Positive test + negative test + a regression test. Re-run the actual operation a second time (a
second restore/import/create/delete — not just once), since some of this class of bug only shows
up on repetition. Full test suite, lint, build. Separately log any new finding you noticed along
the way, without silently fixing it outside the scope you were given.

## 5. Data-integrity / persistence patterns

- **A "whole snapshot" update function (`updateThing(wholeNewState)`) is ambiguous between
  "this is the user's deliberate edit" and "this value just happened to be sitting in the
  snapshot when it was captured."** When more than one independent writer can touch parts of the
  same aggregate, prefer `updateThing(base, edited)` and merge only the actual diff onto the
  *current* canonical state (match any child lists by stable id, not position) — not
  last-writer-wins on the entire object. A field that's forgotten in that merge function quietly
  degrades to "the edited value always wins for that field" — correct by accident, not by
  design — so adding a new field to an aggregate means explicitly adding it to the merge
  function too, not just the data class.
- **Keep canonical state (independent aggregates with their own real writers) distinct from
  projections or denormalized copies (derived and reconciled FROM canonical state).** Never let
  a projection become a second writer of the thing it's supposed to be derived from.
- **A write isn't "done" until its durable commit is confirmed, not merely enqueued.** A screen
  that navigates away or re-reads immediately after calling a save function, before that commit
  is actually confirmed, can race its own read; a double-tap on the same control before the
  first write confirms can create two records. Disable/block the triggering control while a
  critical write is in flight.
- **If a commit can fail, in-memory state can't remain the only source of truth afterward.**
  Resync from the durable store on a write failure and tell the user, rather than silently
  leaving an in-memory copy that has diverged from what's actually persisted.
- **Backup/export should read from one consistent transaction/snapshot of the durable store, not
  from scattered in-memory or optimistic state spread across different writers.** What memory
  shows right after a restore and what a backup/export produces should go through the exact same
  snapshot-building code path.
- **Restoring from a backup is not "delete everything and replace with the file's contents."**
  Restoring into an empty store can be 1:1; restoring on top of existing data needs a data-safe
  merge by stable id — something that only exists locally is kept, something that only exists in
  the backup is added, a field identical in both is a no-op, and a field that differs in both
  needs an explicit, written-down winner rule. Don't invent a "most recent wins" rule from an
  unreliable or missing last-modified timestamp, especially for child records that may not even
  have one.
- **A backup/restore format needs an explicit version check validated BEFORE any write starts.**
  Importing a newer or unrecognized format should refuse outright, not attempt a partial,
  best-effort import of a schema you don't actually understand.
- **Adding a new persisted field or entity means grepping for every place that serializes or
  reconstructs the whole aggregate it belongs to** — not just the main DB mapper, but backup,
  restore/merge, any "convert/migrate this record into another type" path, and any full-graph
  audit/export code. A new field silently vanishes on exactly those paths if they're not updated
  too.

## 6. Security checklist for an app that stores user data locally

Run this whenever touching storage, intents, exported components, or anything auth-adjacent
(biometrics, a local PIN, "protected"/"private" content).

- **Never log PII** (names, phone numbers, emails, free-text notes, anything personal) through
  `Log.*` — on-device logs are readable by other apps and over ADB.
- **Any externally-sourced URI handed to `ACTION_VIEW` or any other intent needs a scheme
  whitelist** (`http`/`https` plus a short, explicit, deliberate list) — block `file:`,
  `content:`, `intent:`, `javascript:`, and `data:` explicitly, especially for a value that could
  have come from an imported, untrusted file (a contact card, a backup, a shared link).
- **Clean up exported temp files** (share exports, imported attachments) from cache after use —
  don't leave them sitting around indefinitely.
- **Default to `android:allowBackup="false"`** unless there's a specific, deliberate reason to
  allow it — otherwise an ADB backup can exfiltrate a local database wholesale.
- **No WebView if you can avoid it**, and if you truly can't, never combine
  `setJavaScriptEnabled(true)` with `addJavascriptInterface`.
- **Every manifest component's `exported` attribute should be a deliberate choice, not
  whatever the default happens to be** for that component type and API level.
- **A security-indicator UI — a lock icon, a blur, a "Protected" badge — is specifically
  dangerous because a logic bug makes it silently decorative while still visually looking like
  it's working.** Trace any such "reveal if authenticated" path all the way to the actual
  authentication API call; don't assume a function "looks like" it calls a biometric prompt or
  equivalent just because its shape resembles one. This is exactly the kind of bug a normal
  review pass tends to miss, because the UI gives every visual signal that it's protected.
- **A cast like `someContext as? SomeActivitySubtype` can silently yield `null`** if anything
  upstream in the call chain wraps the real `Context`/`Activity` (a localization wrapper, a
  theming wrapper, a test harness) — the cast fails quietly rather than crashing, so the bug
  looks like "this feature just does nothing." If you find one broken cast of this shape, grep
  the whole codebase for the same unsafe-cast pattern; it's rarely isolated to one call site.
- **Rate-limit any local PIN/passcode gate with an escalating lockout**, and base that lockout's
  timer on a monotonic, boot-relative clock (not wall-clock time) — wall-clock time is something
  the user can trivially change forward or backward to bypass the lockout entirely.
- **A "turn on this protection" toggle should only persist as enabled after a real, successful
  authentication round-trip** (the actual `onSuccess` callback), never optimistically on tap —
  and always provide a recovery path for a forgotten local secret that doesn't amount to
  permanent data loss, scoped to resetting only the lock itself, never to silently deleting the
  underlying data it was protecting.
- **Add double-tap protection to any "confirm" action that creates a new record with a
  client-generated id.** A fast double-tap before the UI closes or disables itself can create
  two records with two different ids before either one has a chance to prevent the second.

## 7. detekt/ktlint findings — recognize fast, fix right

These five keep recurring, project after project, because each looks like a one-off until you've
hit it twice. Check for all five *before* pushing a new or heavily-edited file, not just after CI
reports them — every one below is cheaper to self-check than to burn a CI round-trip on.

- **`@OptIn`/`@file:OptIn` is required per file, not per "kind of file you've already handled
  once."** A design-system component's own optional parameter (e.g. `TopBar`'s `scrollBehavior`,
  any `DatePickerDialog`/`AlertDialog`/`FilterChip` usage) carries its experimental-API
  requirement to *every caller*, regardless of whether that parameter is ever supplied. Six new
  screen files in one module missing this is a single mechanical oversight, not six separate
  bugs — but it still means: before pushing a batch of new Compose screen files, grep the whole
  batch for the same Material3/experimental symbols your other, already-working screens use, and
  confirm each new file carries the same `@file:OptIn`, rather than assuming "I did this for the
  pattern already, so it's covered."
- **ktlint's function-signature/body-expression-wrapping rule is exact-character-count-driven,
  not shape-driven.** Two near-identical one-liner functions (e.g. two sibling repository methods
  that differ only by a type name a few characters longer/shorter) can require *opposite*
  formatting: one must stay split across two lines because the combined signature+body exceeds
  the line-length ceiling, the other must be joined onto one line because it doesn't. Don't
  pattern-match "this looks like that other function, so it gets the same layout" — compute or
  count the actual combined length before deciding to split or join.
- **detekt's `MatchingDeclarationName` applies only when a file has exactly one top-level
  class/object/function-holding declaration** — a file bundling two or more such declarations
  (the established convention for a small cluster of related read-side Use Cases, e.g.
  `GoalReadUseCases.kt` holding four classes) is exempt, but a file that was trimmed down to hold
  only one no longer is. If a grouped-use-cases file ever ends up with just one class in it
  (after a later edit removes the others, or because it only ever needed one from the start),
  rename the file to match that one declaration's exact name.
- **A function with more than two `return` statements (`ReturnCount`) is usually two or more
  sequential `x ?: return` / `val x = ... ?: return` null-coalescing guards that can be merged.**
  The fix is almost always: extract each guarded value as a plain nullable `val` first (no early
  return yet), then add one `if (a == null || b == null) return` combining as many of the
  original guards as are logically independent of each other (not dependent on one already being
  non-null) — collapsing N sequential one-value guards into fewer combined-condition guards,
  rather than restructuring the function's actual logic.
- **After any refactor that replaces, extracts, or removes a usage, grep the file's own import
  list against what's actually left**, not just the lines you touched. Extracting a chunk of a
  function into a new file/function, or replacing a component call with a different one, routinely
  leaves behind an import for a symbol nothing in the file references anymore — ktlint's
  unused-import check catches it, but only once that file's own ktlint task is actually reached in
  CI (which, in a large multi-module push, can be several iterations after the file was changed).

## When you're about to say "done" / "it builds" / "tests pass"

That statement means: a clean (not cached) build passed, the specific operation that was broken
was re-verified doing the right thing (not just compiling), and — if this was a UI change — the
actual interaction path was walked through at least mentally end to end (entry point → every
state → feedback on success and failure → the edge cases: empty, duplicate, very long input,
deletion with confirmation). "Compiles" and "doesn't crash" are necessary, not sufficient.
