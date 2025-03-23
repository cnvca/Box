package com.github.tvbox.osc.ui.activity;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ItvDns extends NanoHTTPD {

    private static final int PORT = 9978; // 代理服务器端口
    private static final Gson gson = new Gson();
    private static ItvDns instance; // 单例实例

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
     */
    public static String getProxyUrl(String originalUrl, String hostip, String hostipa, String hostipb, String mode, String time) {
        try {
            // 生成代理播放地址
            String proxyUrl = "http://127.0.0.1:" + PORT + "/apv.php?u=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.toString())
                    + "&hostip=" + hostip
                    + "&hostipa=" + hostipa
                    + "&hostipb=" + hostipb
                    + "&mode=" + mode
                    + "&time=" + time;

            return proxyUrl;
        } catch (Exception e) {
            Log.e("ItvDns", "生成播放地址失败", e);
            return originalUrl;
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        // 提取参数
        String channelId = params.get("channel-id");
        String contentId = params.get("Contentid");
        String mode = params.get("mode");
        String yw = params.get("yw");
        String hostip = params.get("hostip");
        String ts = params.get("ts");

        // 如果 hostip 为空，从 JSON 文件中获取
        if (hostip == null || hostip.isEmpty()) {
            hostip = getHostIpFromJson(channelId, yw);
        }

        // 处理 TS 文件请求
        if (ts != null && !ts.isEmpty()) {
            String decodedUts = URLDecoder.decode(ts, StandardCharsets.UTF_8);
            String[] tsa = decodedUts.split("AuthInfo=");
            if (tsa.length > 1) {
                String authinfo = URLEncoder.encode(tsa[1], StandardCharsets.UTF_8);
                decodedUts = tsa[0] + "AuthInfo=" + authinfo;
            }
            URL decodedUrl = new URL(decodedUts);
            String url = decodedUts.replace(decodedUrl.getHost(), hostip);
            List<String> headers = Arrays.asList(
                    "User-Agent: okhttp/3.12.3",
                    "Host: " + decodedUrl.getHost()
            );
            return gettsResponse(url, headers);
        }

        // 生成最终的播放地址
        String playUrl = generatePlayUrl(channelId, contentId, mode, yw, hostip);

        // 返回播放地址
        return newFixedLengthResponse(Status.OK, "application/json", playUrl);
    }

    private String getHostIpFromJson(String channelId, String yw) {
        String jsonFile;
        if ("1".equals(yw)) {
            jsonFile = "/json/yditv/hostip_yw.json";
        } else {
            jsonFile = "/json/yditv/hostip.json";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            StringBuilder jsonData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonData.append(line);
            }
            JsonObject jsonObj = gson.fromJson(jsonData.toString(), JsonObject.class);
            JsonObject ipsArray = jsonObj.getAsJsonObject("ipsArray");
            if (ipsArray != null && ipsArray.has(channelId)) {
                JsonArray ips = ipsArray.getAsJsonArray(channelId);
                if (ips != null && ips.size() > 0) {
                    // 随机选择一个 IP
                    Random random = new Random();
                    return ips.get(random.nextInt(ips.size())).getAsString();
                }
            }
        } catch (Exception e) {
            Log.e("ItvDns", "读取 JSON 文件失败", e);
        }
        return "39.134.95.33"; // 默认 IP
    }

    private String generatePlayUrl(String channelId, String contentId, String mode, String yw, String hostip) {
        try {
            // 生成原始播放地址
            String originalUrl = "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=" + channelId + "&Contentid=" + contentId + "&livemode=1&stbId=toShengfen";

            // 获取原始播放地址的内容
            String url2 = get(originalUrl, Arrays.asList("User-Agent: okhttp/3.12.3"), 3);
            if (url2 == null || url2.isEmpty()) {
                // 如果获取失败，尝试替换 host
                originalUrl = originalUrl.replace("gslbserv.itv.cmvideo.cn", "36.155.98.21");
                url2 = get(originalUrl, Arrays.asList("User-Agent: okhttp/3.12.3", "Host: gslbserv.itv.cmvideo.cn"), 3);
            }

            // 如果 url2 不包含 cache.ott，则替换为 cache.ott
            if (url2 != null && !url2.contains("cache.ott")) {
                int position = url2.indexOf("/", 8);
                if (position != -1) {
                    String str = url2.substring(position);
                    url2 = "http://cache.ott." + channelId + ".itv.cmvideo.cn" + str;
                }
            }

            // 如果 mode 为 3，直接返回 url2
            if ("3".equals(mode)) {
                return url2;
            }

            // 生成代理播放地址
            String proxyUrl = "http://127.0.0.1:" + PORT + "/apv.php?u=" + URLEncoder.encode(url2, StandardCharsets.UTF_8.toString())
                    + "&hostip=" + hostip
                    + "&hostipa=" + hostip
                    + "&hostipb=" + hostip
                    + "&mode=" + mode
                    + "&time=" + (System.currentTimeMillis() / 1000);

            return proxyUrl;
        } catch (Exception e) {
            Log.e("ItvDns", "生成播放地址失败", e);
            return "";
        }
    }

    private String get(String url, List<String> headers, int timeout) {
        try {
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
        } catch (Exception e) {
            Log.e("ItvDns", "获取数据时出错: " + url, e);
            return null;
        }
    }

    private Response gettsResponse(String url, List<String> headers) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            for (String header : headers) {
                String[] parts = header.split(": ");
                connection.setRequestProperty(parts[0], parts[1]);
            }
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            OutputStream out = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            byte[] data = ((ByteArrayOutputStream) out).toByteArray();
            return createResponse(Status.OK, "video/mp2t", "inline", data, data.length);
        } catch (Exception e) {
            Log.e("ItvDns", "获取 ts 数据时出错", e);
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
        }
    }

    private Response createResponse(Status status, String contentType, String disposition, byte[] data, int length) {
        Response response = newFixedLengthResponse(status, contentType, new ByteArrayInputStream(data), length);
        response.addHeader("Content-Disposition", disposition + "; filename=" + (contentType.contains("video") ? "video.ts" : "index.m3u8"));
        return response;
    }
}
