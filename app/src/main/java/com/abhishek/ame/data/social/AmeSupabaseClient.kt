package com.abhishek.ame.data.social

import com.abhishek.ame.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlin.time.Duration.Companion.seconds

/**
 * Global Supabase Client instance for Ame.
 * Connects securely using public anonymous key with Postgrest (Database), Storage (Images), and Realtime (WebSockets).
 */
object AmeSupabaseClient {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Postgrest)
            install(Storage) {
                transferTimeout = 60.seconds
            }
            install(Realtime)
        }
    }
}
