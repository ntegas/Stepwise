"use client";

// Phase 0 stub: the global + button (concept §39). It should eventually open
// a contextual quick-create sheet (Task/Goal/Activity/Habit/Session/Project),
// narrowed to the current screen's context. For now it's wired but inert.
export function QuickAddButton() {
  return (
    <button
      aria-label="Quick add"
      onClick={() => {
        // TODO(phase 1): open contextual quick-add sheet
        console.log("Quick add tapped");
      }}
      style={{
        position: "fixed",
        right: 20,
        bottom: 76,
        width: 52,
        height: 52,
        borderRadius: "50%",
        border: "none",
        background: "#4f7cff",
        color: "#fff",
        fontSize: 26,
        lineHeight: "52px",
        textAlign: "center",
        cursor: "pointer",
        boxShadow: "0 6px 16px rgba(79,124,255,0.4)",
      }}
    >
      +
    </button>
  );
}
