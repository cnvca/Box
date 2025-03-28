package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.util.Log;

// 在 ItvDns.java 头部添加
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.util.Base64;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import fi.iki.elonen.NanoHTTPD;

public class ItvDns extends NanoHTTPD {
    private static final int PORT = 9978; // 添加端口常量    
    private static final String TAG = "ItvDns";
    private static ItvDns instance;
    private Context context;
    private Gson gson = new Gson();

    // 配置文件路径
    private static final String HOSTIP_JSON_URL = "https://api.wheiss.com/json/yditv/hostip.json";
    private static final String HOSTIP_YW_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    private static final String HOSTIP_JSON_FILE = "hostip.json";
    private static final String HOSTIP_YW_JSON_FILE = "hostip_yw.json";

    public static synchronized void startLocalProxyServer(Context context) {
        if (instance == null) {
            try {
                instance = new ItvDns(context);
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d(TAG, "代理服务器启动成功，端口：" + PORT);
            } catch (Exception e) {
                Log.e(TAG, "代理服务器启动失败", e);
            }
        }
    }

    public ItvDns(Context context) throws IOException {
        super(9978);
        this.context = context.getApplicationContext();
        initJsonFiles();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        try {
            // 处理直播代理请求
            if (uri.startsWith("/proxy")) {
                return handleProxyRequest(params);
            }
            // 处理M3U8请求
            else if (params.containsKey("u")) {
                return handleM3u8Request(params);
            }
            // 处理TS片段请求
            else if (params.containsKey("ts")) {
                return handleTsRequest(params);
            }
            // 处理频道请求
            else if (params.containsKey("channel-id")) {
                return handleChannelRequest(params);
            }
        } catch (Exception e) {
            Log.e(TAG, "请求处理失败", e);
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "无效请求");
    }

    private Response handleProxyRequest(Map<String, String> params) throws Exception {
        String type = params.get("type");
        String ext = params.get("ext");
        
        if ("txt".equals(type) && ext != null) {
            String decodedUrl = new String(Base64.decode(ext, Base64.URL_SAFE), "UTF-8");
            return handleM3u8Proxy(decodedUrl);
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "无效的代理请求");
    }

    private Response handleM3u8Proxy(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "okhttp/3.12.3");

        if (conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
            String location = conn.getHeaderField("Location");
            return handleM3u8Proxy(location);
        }

