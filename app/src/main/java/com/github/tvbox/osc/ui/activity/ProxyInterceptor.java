package com.github.tvbox.osc.ui.activity;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class ProxyInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.urlString();
        
        if (url.contains("127.0.0.1:9978/?channel-id=")) {
            String originalUrl = url.split("channel-id=")[1];
            Request newRequest = request.newBuilder()
                .url(originalUrl)
                .build();
            return chain.proceed(newRequest);
        }
        return chain.proceed(request);
    }
}
