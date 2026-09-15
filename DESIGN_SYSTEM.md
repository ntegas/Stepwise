# DESIGN_SYSTEM.md — Scaffold

**Not yet populated.** Real content (actual color values, type scale, spacing scale, component visual specs) is written during `/ROADMAP.md`'s DEV-003 (Design System Foundation), per `/PROJECT_STATE.md`. This file exists now so the canonical documentation set (`DEVELOPMENT_PROTOCOL.md` §47) is complete, and so the *structural* requirement — what must be centralized, and which components must exist — is registered ahead of time without jumping ahead on visual decisions.

## What this document will cover, per `DEVELOPMENT_PROTOCOL.md` rule C1

**Centralized tokens** (categories only — no values yet):
- Colors (including semantic colors, light/dark theme)
- Typography
- Spacing
- Shapes / corner radius
- Elevation
- Icon sizing
- Animation
- Component states

**Reusable components** (names only — no visual spec yet):
`StepwiseButton`, `StepwiseCard`, `StepwiseGoalCard`, `StepwiseTaskRow`, `StepwiseActivityRow`, `StepwiseProgressIndicator`, `StepwiseChip`, `StepwiseDialog`, `StepwiseTopBar`, `StepwiseBottomNavigation`, `StepwiseEmptyState`, `StepwiseLoadingState`, `StepwiseErrorState`

**Accessibility and adaptive-UI requirements** the tokens/components must satisfy (`DEVELOPMENT_PROTOCOL.md` C3–C4): scalable text, content descriptions, touch target sizes, contrast, screen reader support, reduced motion where applicable; layouts that work across phones, large phones, tablets, and relevant orientations/window size classes.

## Until this phase

Feature screens must not invent their own visual constants for anything in the categories above, even as a placeholder — use a provisional shared token file if something is needed before this phase is written, and flag it, rather than hardcoding per-screen values that would need hunting down later.
