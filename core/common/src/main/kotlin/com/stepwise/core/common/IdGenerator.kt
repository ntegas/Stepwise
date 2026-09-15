package com.stepwise.core.common

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The single source of new entity/Session IDs (DEV-001). IDs are always generated
 * client-side and are stable across devices — this is what makes independent-Session
 * convergence and deterministic occurrence identity work (ARCHITECTURE.md §9, ADR-005),
 * so nothing in the domain layer is allowed to construct an ID any other way.
 */
interface IdGenerator {
    /** A new, globally-unique, client-generated ID (string form of a UUID). */
    fun newId(): String
}

/**
 * Production [IdGenerator] using [Uuid] (kotlin-stdlib, multiplatform) rather than
 * `java.util.UUID`, keeping this module free of any JVM-only API per ARCHITECTURE.md §27.
 */
@OptIn(ExperimentalUuidApi::class)
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
