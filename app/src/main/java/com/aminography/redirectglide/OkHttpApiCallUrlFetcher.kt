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

class OkHttpApiCallUrlFetcher(
    private val client: OkHttpClient,
    private val request: Request
) : DataFetcher<ResponseBody> {

    private var call: Call? = null
    private var responseBody: ResponseBody? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ResponseBody>) {
        call = client.newCall(request)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onLoadFailed(e)
            }

            override fun onResponse(call: Call, response: Response) {
                responseBody = response.body
                if (response.isSuccessful) {
                    callback.onDataReady(responseBody)
                } else {
                    callback.onLoadFailed(IOException("Request failed with code: ${response.code}, message: ${response.message}"))
                }
            }
        })
    }

    override fun cleanup() {
        responseBody?.close()
    }

    override fun cancel() {
        call?.cancel()
    }

    override fun getDataClass(): Class<ResponseBody> {
        return ResponseBody::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.REMOTE
    }
}
