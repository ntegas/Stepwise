# DESIGN_SYSTEM.md — Design Tokens & Reusable Components

Implemented in `:core:designsystem` (ARCHITECTURE.md §5: depends on nothing but Compose — no `:core:model`, no domain types, no Room/network). Real values below are DEV-003's deliverable; **all of it is `NOT VERIFIED — ANDROID SDK REQUIRED`** locally (this sandbox cannot resolve the Android Gradle Plugin — `dl.google.com` is blocked — so nothing in this module compiles here). Verified via GitHub Actions instead; see `PROJECT_STATE.md`'s VERIFICATION DEBT section for the current status of that check.

## Provisional visual identity — not a locked brand decision

No brand identity exists yet in `PRODUCT_CANON.md`. The color palette below is a placeholder chosen to read as "focus and forward progress" (a calm indigo/teal pairing) so the theme is usable today — changing it later means editing `theme/Color.kt`, not any screen. Treat every hex value here as provisional; a real design pass can replace them without touching component code, which is the entire point of centralizing them.

## Tokens (`core/designsystem/.../theme/`)

- **Colors** (`Color.kt`, `Theme.kt`) — full Material3 light/dark `ColorScheme` (primary/secondary/tertiary/error, each with its `on*`/`*Container` pair, plus background/surface/surfaceVariant). `StepwiseTheme(darkTheme, content)` is the one entry point every screen wraps in.
- **Typography** (`Type.kt`) — a Material3 `Typography` covering display/headline/title/body/label at large+medium, using the system font (`FontFamily.Default`) — no custom typeface has been chosen.
- **Spacing** (`Spacing.kt`) — `StepwiseSpacing`: none(0)/extraSmall(4)/small(8)/medium(16)/large(24)/extraLarge(32)/extraExtraLarge(48), all `Dp`. Material3 has no built-in spacing scale, so this is the one every screen uses instead of a raw `.dp` literal.
- **Shapes** (`Shape.kt`) — `StepwiseShapes`: a Material3 5-tier `Shapes` (extraSmall 4dp → extraLarge 28dp corner radius).
- **Elevation** (`Elevation.kt`) — `StepwiseElevation`: level0(0dp)/level1(1dp)/level2(3dp)/level3(6dp)/level4(8dp)/level5(12dp), matching Material3's tonal elevation scale.
- **Icon sizing** (`Elevation.kt`) — `StepwiseIconSize`: small(16dp)/medium(24dp)/large(32dp)/extraLarge(48dp).
- **Animation** (`Motion.kt`) — `StepwiseMotion`: short/medium/long duration constants (100/300/500ms) and a standard/emphasized easing curve. No animated component exists yet to consume these — they exist so the first one that does reads a shared value instead of inventing its own.
- **Component states**: covered per-component (enabled/disabled, selected, loading) rather than as a separate token category — Material3's own state layer mechanics are used as-is rather than reimplemented.

## Reusable components (`core/designsystem/.../component/`)

Built now — domain-agnostic, no dependency on a Goal/Task/Activity type:

`StepwiseButton` / `StepwiseOutlinedButton` / `StepwiseTextButton`, `StepwiseCard`, `StepwiseAssistChip` / `StepwiseFilterChip`, `StepwiseDialog`, `StepwiseTopBar`, `StepwiseBottomNavigation` (+ `StepwiseNavigationItem`), `StepwiseEmptyState`, `StepwiseLoadingState`, `StepwiseErrorState`, `StepwiseLinearProgressIndicator` / `StepwiseCircularProgressIndicator`.

**Deliberately deferred**: `StepwiseGoalCard`, `StepwiseTaskRow`, `StepwiseActivityRow` — each is shaped around a domain model (`Goal`, `Task`, `Activity`) that doesn't exist as a Kotlin type yet (`:core:model` is DEV-004's Canonical Data Model). Building them now would mean guessing at fields DEV-004 might define differently. They're built on top of `StepwiseCard` once that model exists — not reinvented as their own card chrome.

No component contains a string literal: every user-facing string is a caller-supplied parameter (`DEVELOPMENT_PROTOCOL.md` rule 25 — i18n from day one; this module has no string-resource/localization layer of its own, the feature module calling it does).

## Accessibility (`DEVELOPMENT_PROTOCOL.md` rules 23–24)

- **Touch targets**: `StepwiseButton`/`StepwiseOutlinedButton`/`StepwiseTextButton` enforce a 48dp minimum height.
- **Content descriptions**: every icon-only affordance (`StepwiseTopBar`'s navigation icon, `StepwiseBottomNavigation`'s items) takes a required content-description parameter — never defaulted to null when the icon is the only affordance for the action. Purely decorative icons (`StepwiseEmptyState`/`StepwiseErrorState`'s illustrative icon) pass `contentDescription = null` deliberately, per Compose's own accessibility guidance for non-interactive decoration.
- **Scalable text / contrast**: inherited from Material3's own `Typography`/`ColorScheme` mechanics (`.sp` units scale with system font size; the color schemes above meet Material3's built-in contrast pairing between `on*` roles and their surface).
- **Screen reader support / reduced motion**: not yet exercised by a real interactive flow — revisited once the first genuinely interactive screen exists (DEV-015) rather than asserted here without a component to test it against.
- **Adaptive UI** (phones/large phones/tablets/orientation, window size classes): not yet addressed — no screen exists yet to lay out. Revisited at DEV-033 (Accessibility / Adaptive UI / Localization) per `/ROADMAP.md`, or sooner if DEV-015's first real screen needs it.

## Until further phases

Feature screens read tokens/components from this module; they never invent their own color/spacing/shape/elevation constant, per `DEVELOPMENT_PROTOCOL.md` rule 20.
