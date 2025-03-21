import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ItvDns {

    private static final String REMOTE_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    private static final String LOCAL_JSON_PATH = "json/yditv/hostip.json";
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // channel-id 到主机名的映射
    private static final Map<String, String> CHANNEL_TO_HOST = new HashMap<>();
    static {
        CHANNEL_TO_HOST.put("bestzb", "cache.ott.bestlive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("hnbblive", "cache.ott.hnbblive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("wasusyt", "cache.ott.wasulive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("ystenlive", "cache.ott.ystenlive.itv.cmvideo.cn");
        CHANNEL_TO_HOST.put("fifastlive", "cache.ott.fifalive.itv.cmvideo.cn");
    }

    public static void startLocalProxyServer() {
        try {
            // 启动本地代理服务器
            HttpServer server = HttpServer.create(new InetSocketAddress(9978), 0);
            server.createContext("/apv.php", new ProxyHandler());
            server.setExecutor(null); // 使用默认线程池
            server.start();
            System.out.println("ItvDns 代理服务器已启动，监听端口 9978...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getProxyUrl(String originalUrl) {
        try {
            // 解析原始 URL
            URL url = new URL(originalUrl);
            String host = url.getHost();
            String path = url.getPath();
            String query = url.getQuery();

            // 如果主机名是 gslbserv.itv.cmvideo.cn，则替换为代理地址
            if ("gslbserv.itv.cmvideo.cn".equals(host)) {
                return "http://127.0.0.1:9978/apv.php?u=" + URLEncoder.encode(originalUrl, "UTF-8");
            }

            // 其他情况直接返回原始 URL
            return originalUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return originalUrl;
        }
    }

    static class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 处理代理请求
            // 这里可以根据需要实现具体的代理逻辑
        }
    }
}
