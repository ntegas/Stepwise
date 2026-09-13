"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/today", label: "Today" },
  { href: "/goals", label: "Goals" },
  { href: "/plan", label: "Plan" },
  { href: "/progress", label: "Progress" },
] as const;

export function TabBar() {
  const pathname = usePathname();

  return (
    <nav
      style={{
        position: "sticky",
        bottom: 0,
        display: "flex",
        borderTop: "1px solid #2a2b30",
        background: "#0b0c0f",
      }}
    >
      {TABS.map((tab) => {
        const active = pathname?.startsWith(tab.href);
        return (
          <Link
            key={tab.href}
            href={tab.href}
            style={{
              flex: 1,
              textAlign: "center",
              padding: "14px 0",
              color: active ? "#ffffff" : "#8a8b90",
              fontWeight: active ? 600 : 400,
              textDecoration: "none",
              fontSize: 13,
            }}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
