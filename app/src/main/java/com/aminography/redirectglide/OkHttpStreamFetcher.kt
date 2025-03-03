package com.aminography.redirectglide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.io.InputStream

class OkHttpStreamFetcher(
    private val client: OkHttpClient,
    private val request: Request
) : DataFetcher<InputStream> {

    private var call: Call? = null
    private var stream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        call = client.newCall(request)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onLoadFailed(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    stream = response.body?.byteStream()
                    callback.onDataReady(stream)
                } else {
                    callback.onLoadFailed(IOException("Request failed with code: ${response.code}, message: ${response.message}"))
                }
            }
        })
    }

    override fun cleanup() {
        stream?.close()
    }

    override fun cancel() {
        call?.cancel()
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
}
