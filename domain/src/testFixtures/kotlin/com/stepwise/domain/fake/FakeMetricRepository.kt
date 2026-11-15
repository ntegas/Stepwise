package com.stepwise.domain.fake

import com.stepwise.core.common.AppResult
import com.stepwise.core.model.Metric
import com.stepwise.core.model.MetricId
import com.stepwise.domain.repository.MetricRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMetricRepository : MetricRepository {
    private val metrics = MutableStateFlow<Map<String, Metric>>(emptyMap())

    fun seed(metric: Metric) {
        metrics.value = metrics.value + (metric.id.value to metric)
    }

    override fun observeAll(): Flow<List<Metric>> = metrics.map { it.values.toList() }

    override suspend fun getById(id: MetricId): AppResult<Metric?> = AppResult.Success(metrics.value[id.value])

    override suspend fun upsert(metric: Metric): AppResult<Unit> {
        metrics.value = metrics.value + (metric.id.value to metric)
        return AppResult.Success(Unit)
    }
}
