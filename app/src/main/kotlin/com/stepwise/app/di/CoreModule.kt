package com.stepwise.app.di

import com.stepwise.core.common.Clock
import com.stepwise.core.common.IdGenerator
import com.stepwise.core.common.SystemClock
import com.stepwise.core.common.UuidIdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * `@Provides`, not `@Inject`-constructed - neither `SystemClock`/`UuidIdGenerator` nor any other
 * `:domain`/`:data` type anywhere in this app ever carries a Hilt annotation (DEVELOPMENT_PROTOCOL.md's
 * module-boundary rule: no DI-framework type crosses into business logic). Every binding in
 * `:app/.../di` exists for exactly this reason - the composition root is the one place framework
 * wiring is allowed to live.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    fun provideClock(): Clock = SystemClock()

    @Provides
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()
}
