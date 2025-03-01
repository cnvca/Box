package com.github.catvod.net;

import android.net.Uri;

import androidx.collection.ArrayMap;

import com.github.tvbox.osc.bean.Doh;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.github.tvbox.osc.util.OkGoHelper;
import com.google.common.net.HttpHeaders;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkHttp {

    private static final int TIMEOUT = 30 * 1000;
    private static final int CACHE = 100 * 1024 * 1024;

    private DnsOverHttps dns;
    private OkHttpClient client;
    private ProxySelector selector;

    private static class Loader {
        static volatile OkHttp INSTANCE = new OkHttp();
    }

    public static OkHttp get() {
        return Loader.INSTANCE;
    }

    public static Dns dns() {
        // 如果 dns 未初始化，则使用 OkGoHelper 中的 dnsOverHttps
        return get().dns != null ? get().dns : OkGoHelper.dnsOverHttps;
    }

    /**
     * 设置 DNS-over-HTTPS 配置。
     *
     * @param doh DNS-over-HTTPS 配置。
     */
    public void setDoh(Doh doh) {
        OkHttpClient dohClient = new OkHttpClient.Builder()
                .cache(new Cache(Path.doh(), CACHE))
                .hostnameVerifier(SSLCompat.VERIFIER)
                .sslSocketFactory(new SSLCompat(), SSLCompat.TM)
                .build();

        // 初始化 DnsOverHttps
        dns = doh.getUrl().isEmpty() ? null : new DnsOverHttps.Builder()
                .client(dohClient)
                .url(HttpUrl.get(doh.getUrl()))
                .bootstrapDnsHosts(doh.getHosts())
                .build();

        // 重置 client，以便下次调用 client() 时重新构建
        client = null;
    }

    /**
     * 设置代理。
     *
     * @param proxy 代理地址。
     */
    public void setProxy(String proxy) {
        ProxySelector.setDefault(selector());
        selector().setProxy(proxy);
        client = null;
    }

    /**
     * 获取代理选择器。
     */
    public static ProxySelector selector() {
        if (get().selector != null) return get().selector;
        return get().selector = new ProxySelector();
    }

    /**
     * 获取默认的 OkHttpClient。
     */
    public static OkHttpClient client() {
        if (get().client != null) return get().client;
        return get().client = getBuilder().build();
    }

    /**
     * 获取自定义超时的 OkHttpClient。
     *
     * @param timeout 超时时间（毫秒）。
     */
    public static OkHttpClient client(int timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 获取禁用重定向的 OkHttpClient。
     *
     * @param timeout 超时时间（毫秒）。
     */
    public static OkHttpClient noRedirect(int timeout) {
        return client().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    /**
     * 获取自定义是否重定向的 OkHttpClient。
     *
     * @param redirect 是否启用重定向。
     * @param timeout  超时时间（毫秒）。
     */
    public static OkHttpClient client(boolean redirect, int timeout) {
        return redirect ? client(timeout) : noRedirect(timeout);
    }

    /**
     * 发起 GET 请求并返回字符串结果。
     *
     * @param url 请求地址。
     */
    public static String string(String url) {
        try {
            return url.startsWith("http") ? newCall(url).execute().body().string() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 创建 GET 请求的 Call 对象。
     *
     * @param url 请求地址。
     */
    public static Call newCall(String url) {
        Uri uri = Uri.parse(url);
        if (uri.getUserInfo() != null) {
            return newCall(url, Headers.of(HttpHeaders.AUTHORIZATION, Util.basic(uri)));
        }
        return client().newCall(new Request.Builder().url(url).build());
    }

    /**
     * 创建 GET 请求的 Call 对象（使用自定义的 OkHttpClient）。
     *
     * @param client 自定义的 OkHttpClient。
     * @param url    请求地址。
     */
    public static Call newCall(OkHttpClient client, String url) {
        return client.newCall(new Request.Builder().url(url).build());
    }

    /**
     * 创建 GET 请求的 Call 对象（使用自定义的 OkHttpClient 和 Headers）。
     *
     * @param client  自定义的 OkHttpClient。
     * @param url     请求地址。
     * @param headers 请求头。
     */
    public static Call newCall(OkHttpClient client, String url, Headers headers) {
        return client.newCall(new Request.Builder().url(url).headers(headers).build());
    }

    /**
     * 创建 GET 请求的 Call 对象（使用自定义的 Headers）。
     *
     * @param url     请求地址。
     * @param headers 请求头。
     */
    public static Call newCall(String url, Headers headers) {
        return client().newCall(new Request.Builder().url(url).headers(headers).build());
    }

    /**
     * 创建 GET 请求的 Call 对象（使用自定义的 Headers 和查询参数）。
     *
     * @param url     请求地址。
     * @param headers 请求头。
     * @param params  查询参数。
     */
    public static Call newCall(String url, Headers headers, ArrayMap<String, String> params) {
        return client().newCall(new Request.Builder().url(buildUrl(url, params)).headers(headers).build());
    }

    /**
     * 创建 POST 请求的 Call 对象（使用自定义的 Headers 和请求体）。
     *
     * @param url     请求地址。
     * @param headers 请求头。
     * @param body    请求体。
     */
    public static Call newCall(String url, Headers headers, RequestBody body) {
        return client().newCall(new Request.Builder().url(url).headers(headers).post(body).build());
    }

    /**
     * 创建 POST 请求的 Call 对象（使用自定义的 OkHttpClient 和请求体）。
     *
     * @param client 自定义的 OkHttpClient。
     * @param url    请求地址。
     * @param body   请求体。
     */
    public static Call newCall(OkHttpClient client, String url, RequestBody body) {
        return client.newCall(new Request.Builder().url(url).post(body).build());
    }

    /**
     * 将 ArrayMap 转换为 FormBody。
     *
     * @param params 参数列表。
     */
    public static FormBody toBody(ArrayMap<String, String> params) {
        FormBody.Builder body = new FormBody.Builder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.add(entry.getKey(), entry.getValue());
        }
        return body.build();
    }

    /**
     * 构建带查询参数的 URL。
     *
     * @param url    基础 URL。
     * @param params 查询参数。
     */
    private static HttpUrl buildUrl(String url, ArrayMap<String, String> params) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /**
     * 获取默认的 OkHttpClient.Builder。
     */
    private static OkHttpClient.Builder getBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(new OkhttpInterceptor())
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .dns(dns()) // 使用自定义的 DNS
                .hostnameVerifier(SSLCompat.VERIFIER)
                .sslSocketFactory(new SSLCompat(), SSLCompat.TM);

        builder.proxySelector(selector());
        return builder;
    }
}
