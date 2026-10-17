package com.emniko.blockpedia

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache

class BlockpediaApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        Graph.init(this)
        return ImageLoader.Builder(this)
            // Reuse the client for its User-Agent; Coil keeps its own disk cache for images.
            .okHttpClient { Graph.http.newBuilder().cache(null).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
