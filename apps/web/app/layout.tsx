import type { ReactNode } from "react";
import { TabBar } from "./components/TabBar";
import { QuickAddButton } from "./components/QuickAddButton";

export const metadata = {
  title: "Stepwise",
  description: "Turn your goals into action.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          fontFamily: "system-ui, sans-serif",
          background: "#0b0c0f",
          color: "#f2f2f2",
          minHeight: "100vh",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <main style={{ flex: 1, padding: "24px 16px 96px", maxWidth: 480, margin: "0 auto", width: "100%" }}>
          {children}
        </main>
        <QuickAddButton />
        <TabBar />
      </body>
    </html>
  );
}
