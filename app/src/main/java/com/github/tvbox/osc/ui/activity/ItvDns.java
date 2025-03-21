package com.github.tvbox.osc.ui.activity;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ItvDns extends NanoHTTPD {

    private static final int PORT = 9978; // 代理服务器端口
    private static final String REMOTE_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    private static final String LOCAL_JSON_PATH = "json/yditv/hostip.json";
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ItvDns instance; // 单例实例

    // channel-id 到主机名的映射
    private static final Map<String, String> CHANNEL_TO_HOST = new HashMap<>();
    static {
        CHANNEL_TO_HOST.put("bestzb", "cache.ott.bestlive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("hnbblive", "cache.ott.hnbblive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("wasusyt", "cache.ott.wasulive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("ystenlive", "cache.ott.ystenlive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("fifastlive", "cache.ott.fifalive.itv.cmvideo.cn");
    }

    // 构造函数
    public ItvDns() throws IOException {
        super(PORT);
    }

    /**
     * 启动本地代理服务器
     */
    public static void startLocalProxyServer() {
        if (instance == null) {
            try {
                instance = new ItvDns();
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); // 启动服务器
                Log.d("ItvDns", "代理服务器已启动，监听端口 " + PORT + "...");
            } catch (IOException e) {
                Log.e("ItvDns", "启动代理服务器失败", e);
            }
        }
    }

    /**
     * 根据原始 URL 获取代理后的 URL
     *
     * @param originalUrl 原始 URL
     * @return 代理后的 URL
     */
    public static String getProxyUrl(String originalUrl) {
        try {
            // 解析原始 URL
            String host = new URL(originalUrl).getHost();

            // 如果主机名是 gslbserv.itv.cmvideo.cn，则替换为代理地址
            if ("gslbserv.itv.cmvideo.cn".equals(host)) {
                return "http://127.0.0.1:" + PORT + "/apv.php?u=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.toString());
            }

            // 其他情况直接返回原始 URL
            return originalUrl;
        } catch (Exception e) {
            Log.e("ItvDns", "解析 URL 失败", e);
            return originalUrl;
        }
    }

    /**
     * 处理 HTTP 请求
     *
     * @param session HTTP 会话
     * @return HTTP 响应
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri(); // 获取请求路径
        Map<String, String> params = session.getParms(); // 获取请求参数

        // 处理 /apv.php 请求
        if (uri.equals("/apv.php")) {
            String u = params.get("u"); // 获取 u 参数
            if (u != null) {
                // 这里可以根据需要实现具体的代理逻辑
                // 例如：转发请求到目标服务器并返回响应
                return Response.newFixedLengthResponse(Status.OK, "application/octet-stream", "代理响应");
            }
        }

        // 其他请求返回 404
        return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found");
    }
}
