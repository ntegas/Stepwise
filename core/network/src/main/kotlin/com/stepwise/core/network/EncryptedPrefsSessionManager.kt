package com.stepwise.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The one [SessionManager] this app uses — supabase-kt's own default (`SettingsSessionManager`)
 * persists the refresh token in plain `SharedPreferences`, which `/ARCHITECTURE.md` §11 forbids
 * ("Refresh token stored via Android Keystore-backed encrypted storage
 * (`androidx.security.crypto`)"). Wired into the `Auth` plugin's `sessionManager` config in
 * [SupabaseClientFactory] — never the default.
 */
internal class EncryptedPrefsSessionManager(
    context: Context,
) : SessionManager {
    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(SESSION_KEY, Json.encodeToString(session)).apply()
    }

    // Real bug found and fixed during DEV-007 review (logic reviewer, MEDIUM): this threw a plain
    // IllegalStateException. supabase-kt's AuthImpl.loadFromStorage() specifically catches
    // NoSessionFoundException and logs it at debug level (the expected, normal case — no session
    // yet on a fresh install); any other Throwable logs at error level. Throwing the wrong type
    // meant every first-launch-ever app start logged through the error branch, a false-positive
    // error event on every fresh install if error logs are wired to crash/alerting (ARCHITECTURE.md
    // §3) — not a correctness bug (both branches end with session = null), but a real noise/signal
    // problem this fix removes.
    override suspend fun loadSession(): UserSession = loadSessionOrNull() ?: throw NoSessionFoundException()

    override suspend fun loadSessionOrNull(): UserSession? =
        prefs.getString(SESSION_KEY, null)?.let { Json.decodeFromString<UserSession>(it) }

    override suspend fun deleteSession() {
        prefs.edit().remove(SESSION_KEY).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "stepwise_auth_session"
        const val SESSION_KEY = "session"
    }
}
