package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class ItvDns extends NanoHTTPD {

    private static final int PORT = 9978;
    private static final Gson gson = new Gson();
    private static ItvDns instance;
    private Context context;
    
    // JSON文件URL
    private static final String HOSTIP_JSON_URL = "https://api.wheiss.com/json/yditv/hostip.json";
    private static final String HOSTIP_YW_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    // 本地JSON文件路径
    private static final String HOSTIP_JSON_FILE = "hostip.json";
    private static final String HOSTIP_YW_JSON_FILE = "hostip_yw.json";

    public ItvDns(Context context) throws IOException {
        super(PORT);
        this.context = context;
        // 启动时检查并创建JSON文件
        initJsonFiles();
    }

    public static void startLocalProxyServer(Context context) {
        if (instance == null) {
            try {
                instance = new ItvDns(context);
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d("ItvDns", "代理服务器已启动，监听端口 " + PORT + "...");
            } catch (IOException e) {
                Log.e("ItvDns", "启动代理服务器失败", e);
            }
        }
    }

    private void initJsonFiles() {
        // 创建目录
        File jsonDir = new File(context.getFilesDir(), "json/yditv");
        if (!jsonDir.exists()) {
            jsonDir.mkdirs();
        }
        
        // 检查并创建hostip.json
        File hostipFile = new File(jsonDir, HOSTIP_JSON_FILE);
        if (!hostipFile.exists()) {
            downloadJsonFile(HOSTIP_JSON_URL, hostipFile);
        }
        
        // 检查并创建hostip_yw.json
        File hostipYwFile = new File(jsonDir, HOSTIP_YW_JSON_FILE);
        if (!hostipYwFile.exists()) {
            downloadJsonFile(HOSTIP_YW_JSON_URL, hostipYwFile);
        }
    }

    private void downloadJsonFile(String url, File outputFile) {
        new Thread(() -> {
            try {
                URL jsonUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) jsonUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream();
                         FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        Log.d("ItvDns", "成功下载JSON文件: " + outputFile.getName());
                    }
                } else {
                    Log.e("ItvDns", "下载JSON文件失败，HTTP状态码: " + connection.getResponseCode());
                    createDefaultJsonFile(outputFile);
                }
            } catch (Exception e) {
                Log.e("ItvDns", "下载JSON文件时出错: " + url, e);
                createDefaultJsonFile(outputFile);
            }
        }).start();
    }

    private void createDefaultJsonFile(File outputFile) {
        try {
            JsonObject defaultJson = new JsonObject();
            defaultJson.add("ipsArray", new JsonObject());
            defaultJson.addProperty("updated", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(defaultJson.toString());
            }
            Log.d("ItvDns", "创建默认JSON文件: " + outputFile.getName());
        } catch (Exception ex) {
            Log.e("ItvDns", "创建默认JSON文件失败", ex);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        Log.d("ItvDns", "请求 URI: " + uri);
        Log.d("ItvDns", "请求参数: " + params.toString());

        try {
            // 处理 TS 文件请求
            if (params.containsKey("ts")) {
                return handleTsRequest(params);
            }

            // 处理 M3U8 请求
            if (params.containsKey("u")) {
                return handleM3u8Request(params);
            }

            // 处理直播频道请求
            return handleLiveChannelRequest(params);
        } catch (Exception e) {
            Log.e("ItvDns", "处理请求时出错", e);
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
        }
    }

    private Response handleTsRequest(Map<String, String> params) throws Exception {
        String ts = params.get("ts");
        String hostip = params.get("hostip");
        String mode = params.get("mode");
        String time = params.get("time");

        String decodedUts = URLDecoder.decode(ts, StandardCharsets.UTF_8.toString());
        String[] tsa = decodedUts.split("AuthInfo=");
        if (tsa.length > 1) {
            String authinfo = URLEncoder.encode(tsa[1], StandardCharsets.UTF_8.toString());
            decodedUts = tsa[0] + "AuthInfo=" + authinfo;
        }

        URL decodedUrl = new URL(decodedUts);
        String url = decodedUts.replace(decodedUrl.getHost(), hostip);

        List<String> headers = Arrays.asList(
                "User-Agent: okhttp/3.12.3",
                "Host: " + decodedUrl.getHost()
        );

        // 如果是模式1或者时间在20秒内，直接获取TS
        if ("1".equals(mode) || (System.currentTimeMillis() / 1000 - Long.parseLong(time) < 20)) {
            return getTsResponse(url, headers);
        }

        // 否则尝试多个备用IP
        Response response = tryMultipleTsUrls(decodedUts, decodedUrl.getHost(), headers);
        if (response != null) {
            return response;
        }

        return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response tryMultipleTsUrls(String originalUrl, String originalHost, List<String> headers) throws Exception {
        String hostip = getHostIpFromJson("", "0"); // 获取默认IP
        String hostipa = "39.135.97.80";
        String hostipb = "39.135.238.209";

        String[] ips = {hostip, hostipa, hostipb};
        for (String ip : ips) {
            String url = originalUrl.replace(originalHost, ip);
            try {
                Response response = getTsResponse(url, headers);
                if (response.getStatus() == Status.OK) {
                    return response;
                }
            } catch (Exception e) {
                Log.e("ItvDns", "尝试IP " + ip + " 失败", e);
            }
        }
        return null;
    }

    private Response handleM3u8Request(Map<String, String> params) throws Exception {
        String u = params.get("u");
        String hostip = params.get("hostip");
        String hostipa = params.get("hostipa");
        String hostipb = params.get("hostipb");
        String mode = params.get("mode");
        String time = params.get("time");

        String decodedU = URLDecoder.decode(u, StandardCharsets.UTF_8.toString());
        String urlpath = decodedU.split("index.m3u8")[0];
        String urlp = "http://127.0.0.1:" + PORT + "/?ts=";

        URL decodedUArray = new URL(decodedU);
        String url = decodedU.replace(decodedUArray.getHost(), hostip);

        List<String> headers = Arrays.asList(
                "User-Agent: okhttp/3.12.3",
                "Host: " + decodedUArray.getHost()
        );

        String m3u8 = get(url, headers, 3);
        if (m3u8 == null || !m3u8.contains("EXTM3U")) {
            url = decodedU.replace(decodedUArray.getHost(), hostipa);
            m3u8 = get(url, headers, 3);
            if (m3u8 == null || !m3u8.contains("EXTM3U")) {
                url = decodedU.replace(decodedUArray.getHost(), hostipb);
                m3u8 = get(url, headers, 3);
                if (m3u8 == null || !m3u8.contains("EXTM3U")) {
                    return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
                }
            }
        }

        StringBuilder d = new StringBuilder();
        String[] m3u8s = m3u8.split("\n");
        for (String m3u8l : m3u8s) {
            if (m3u8l.trim().isEmpty()) continue;
            
            if (m3u8l.contains(".ts")) {
                d.append(urlp).append(URLEncoder.encode(urlpath + m3u8l, StandardCharsets.UTF_8.toString()))
                 .append("&hostip=").append(hostip)
                 .append("&hostipa=").append(hostipa)
                 .append("&hostipb=").append(hostipb)
                 .append("&mode=").append(mode)
                 .append("&time=").append(time)
                 .append("\n");
            } else {
                d.append(m3u8l).append("\n");
            }
        }

        return newFixedLengthResponse(Status.OK, "application/vnd.apple.mpegurl", d.toString());
    }

    private Response handleLiveChannelRequest(Map<String, String> params) throws Exception {
        String channelId = params.get("channel-id");
        String contentId = params.get("Contentid");
        String stbId = params.get("stbId");
        String playseek = params.get("playseek");
        String yw = params.get("yw");
        String mode = params.get("mode");
        String hostip = params.get("hostip");
        String hostipa = params.get("hostipa", "39.135.97.80");
        String hostipb = params.get("hostipb", "39.135.238.209");
        long time = System.currentTimeMillis() / 1000;

        String domainId = getDomainId(channelId);

        String url1;
        if (playseek != null && !playseek.isEmpty()) {
            String[] tArr = (playseek.replace("-", ".0") + ".0").split("(?<=\\G.{8})");
            String starttime = tArr[0] + "T" + tArr[1] + "0Z";
            String endtime = tArr[2] + "T" + tArr[3] + "0Z";
            url1 = "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=" + channelId + 
                  "&Contentid=" + contentId + "&livemode=4&stbId=" + stbId + 
                  "&starttime=" + starttime + "&endtime=" + endtime;
        } else {
            url1 = "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=" + channelId + 
                  "&Contentid=" + contentId + "&livemode=1&stbId=" + stbId;
        }

        String url2 = getRedirectUrl(url1, Arrays.asList("User-Agent: okhttp/3.12.3"), 3);
        if (url2 == null || url2.isEmpty()) {
            url2 = getRedirectUrl(url1.replace("gslbserv.itv.cmvideo.cn", "221.181.100.64"), 
                    Arrays.asList("User-Agent: okhttp/3.12.3", "Host: gslbserv.itv.cmvideo.cn"), 3);
        }

        if (url2 != null && !url2.contains("cache.ott")) {
            int position = url2.indexOf("/", 8);
            if (position != -1) {
                String str = url2.substring(position);
                url2 = "http://cache.ott." + domainId + ".itv.cmvideo.cn" + str;
            }
        }

        if ("3".equals(mode)) {
            return newFixedLengthResponse(Status.OK, "text/plain", url2);
        }

        if (hostip == null || hostip.isEmpty()) {
            String[] ips = getRandomIps(channelId, yw, time);
            hostip = ips[0];
            hostipa = ips[1];
            hostipb = ips[2];
        }

        String url3 = URLEncoder.encode(url2, StandardCharsets.UTF_8.toString());
        String url4 = "http://127.0.0.1:" + PORT + "/?u=" + url3 + 
                      "&hostip=" + hostip + "&hostipa=" + hostipa + 
                      "&hostipb=" + hostipb + "&mode=" + mode + "&time=" + time;

        Response response = newFixedLengthResponse(Status.TEMPORARY_REDIRECT, "text/plain", "");
        response.addHeader("Location", url4);
        return response;
    }

    private String getDomainId(String channelId) {
        switch (channelId) {
            case "bestzb":
                return "bestlive";
            case "wasusyt":
                return "wasulive";
            case "FifastbLive":
                return "fifalive";
            default:
                return channelId;
        }
    }

    private String[] getRandomIps(String channelId, String yw, long time) {
        try {
            String jsonFileName = "1".equals(yw) ? HOSTIP_YW_JSON_FILE : HOSTIP_JSON_FILE;
            File jsonFile = new File(context.getFilesDir(), "json/yditv/" + jsonFileName);
            
            // 检查文件是否需要更新（1小时更新一次）
            if (jsonFile.exists()) {
                long lastModified = jsonFile.lastModified() / 1000;
                if (time - lastModified > 3600) { // 超过1小时
                    downloadJsonFile("1".equals(yw) ? HOSTIP_YW_JSON_URL : HOSTIP_JSON_URL, jsonFile);
                }
            } else {
                downloadJsonFile("1".equals(yw) ? HOSTIP_YW_JSON_URL : HOSTIP_JSON_URL, jsonFile);
            }
            
            // 读取JSON文件
            JsonObject jsonObj;
            try (FileReader reader = new FileReader(jsonFile)) {
                jsonObj = gson.fromJson(reader, JsonObject.class);
            }
            
            JsonObject ipsArray = jsonObj.getAsJsonObject("ipsArray");
            JsonArray ips = ipsArray != null ? ipsArray.getAsJsonArray(channelId) : null;
            
            if (ips == null || ips.size() == 0) {
                return new String[]{"39.134.95.33", "39.135.97.80", "39.135.238.209"};
            }

            // 随机选择IP
            Random random = new Random();
            int size = ips.size();
            
            if (size < 3) {
                String ip = ips.get(random.nextInt(size)).getAsString();
                return new String[]{ip, ip, ip};
            } else {
                int a = size / 3;
                int b = a * 2;
                return new String[]{
                    ips.get(random.nextInt(a)).getAsString(),
                    ips.get(random.nextInt(a, b)).getAsString(),
                    ips.get(random.nextInt(b, size)).getAsString()
                };
            }
        } catch (Exception e) {
            Log.e("ItvDns", "获取随机IP时出错", e);
            return new String[]{"39.134.95.33", "39.135.97.80", "39.135.238.209"};
        }
    }

    private String getHostIpFromJson(String channelId, String yw) {
        try {
            String[] ips = getRandomIps(channelId, yw, System.currentTimeMillis() / 1000);
            return ips[0];
        } catch (Exception e) {
            Log.e("ItvDns", "从JSON获取IP时出错", e);
            return "39.134.95.33";
        }
    }

    private Response getTsResponse(String url, List<String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        for (String header : headers) {
            String[] parts = header.split(": ");
            connection.setRequestProperty(parts[0], parts[1]);
        }
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        byte[] data = out.toByteArray();
        return newFixedLengthResponse(Status.OK, "video/mp2t", new ByteArrayInputStream(data), data.length);
    }

    private String get(String url, List<String> headers, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        for (String header : headers) {
            String[] parts = header.split(": ");
            connection.setRequestProperty(parts[0], parts[1]);
        }
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        if (timeout > 0) {
            connection.setReadTimeout(timeout * 1000);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private String getRedirectUrl(String url, List<String> headers, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        for (String header : headers) {
            String[] parts = header.split(": ");
            connection.setRequestProperty(parts[0], parts[1]);
        }
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        if (timeout > 0) {
            connection.setReadTimeout(timeout * 1000);
        }
        connection.setInstanceFollowRedirects(false);
        
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
            responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
            return connection.getHeaderField("Location");
        }
        return null;
    }
}
