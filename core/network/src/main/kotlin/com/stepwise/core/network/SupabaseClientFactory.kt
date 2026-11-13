package com.stepwise.core.network

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * The one place a [SupabaseClient] is constructed (`/DEVELOPMENT_PROTOCOL.md`'s centralization
 * principle) — [url]/[anonKey] come from [BuildConfig] (per build flavor, environment isolation,
 * `/ARCHITECTURE.md` §11/§21), never hardcoded here. The anon key is Supabase's intentionally
 * public, client-embeddable project identifier, not a secret the way a service-role key is
 * (`/docs/security/SECURITY_ARCHITECTURE.md` — service role protection); RLS is the real
 * authorization boundary regardless of which anon key a build embeds.
 */
object SupabaseClientFactory {
    fun create(
        context: Context,
        url: String,
        anonKey: String,
    ): SupabaseClient =
        createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth) {
                // Never the default SettingsSessionManager (plain SharedPreferences) — see
                // EncryptedPrefsSessionManager's own KDoc for why.
                sessionManager = EncryptedPrefsSessionManager(context)
            }
            install(Postgrest)
        }
}
