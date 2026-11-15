package com.stepwise.app.di

import android.content.Context
import com.stepwise.core.network.BuildConfig
import com.stepwise.core.network.SupabaseAuthClient
import com.stepwise.core.network.SupabaseClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * [BuildConfig] here is `:core:network`'s own generated class, flavor-resolved to whichever of
 * `dev`/`staging`/`prod` `:app`'s matching `productFlavors` selected for this build
 * (`core/network/build.gradle.kts`'s own comment anticipates exactly this - ":app (DEV-015+)
 * must declare these same three flavor names").
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
    ): SupabaseClient = SupabaseClientFactory.create(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun provideSupabaseAuthClient(client: SupabaseClient): SupabaseAuthClient = SupabaseAuthClient(client)
}
