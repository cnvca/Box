package com.github.tvbox.osc.ui.activity;

import android.content.Context; // 导入 Context
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
import java.util.*;

public class ItvDns extends NanoHTTPD {

    private static final int PORT = 9978; // 代理服务器端口
    private static final Gson gson = new Gson();
    private static ItvDns instance; // 单例实例
    private Context context; // 增加 Context 成员变量

    // 构造函数，接收 Context 参数
    public ItvDns(Context context) throws IOException {
        super(PORT);
        this.context = context; // 初始化 Context
    }

    /**
     * 保存日志到文件
     */
    private void saveLogToFile(String logMessage) {
        // 使用 Context 的 getFilesDir() 方法
        File logFile = new File(context.getFilesDir(), "tvbox_log.txt");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logMessage + "\n");
            Log.d("ItvDns", "日志已写入文件: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("ItvDns", "保存日志到文件失败", e);
        }
    }

    /**
     * 启动本地代理服务器
     */
    public static void startLocalProxyServer(Context context) {
        if (instance == null) {
            try {
                instance = new ItvDns(context); // 传递 Context
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
            // 生成代理播放地址（不包含 apv.php）
            String proxyUrl = "http://127.0.0.1:" + PORT + "/?u=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.toString())
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

    // 记录请求日志
    Log.d("ItvDns", "请求 URI: " + uri);
    Log.d("ItvDns", "请求参数: " + params.toString());
    saveLogToFile("请求 URI: " + uri); // 保存日志到文件

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
        try {
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
        } catch (MalformedURLException e) {
            Log.e("ItvDns", "URL 格式错误", e);
            return newFixedLengthResponse(Status.BAD_REQUEST, "text/plain", "Invalid URL");
        } catch (Exception e) {
            Log.e("ItvDns", "处理 TS 请求时出错", e);
            return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
        }
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

    private String generatePlayUrl(String originalUrl, String hostip, String hostipa, String hostipb, String mode, String time) {
        try {
            // 直接生成播放地址
            String playUrl = originalUrl
                    + "&hostip=" + hostip
                    + "&hostipa=" + hostipa
                    + "&hostipb=" + hostipb
                    + "&mode=" + mode
                    + "&time=" + time;

            return playUrl;
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
