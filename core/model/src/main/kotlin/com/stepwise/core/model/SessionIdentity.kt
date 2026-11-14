package com.stepwise.core.model

import kotlinx.datetime.LocalDate

/**
 * Computes a [SessionId]'s stable, deterministic identity for a completion whose target is
 * already known — decision `/ARCHITECTURE.md` §19.4, extending B9's deterministic-identity
 * pattern ([OccurrenceIdentity]) one step further: "`CompleteOccurrenceUseCase`'s Room write is
 * itself an upsert keyed on a Session ID deterministically derived from the occurrence being
 * completed... rather than a fresh random UUID per invocation." Invoking a completion twice for
 * the same target (a double-tap, a retried command, two surfaces racing) computes the *same*
 * Session ID and resolves to one row through the ordinary sync upsert mechanism (B4), by
 * construction, before sync is even involved — not a Widget-only special case (§19.4's own
 * closing line: "this tightening applies uniformly to the App too").
 *
 * Unlike [OccurrenceIdentity] (a plain composite string, since `recurrence_occurrences.id` is a
 * `text` column), `sessions.id` is a real Postgres `uuid` column (`0002_canonical_schema.sql`),
 * so the output must actually be 128 deterministic bits formatted as a UUID, not a readable
 * composite string. [fnv1a64] is a small, dependency-free FNV-1a implementation — deliberately
 * not a cryptographic hash, same bar [OccurrenceIdentity]'s own KDoc already accepts
 * ("correctness only requires that the same input always produce the same output and different
 * inputs never collide [in practice]"), with no dependency on `java.security`/any
 * platform-specific hashing API and no new external library for one small function. This keeps
 * `:core:model` free of `java.util.UUID` too, matching [IdGenerator][com.stepwise.core.common.IdGenerator]'s
 * own explicit Kotlin-Multiplatform rule (`/ARCHITECTURE.md` §27) rather than reaching for the
 * JVM-only `UUID.nameUUIDFromBytes` that would otherwise be the obvious choice here.
 *
 * A one-off (non-recurring) target's seed includes [LocalDate] deliberately — an Activity/Habit
 * logged ad hoc with no [RecurrenceRule] is still typically completed on more than one calendar
 * date over its lifetime (today's run and tomorrow's run are two different Sessions), so only
 * the recurring-occurrence overload (whose [RecurrenceOccurrenceId] already bakes a date into
 * B9's own hash) can safely omit a separate date component.
 */
object SessionIdentity {
    /** For a recurring occurrence's completion — one Session per materialized occurrence, ever. */
    fun deterministicId(occurrenceId: RecurrenceOccurrenceId): SessionId =
        SessionId(deterministicUuidString("session:occurrence:${occurrenceId.value}"))

    /** For a non-recurring (one-off) source's completion on a given calendar date. */
    fun deterministicId(
        sourceType: ExecutableKind,
        sourceId: String,
        date: LocalDate,
    ): SessionId = SessionId(deterministicUuidString("session:one-off:${sourceType.name}:$sourceId:$date"))

    /**
     * A Session carries at most one [SessionMetricValue] per [MetricId] — keying the row's own
     * id off `(sessionId, metricId)` the same deterministic way [deterministicId] keys the
     * Session itself means re-resolving the same metric on an idempotent re-completion upserts
     * the existing row instead of inserting a second one for the same metric.
     */
    fun deterministicMetricValueId(
        sessionId: SessionId,
        metricId: MetricId,
    ): SessionMetricValueId =
        SessionMetricValueId(deterministicUuidString("session-metric-value:${sessionId.value}:${metricId.value}"))

    private fun deterministicUuidString(seed: String): String {
        val high = fnv1a64(seed).toULong()
        val low = fnv1a64("$seed:low").toULong()
        return formatAsUuidString(high, low)
    }

    private fun formatAsUuidString(
        high: ULong,
        low: ULong,
    ): String {
        val hex =
            high.toString(radix = 16).padStart(HEX_CHARS_PER_LONG, '0') +
                low.toString(radix = 16).padStart(HEX_CHARS_PER_LONG, '0')
        return "${hex.substring(0, UUID_GROUP_1_END)}-" +
            "${hex.substring(UUID_GROUP_1_END, UUID_GROUP_2_END)}-" +
            "${hex.substring(UUID_GROUP_2_END, UUID_GROUP_3_END)}-" +
            "${hex.substring(UUID_GROUP_3_END, UUID_GROUP_4_END)}-" +
            "${hex.substring(UUID_GROUP_4_END, UUID_GROUP_5_END)}"
    }

    // Standard UUID string format (8-4-4-4-12 hex digits) - these are the group-boundary
    // indices into the 32-char concatenated hex string, not arbitrary values (detekt MagicNumber,
    // found at DEV-009 review).
    private const val HEX_CHARS_PER_LONG = 16
    private const val UUID_GROUP_1_END = 8
    private const val UUID_GROUP_2_END = 12
    private const val UUID_GROUP_3_END = 16
    private const val UUID_GROUP_4_END = 20
    private const val UUID_GROUP_5_END = 32

    // Standard FNV-1a 64-bit prime/offset basis — a well-known, fixed, dependency-free
    // algorithm, applied twice to two distinct derived inputs ("seed" and "seed:low") to get
    // 128 deterministic bits, rather than a second unexplained magic constant.
    private const val FNV64_PRIME = 0x100000001b3L

    // Standard FNV-1a 64-bit offset basis (0xcbf29ce484222325), written as its signed decimal
    // value since `const val` requires a compile-time constant and Kotlin hex literals wider
    // than Long.MAX_VALUE's bit pattern still need an explicit sign either way.
    private const val FNV64_OFFSET_BASIS = -3750763034362895579L

    private fun fnv1a64(input: String): Long {
        var hash = FNV64_OFFSET_BASIS
        for (byte in input.encodeToByteArray()) {
            hash = hash xor byte.toLong()
            hash *= FNV64_PRIME
        }
        return hash
    }
}
