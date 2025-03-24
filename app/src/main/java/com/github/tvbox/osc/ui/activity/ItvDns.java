package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

public class ItvDns extends NanoHTTPD {
    private static final int PORT = 9978;
    private static final Gson gson = new Gson();
    private static ItvDns instance;
    private Context context;
    
    // JSON配置
    private static final String HOSTIP_JSON_URL = "https://api.wheiss.com/json/yditv/hostip.json";
    private static final String HOSTIP_YW_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    private static final String HOSTIP_JSON_FILE = "hostip.json";
    private static final String HOSTIP_YW_JSON_FILE = "hostip_yw.json";

    public ItvDns(Context context) throws IOException {
        super(PORT);
        this.context = context.getApplicationContext();
        initJsonFiles();
    }

    public static synchronized void startLocalProxyServer(Context context) {
        if (instance == null) {
            try {
                instance = new ItvDns(context);
                instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d("ItvDns", "代理服务器已启动，端口: " + PORT);
            } catch (IOException e) {
                Log.e("ItvDns", "启动代理服务器失败", e);
            }
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();
        
        Log.d("ItvDns", "处理请求: " + uri);
        Log.d("ItvDns", "请求参数: " + params);

        try {
            if (params.containsKey("ts")) {
                return handleTsRequest(params);
            } else if (params.containsKey("u")) {
                return handleM3u8Request(params);
            } else if (params.containsKey("channel-id")) {
                return handleLiveChannelRequest(params);
            }
        } catch (Exception e) {
            Log.e("ItvDns", "处理请求出错", e);
        }
        return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response handleTsRequest(Map<String, String> params) throws Exception {
        String ts = params.get("ts");
        String hostip = params.get("hostip");
        String mode = params.get("mode");
        String time = params.get("time");

        String decodedUts = URLDecoder.decode(ts, StandardCharsets.UTF_8.name());
        String[] tsa = decodedUts.split("AuthInfo=");
        if (tsa.length > 1) {
            String authinfo = tsa[1].split("&")[0];
            decodedUts = tsa[0] + "AuthInfo=" + URLEncoder.encode(authinfo, StandardCharsets.UTF_8.name());
        }

        URL decodedUrl = new URL(decodedUts);
        String url = decodedUts.replace(decodedUrl.getHost(), hostip);

        List<String> headers = Arrays.asList(
            "User-Agent: okhttp/3.12.3",
            "Host: " + decodedUrl.getHost()
        );

        if ("1".equals(mode) || (System.currentTimeMillis()/1000 - Long.parseLong(time)) < 20) {
            return getTsResponse(url, headers);
        }

        // 尝试备用IP
        String[] backupIps = {"39.135.97.80", "39.135.238.209"};
        for (String ip : backupIps) {
            try {
                String backupUrl = decodedUts.replace(decodedUrl.getHost(), ip);
                Response response = getTsResponse(backupUrl, headers);
                if (response != null && response.getStatus() == Status.OK) {
                    return response;
                }
            } catch (Exception e) {
                Log.e("ItvDns", "备用IP失败: " + ip, e);
            }
        }
        return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response handleM3u8Request(Map<String, String> params) throws Exception {
        String u = params.get("u");
        String hostip = params.getOrDefault("hostip", "");
        String hostipa = params.getOrDefault("hostipa", "39.135.97.80");
        String hostipb = params.getOrDefault("hostipb", "39.135.238.209");
        String mode = params.get("mode");
        String time = params.get("time");

        String decodedU = URLDecoder.decode(u, StandardCharsets.UTF_8.name());
        String urlPath = decodedU.split("index.m3u8")[0];
        String proxyUrl = "http://127.0.0.1:" + PORT + "/?ts=";

        URL originalUrl = new URL(decodedU);
        String[] testUrls = {
            decodedU.replace(originalUrl.getHost(), hostip),
            decodedU.replace(originalUrl.getHost(), hostipa),
            decodedU.replace(originalUrl.getHost(), hostipb)
        };

        String m3u8Content = null;
        for (String testUrl : testUrls) {
            try {
                m3u8Content = getUrlContent(testUrl, Arrays.asList(
                    "User-Agent: okhttp/3.12.3",
                    "Host: " + originalUrl.getHost()
                ));
                if (m3u8Content != null && m3u8Content.contains("EXTM3U")) {
                    break;
                }
            } catch (Exception e) {
                Log.e("ItvDns", "M3U8获取失败: " + testUrl, e);
            }
        }

        if (m3u8Content == null) {
            return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "No M3U8");
        }

        StringBuilder result = new StringBuilder();
        for (String line : m3u8Content.split("\n")) {
            if (line.trim().isEmpty()) continue;
            
            if (line.contains(".ts")) {
                if (!line.startsWith("http")) {
                    line = urlPath + line;
                }
                result.append(proxyUrl)
                     .append(URLEncoder.encode(line, StandardCharsets.UTF_8.name()))
                     .append("&hostip=").append(hostip)
                     .append("&hostipa=").append(hostipa)
                     .append("&hostipb=").append(hostipb)
                     .append("&mode=").append(mode)
                     .append("&time=").append(time)
                     .append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        Response response = newFixedLengthResponse(Status.OK, "application/vnd.apple.mpegurl", result.toString());
        response.addHeader("Content-Disposition", "inline; filename=index.m3u8");
        return response;
    }

    private Response handleLiveChannelRequest(Map<String, String> params) throws Exception {
        String channelId = params.get("channel-id");
        String contentId = params.get("Contentid");
        String stbId = params.get("stbId");
        String playseek = params.get("playseek");
        String yw = params.get("yw");
        String mode = params.get("mode");
        
        String[] ips = getBestIps(channelId, yw);
        String hostip = ips[0];
        String hostipa = ips[1];
        String hostipb = ips[2];

        String originalUrl = buildOriginalUrl(channelId, contentId, stbId, playseek);
        String finalUrl = getFinalUrl(originalUrl, channelId);
        
        if ("3".equals(mode)) {
            return newFixedLengthResponse(Status.OK, "text/plain", finalUrl);
        }

        String proxyUrl = "http://127.0.0.1:" + PORT + "/?u=" + 
            URLEncoder.encode(finalUrl, StandardCharsets.UTF_8.name()) + 
            "&hostip=" + hostip + 
            "&hostipa=" + hostipa + 
            "&hostipb=" + hostipb + 
            "&mode=" + mode + 
            "&time=" + (System.currentTimeMillis()/1000);

        Response response = newFixedLengthResponse(Status.TEMPORARY_REDIRECT, "text/plain", "");
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

    private Response getTsResponse(String url, List<String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        for (String header : headers) {
            String[] parts = header.split(": ");
            conn.setRequestProperty(parts[0], parts[1]);
        }
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return newFixedLengthResponse(Status.OK, "video/mp2t", new ByteArrayInputStream(out.toByteArray()), out.size());
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
                    Log.d("ItvDns", "JSON文件下载成功: " + outputFile.getName());
                }
            } catch (Exception e) {
                Log.e("ItvDns", "下载JSON失败", e);
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
            Log.d("ItvDns", "创建默认JSON文件: " + file.getName());
        } catch (Exception e) {
            Log.e("ItvDns", "创建默认JSON失败", e);
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
}
