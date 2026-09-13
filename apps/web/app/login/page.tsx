"use client";

import { useState } from "react";
import { supabase } from "../../lib/supabaseClient";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const { error } = await supabase.auth.signInWithOtp({ email });
    if (error) {
      setError(error.message);
    } else {
      setSent(true);
    }
  }

  return (
    <div>
      <h1 style={{ fontSize: 22 }}>Sign in to Stepwise</h1>
      {sent ? (
        <p>Check your email for a login link.</p>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <input
            type="email"
            required
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={{ padding: 12, borderRadius: 8, border: "1px solid #2a2b30", background: "#16171b", color: "#fff" }}
          />
          <button
            type="submit"
            style={{ padding: 12, borderRadius: 8, border: "none", background: "#4f7cff", color: "#fff", fontWeight: 600 }}
          >
            Send magic link
          </button>
          {error && <p style={{ color: "#ff6b6b" }}>{error}</p>}
        </form>
      )}
    </div>
  );
}
