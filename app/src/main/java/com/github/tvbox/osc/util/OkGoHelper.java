package com.github.tvbox.osc.util;

import static okhttp3.ConnectionSpec.CLEARTEXT;
import static okhttp3.ConnectionSpec.COMPATIBLE_TLS;
import static okhttp3.ConnectionSpec.MODERN_TLS;
import static okhttp3.ConnectionSpec.RESTRICTED_TLS;

import androidx.annotation.NonNull;

import com.github.catvod.net.SSLCompat;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Cache;
import okhttp3.Credentials;
import okhttp3.Dns;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Util;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000; // 默认的超时时间

    // 默认客户端（无代理）
    static OkHttpClient defaultClient = null;

    // 带代理的客户端
    static OkHttpClient proxyClient = null;

    // DNS over HTTPS
    public static DnsOverHttps dnsOverHttps = null;
    public static ArrayList<String> dnsHttpsList = new ArrayList<>();
    public static boolean is_doh = false;
    public static Map<String, String> myHosts = null;

    // 初始化
    public static void init() {
        initDnsOverHttps();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkGo");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }

        // 添加拦截器，支持失败时切换到代理
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Response response = null;
            try {
                // 第一次尝试（无代理）
                response = chain.proceed(request);
                if (!response.isSuccessful()) {
                    // 如果失败，切换到代理客户端
                    OkHttpClient proxyClient = getProxyClient();
                    if (proxyClient != null) {
                        response = proxyClient.newCall(request).execute();
                    }
                }
            } catch (IOException e) {
                // 如果第一次请求失败，切换到代理客户端
                OkHttpClient proxyClient = getProxyClient();
                if (proxyClient != null) {
                    response = proxyClient.newCall(request).execute();
                }
            }
            return response;
        });

        builder.connectionSpecs(getConnectionSpec());
        builder.readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        builder.connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS);
        if (dnsOverHttps != null) builder.dns(dnsOverHttps);

        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        HttpHeaders.setUserAgent(Version.userAgent());
        defaultClient = builder.build();
        OkGo.getInstance().setOkHttpClient(defaultClient);

        initExoOkHttpClient();
    }

    // 设置代理
    public static void setProxy(String type, String host, int port, String username, String password) {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));

        OkHttpClient.Builder builder = defaultClient.newBuilder();
        builder.proxy(proxy);

        if (username != null && password != null) {
            builder.proxyAuthenticator((route, response) -> {
                String credential = Credentials.basic(username, password);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }

        // 更新带代理的客户端
        proxyClient = builder.build();
    }

    // 获取带代理的客户端
    public static OkHttpClient getProxyClient() {
        return proxyClient;
    }

    // 获取默认客户端
    public static OkHttpClient getDefaultClient() {
        return defaultClient;
    }

    // 初始化 ExoPlayer 的客户端
    static void initExoOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        builder.connectionSpecs(getConnectionSpec());
        builder.retryOnConnectionFailure(true);
        builder.followRedirects(true);
        builder.followSslRedirects(true);

        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.dns(new CustomDns());

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(builder.build());
    }

    // 获取连接配置
    public static List<ConnectionSpec> getConnectionSpec() {
        return Util.immutableList(RESTRICTED_TLS, MODERN_TLS, COMPATIBLE_TLS, CLEARTEXT);
    }

    // 获取 DoH URL
    public static String getDohUrl(int type) {
        switch (type) {
            case 1:
                return "https://doh.pub/dns-query";
            case 2:
                return "https://dns.alidns.com/dns-query";
            case 3:
                return "https://doh.360.cn/dns-query";
            case 4:
                return "https://dns.google/dns-query";
            case 5:
                return "https://dns.adguard.com/dns-query";
            case 6:
                return "https://dns.quad9.net/dns-query";
        }
        return "";
    }

    // 初始化 DNS over HTTPS
    static void initDnsOverHttps() {
        dnsHttpsList.add("关闭");
        dnsHttpsList.add("腾讯");
        dnsHttpsList.add("阿里");
        dnsHttpsList.add("360");
        dnsHttpsList.add("Google");
        dnsHttpsList.add("AdGuard");
        dnsHttpsList.add("Quad9");

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.connectionSpecs(getConnectionSpec());
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 10 * 1024 * 1024));
        OkHttpClient dohClient = builder.build();
        String dohUrl = getDohUrl(Hawk.get(HawkConfig.DOH_URL, 0));
        if (!dohUrl.isEmpty()) is_doh = true;
        dnsOverHttps = new DnsOverHttps.Builder()
                .client(dohClient)
                .url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl))
                .build();
    }

    // 自定义 DNS 解析器
    static class CustomDns implements Dns {
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            if (myHosts == null) {
                LOG.i("echo-exo-setHOSTS");
                myHosts = ApiConfig.get().getMyHost();
            }
            if (is_doh && !hostname.equals("127.0.0.1")) {
                if (!myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                    return dnsOverHttps.lookup(Objects.requireNonNull(myHosts.get(hostname)));
                } else {
                    return dnsOverHttps.lookup(hostname);
                }
            } else {
                if (!myHosts.isEmpty() && myHosts.containsKey(hostname)) {
                    return Dns.SYSTEM.lookup(Objects.requireNonNull(myHosts.get(hostname)));
                } else {
                    return Dns.SYSTEM.lookup(hostname);
                }
            }
        }
    }

    // 设置 SSL
    private static synchronized void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            final SSLSocketFactory sslSocketFactory = new SSLCompat();
            builder.sslSocketFactory(sslSocketFactory, SSLCompat.TM);
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
