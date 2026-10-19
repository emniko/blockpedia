package com.emniko.blockpedia

import android.content.Context
import com.emniko.blockpedia.data.PageCache
import com.emniko.blockpedia.data.UserStore
import com.emniko.blockpedia.data.WikiApi
import com.emniko.blockpedia.data.WikiRepository
import com.emniko.blockpedia.seed.SeedStore
import com.emniko.blockpedia.update.AppUpdater
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Minimal manual dependency container. */
object Graph {
    /** MediaWiki asks API clients to identify themselves. Update the URL to your repo. */
    const val USER_AGENT =
        "Blockpedia/1.0 (Android; unofficial Minecraft Wiki reader; +https://github.com/emniko/blockpedia)"

    lateinit var http: OkHttpClient
        private set
    lateinit var pageCache: PageCache
        private set
    lateinit var repo: WikiRepository
        private set
    lateinit var userStore: UserStore
        private set
    lateinit var seedStore: SeedStore
        private set
    lateinit var updater: AppUpdater
        private set

    fun init(context: Context) {
        if (::http.isInitialized) return
        val app = context.applicationContext
        http = OkHttpClient.Builder()
            .cache(Cache(File(app.cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
        pageCache = PageCache(app)
        repo = WikiRepository(WikiApi(http), pageCache)
        userStore = UserStore(app)
        seedStore = SeedStore(app)
        updater = AppUpdater(app, http)
    }
}
