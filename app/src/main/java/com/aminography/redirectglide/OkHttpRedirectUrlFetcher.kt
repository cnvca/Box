package com.aminography.redirectglide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class OkHttpRedirectUrlFetcher(
    private val client: OkHttpClient,
    private val request: Request
) : DataFetcher<String> {

    private var call: Call? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in String>) {
        call = client.newCall(request)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onLoadFailed(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback.onDataReady(response.request.url.toString())
                } else {
                    callback.onLoadFailed(IOException("Request failed with code: ${response.code}, message: ${response.message}"))
                }
            }
        })
    }

    override fun cleanup() {
        // No-op
    }

    override fun cancel() {
        call?.cancel()
    }

    override fun getDataClass(): Class<String> {
        return String::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
}