        InputStream is = conn.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        
        return newFixedLengthResponse(
            Response.Status.OK, 
            "application/vnd.apple.mpegurl", 
            buffer.toString("UTF-8")
        );
    }

    private Response handleM3u8Request(Map<String, String> params) throws Exception {
        String m3u8Url = URLDecoder.decode(params.get("u"), "UTF-8");
        String hostip = params.get("hostip");
        String hostipa = params.getOrDefault("hostipa", "39.135.97.80");
        String hostipb = params.getOrDefault("hostipb", "39.135.238.209");
        
        URL originalUrl = new URL(m3u8Url);
        String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);
        
        // 尝试多个IP获取M3U8
        String[] testIps = {hostip, hostipa, hostipb};
        String m3u8Content = null;
        for (String ip : testIps) {
            try {
                String testUrl = m3u8Url.replace(originalUrl.getHost(), ip);
                m3u8Content = getUrlContent(testUrl, Arrays.asList(
                    "User-Agent: okhttp/3.12.3",
                    "Host: " + originalUrl.getHost()
                ));
                if (m3u8Content != null && m3u8Content.contains("EXTM3U")) break;
            } catch (Exception e) {
                Log.w(TAG, "M3U8获取失败: " + ip);
            }
        }

        if (m3u8Content == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "获取M3U8失败");
        }

        // 处理TS路径
        StringBuilder result = new StringBuilder();
        String proxyUrl = "http://127.0.0.1:9978/?ts=";
        for (String line : m3u8Content.split("\n")) {
            if (line.trim().isEmpty()) continue;
            
            if (line.endsWith(".ts")) {
                String tsUrl = line.contains("://") ? line : baseUrl + line;
                result.append(proxyUrl)
                     .append(URLEncoder.encode(tsUrl, "UTF-8"))
                     .append("&hostip=").append(hostip)
                     .append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        Response response = newFixedLengthResponse(
            Response.Status.OK, 
            "application/vnd.apple.mpegurl", 
            result.toString()
        );
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    private Response handleTsRequest(Map<String, String> params) throws Exception {
        String tsUrl = URLDecoder.decode(params.get("ts"), "UTF-8");
        String hostip = params.get("hostip");
        
        URL originalUrl = new URL(tsUrl);
        String proxyUrl = tsUrl.replace(originalUrl.getHost(), hostip);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(proxyUrl).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "okhttp/3.12.3");
        conn.setRequestProperty("Host", originalUrl.getHost());
        
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, 
                "text/plain", 
                "TS片段获取失败"
            );
        }
        
        return newChunkedResponse(
            Response.Status.OK, 
            "video/mp2t", 
            conn.getInputStream()
        );
    }

    private Response handleChannelRequest(Map<String, String> params) throws Exception {
        String channelId = params.get("channel-id");
        String contentId = params.get("Contentid");
        String stbId = params.get("stbId");
        String playseek = params.get("playseek");
        String yw = params.get("yw");
        
        String[] ips = getBestIps(channelId, yw);
        String hostip = ips[0];
        String hostipa = ips[1];
        String hostipb = ips[2];
        
        String originalUrl = buildOriginalUrl(channelId, contentId, stbId, playseek);
        String finalUrl = getFinalUrl(originalUrl, channelId);
        
        String proxyUrl = "http://127.0.0.1:9978/?u=" + 
            URLEncoder.encode(finalUrl, "UTF-8") + 
            "&hostip=" + hostip + 
            "&hostipa=" + hostipa + 
            "&hostipb=" + hostipb + 
            "&time=" + (System.currentTimeMillis()/1000);
        
        Response response = newFixedLengthResponse(
            Response.Status.TEMPORARY_REDIRECT, 
            "text/plain", 
            ""
        );
        response.addHeader("Location", proxyUrl);
        return response;
    }

    private String[] getBestIps(String channelId, String yw) throws Exception {
        File jsonFile = new File(context.getFilesDir(), 
            "json/yditv/" + ("1".equals(yw) ? HOSTIP_YW_JSON_FILE : HOSTIP_JSON_FILE));
        
        if (!jsonFile.exists() || 
            (System.currentTimeMillis() - jsonFile.lastModified()) > 3600000) {
            downloadJsonFile(
                "1".equals(yw) ? HOSTIP_YW_JSON_URL : HOSTIP_JSON_URL, 
                jsonFile
            );
        }

        JsonObject json = gson.fromJson(new FileReader(jsonFile), JsonObject.class);
        JsonArray ips = json.getAsJsonObject("ipsArray").getAsJsonArray(channelId);
        
        if (ips == null || ips.size() == 0) {
            return new String[]{"39.134.95.33", "39.135.97.80", "39.135.238.209"};
        }

        Random random = new Random();
        int size = ips.size();
        if (size < 3) {
            String ip = ips.get(random.nextInt(size)).getAsString();
            return new String[]{ip, ip, ip};
        } else {
            int section = size / 3;
            return new String[]{
                ips.get(random.nextInt(section)).getAsString(),
                ips.get(random.nextInt(section) + section).getAsString(),
                ips.get(random.nextInt(size - 2*section) + 2*section).getAsString()
            };
        }
    }

    private String buildOriginalUrl(String channelId, String contentId, String stbId, String playseek) {
        if (playseek != null && !playseek.isEmpty()) {
            String[] times = (playseek.replace("-", ".0") + ".0").split("(?<=\\G.{8})");
            return String.format(
                "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=%s&Contentid=%s&livemode=4&stbId=%s&starttime=%sT%s0Z&endtime=%sT%s0Z",
                channelId, contentId, stbId, times[0], times[1], times[2], times[3]
            );
        } else {
            return String.format(
                "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=%s&Contentid=%s&livemode=1&stbId=%s",
                channelId, contentId, stbId
            );
        }
    }

    private String getFinalUrl(String originalUrl, String channelId) throws Exception {
        String url = getRedirectUrl(originalUrl, Arrays.asList("User-Agent: okhttp/3.12.3"));
        if (url == null) {
            url = getRedirectUrl(
                originalUrl.replace("gslbserv.itv.cmvideo.cn", "221.181.100.64"),
                Arrays.asList("User-Agent: okhttp/3.12.3", "Host: gslbserv.itv.cmvideo.cn")
            );
        }

        if (url != null && !url.contains("cache.ott")) {
            int pos = url.indexOf('/', 8);
            if (pos > 0) {
                String domain = getDomain(channelId);
                url = "http://cache.ott." + domain + ".itv.cmvideo.cn" + url.substring(pos);
            }
        }
        return url;
    }

    private String getDomain(String channelId) {
        switch (channelId) {
            case "bestzb": return "bestlive";
            case "wasusyt": return "wasulive";
            case "FifastbLive": return "fifalive";
            default: return channelId;
        }
    }

    private String getRedirectUrl(String url, List<String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        for (String header : headers) {
            String[] parts = header.split(": ");
            conn.setRequestProperty(parts[0], parts[1]);
        }
        conn.setConnectTimeout(2000);
        
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM) {
            return conn.getHeaderField("Location");
        }
        return null;
    }

    private String getUrlContent(String url, List<String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        for (String header : headers) {
            String[] parts = header.split(": ");
            conn.setRequestProperty(parts[0], parts[1]);
        }
        conn.setConnectTimeout(3000);
        
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    private void initJsonFiles() {
        File jsonDir = new File(context.getFilesDir(), "json/yditv");
        if (!jsonDir.exists()) jsonDir.mkdirs();
        
        File hostipFile = new File(jsonDir, HOSTIP_JSON_FILE);
        if (!hostipFile.exists()) downloadJsonFile(HOSTIP_JSON_URL, hostipFile);
        
        File hostipYwFile = new File(jsonDir, HOSTIP_YW_JSON_FILE);
        if (!hostipYwFile.exists()) downloadJsonFile(HOSTIP_YW_JSON_URL, hostipYwFile);
    }

    private void downloadJsonFile(String url, File outputFile) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Log.d(TAG, "JSON文件下载成功: " + outputFile.getName());
                }
            } catch (Exception e) {
                Log.e(TAG, "下载JSON失败", e);
                createDefaultJson(outputFile);
            }
        }).start();
    }

    private void createDefaultJson(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject json = new JsonObject();
            json.add("ipsArray", new JsonObject());
            json.addProperty("updated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.write(json.toString());
            Log.d(TAG, "创建默认JSON文件: " + file.getName());
        } catch (Exception e) {
            Log.e(TAG, "创建默认JSON失败", e);
        }
    }

    public static String getProxyUrl(String originalUrl, String hostip, String hostipa, String hostipb, String mode, String time) {
        try {
            return "http://127.0.0.1:" + PORT + "/?u=" + 
                   URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.name()) + 
                   "&hostip=" + hostip + 
                   "&hostipa=" + hostipa + 
                   "&hostipb=" + hostipb + 
                   "&mode=" + mode + 
                   "&time=" + time;
        } catch (Exception e) {
            return originalUrl;
        }
    }
    
    public static boolean isRunning() {
        return instance != null && instance.isAlive();
    }
}
