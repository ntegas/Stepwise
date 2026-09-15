package com.stepwise.core.common

/**
 * Deterministic [IdGenerator] for tests: returns `prefix-1`, `prefix-2`, ... in order,
 * so an idempotency/convergence test can assert on a specific, predictable ID instead
 * of a random UUID.
 */
class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0

    override fun newId(): String {
        counter += 1
        return "$prefix-$counter"
    }
}
