package com.github.tvbox.osc.ui.activity;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItvDns extends NanoHTTPD {

    private static final int PORT = 9978; // 代理服务器端口
    private static final String REMOTE_JSON_URL = "http://611594.lovexyz.cc/live/hostip_yw";
    private static final String LOCAL_JSON_PATH = "json/yditv/hostip.json";
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static ItvDns instance; // 单例实例

    // 构造函数
    public ItvDns() throws IOException {
        super(PORT);
        downloadAndSaveJson();
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
            URL url = new URL(originalUrl);
            String host = url.getHost();

            if ("gslbserv.itv.cmvideo.cn".equals(host)) {
                return "http://127.0.0.1:" + PORT + "/apv.php?u=" + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.toString())
                        + "&hostip=" + hostip
                        + "&hostipa=" + hostipa
                        + "&hostipb=" + hostipb
                        + "&mode=" + mode
                        + "&time=" + time;
            }
            return originalUrl;
        } catch (Exception e) {
            Log.e("ItvDns", "解析 URL 失败", e);
            return originalUrl;
        }
    }

    /**
     * 处理 HTTP 请求
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        String hostip = params.get("hostip") != null ? params.get("hostip") : "";
        String hostipa = params.get("hostipa") != null ? params.get("hostipa") : "39.135.97.80";
        String hostipb = params.get("hostipb") != null ? params.get("hostipb") : "39.135.238.209";
        String mode = params.get("mode") != null ? params.get("mode") : "0";
        String ts = params.get("ts") != null ? params.get("ts") : "";
        String timeStr = params.get("time") != null ? params.get("time") : String.valueOf(System.currentTimeMillis() / 1000);
        long time = Long.parseLong(timeStr);

        if (ts != null && !ts.isEmpty()) {
            try {
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

                if ("1".equals(mode) || ("0".equals(mode) && System.currentTimeMillis() / 1000 - time < 20)) {
                    return gettsResponse(url, headers);
                }

                byte[][] data = get(url, headers);
                if (data[1][0] != 200) {
                    url = decodedUts.replace(decodedUrl.getHost(), hostipa);
                    data = get(url, headers);
                    if (data[1][0] != 200) {
                        url = decodedUts.replace(decodedUrl.getHost(), hostipb);
                        data = get(url, headers);
                        if (data[1][0] != 200) {
                            return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found");
                        } else {
                            return createResponse(Status.OK, "video/MP2T", "inline", data[0], data[0].length);
                        }
                    } else {
                        return createResponse(Status.OK, "video/MP2T", "inline", data[0], data[0].length);
                    }
                } else {
                    return createResponse(Status.OK, "video/MP2T", "inline", data[0], data[0].length);
                }
            } catch (Exception e) {
                Log.e("ItvDns", "处理 ts 请求时出错", e);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
            }
        } else {
            String u = params.get("u") != null ? params.get("u") : "";
            String https = session.getHeaders().get("host").toLowerCase().startsWith("https") ? "https" : "http";
            String httpHost = session.getHeaders().get("host");
            String requestUri = session.getUri();
            try {
                String decodedUri = URLDecoder.decode(requestUri, StandardCharsets.UTF_8.toString());
                String[] uriParts = decodedUri.split("\\?");
                String Uripath = uriParts[0];

                if (u != null && !u.isEmpty()) {
                    String decodedU = URLDecoder.decode(u, StandardCharsets.UTF_8.toString());
                    String[] urlpathParts = decodedU.split("index.m3u8");
                    String urlpath = urlpathParts[0];
                    String urlp = https + "://" + httpHost + Uripath + "?ts=";

                    URL decodedUrl = new URL(decodedU);
                    String url = decodedU.replace(decodedUrl.getHost(), hostip);

                    List<String> headers = Arrays.asList(
                            "User-Agent: okhttp/3.12.3",
                            "Host: " + decodedUrl.getHost()
                    );

                    byte[] m3u8 = get(url, headers, 3)[0];
                    if (new String(m3u8, StandardCharsets.UTF_8).indexOf("EXTM3U") == -1) {
                        url = decodedU.replace(decodedUrl.getHost(), hostipa);
                        m3u8 = get(url, headers, 3)[0];
                        if (new String(m3u8, StandardCharsets.UTF_8).indexOf("EXTM3U") == -1) {
                            url = decodedU.replace(decodedUrl.getHost(), hostipb);
                            m3u8 = get(url, headers, 3)[0];
                            if (new String(m3u8, StandardCharsets.UTF_8).indexOf("EXTM3U") == -1) {
                                return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found");
                            }
                        }
                    }

                    String[] m3u8s;
                    if (new String(m3u8, StandardCharsets.UTF_8).indexOf("\r\n") != -1) {
                        m3u8s = new String(m3u8, StandardCharsets.UTF_8).split("\r\n");
                    } else {
                        m3u8s = new String(m3u8, StandardCharsets.UTF_8).split("\n");
                    }

                    StringBuilder d = new StringBuilder();
                    for (String m3u8l : m3u8s) {
                        if (m3u8l.toLowerCase().indexOf(".ts") != -1) {
                            d.append(urlp)
                                    .append(URLEncoder.encode(urlpath + m3u8l, StandardCharsets.UTF_8.toString()))
                                    .append("&hostip=").append(hostip)
                                    .append("&hostipa=").append(hostipa)
                                    .append("&hostipb=").append(hostipb)
                                    .append("&mode=").append(mode)
                                    .append("&time=").append(timeStr)
                                    .append("\n");
                        } else {
                            d.append(m3u8l).append("\n");
                        }
                    }

                    return createResponse(Status.OK, "application/vnd.apple.mpegurl", "inline", d.toString().getBytes(StandardCharsets.UTF_8), d.toString().getBytes(StandardCharsets.UTF_8).length);
                } else {
                    String channelId = params.get("channel-id") != null ? params.get("channel-id") : "ystenlive";
                    String contentId = params.get("Contentid") != null ? params.get("Contentid") : "8785669936177902664";
                    String stbId = params.get("stbId") != null ? params.get("stbId") : "toShengfen";
                    String playseek = params.get("playseek") != null ? params.get("playseek") : "";
                    String yw = params.get("yw") != null ? params.get("yw") : "";

                    String domainId;
                    switch (channelId) {
                        case "bestzb":
                            domainId = "bestlive";
                            break;
                        case "wasusyt":
                            domainId = "wasulive";
                            break;
                        case "FifastbLive":
                            domainId = "fifalive";
                            break;
                        default:
                            domainId = channelId;
                            break;
                    }

                    String url1;
                    if (playseek != null && !playseek.isEmpty()) {
                        String[] tArr = playseek.replace("-", ".0").concat(".0").split("(?<=\\G.{8})");
                        String starttime = tArr[0] + "T" + tArr[1] + "0Z";
                        String endtime = tArr[2] + "T" + tArr[3] + "0Z";
                        url1 = "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=" + channelId + "&Contentid=" + contentId + "&livemode=4&stbId=" + stbId + "&starttime=" + starttime + "&endtime=" + endtime;
                    } else {
                        url1 = "http://gslbserv.itv.cmvideo.cn/index.m3u8?channel-id=" + channelId + "&Contentid=" + contentId + "&livemode=1&stbId=" + stbId;
                    }

                    byte[] url2 = get(url1, Arrays.asList("User-Agent: okhttp/3.12.3"), 3)[0];
                    if (url2 == null || url2.length == 0) {
                        url1 = url1.replace("gslbserv.itv.cmvideo.cn", "221.181.100.64");
                        url2 = get(url1, Arrays.asList("User-Agent: okhttp/3.12.3", "Host: gslbserv.itv.cmvideo.cn"), 3)[0];
                    }

                    if (new String(url2, StandardCharsets.UTF_8).indexOf("cache.ott") == -1) {
                        int position = new String(url2, StandardCharsets.UTF_8).indexOf("/", 8);
                        String str = new String(url2, StandardCharsets.UTF_8).substring(position);
                        url2 = ("http://cache.ott." + domainId + ".itv.cmvideo.cn" + str).getBytes(StandardCharsets.UTF_8);
                    }

                    String url4;
                    if ("3".equals(mode)) {
                        url4 = new String(url2, StandardCharsets.UTF_8);
                    } else {
                        if (hostip == null || hostip.isEmpty()) {
                            String jsonFile;
                            String api;
                            if ("1".equals(yw)) {
                                jsonFile = "json/yditv/hostip_yw.json";
                                api = "http://611594.lovexyz.cc/live/hostip_yw";
                            } else {
                                jsonFile = "json/yditv/hostip.json";
                                api = "https://api.wheiss.com/json/yditv/hostip.json";
                            }

                            File directory = new File(jsonFile).getParentFile();
                            if (!directory.exists() && !directory.mkdirs()) {
                                Log.e("ItvDns", "目录创建失败: " + directory.getAbsolutePath());
                                return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "目录创建失败");
                            }

                            int update = 1;
                            List<String> ipsArray = new ArrayList<>();
                            if (new File(jsonFile).exists()) {
                                try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
                                    StringBuilder jsonData = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        jsonData.append(line);
                                    }
                                    JsonObject jsonObj = gson.fromJson(jsonData.toString(), JsonObject.class);
                                    JsonArray ipsJsonArray = jsonObj.getAsJsonArray("ipsArray").getAsJsonArray();
                                    for (int i = 0; i < ipsJsonArray.size(); i++) {
                                        ipsArray.add(ipsJsonArray.get(i).getAsString());
                                    }
                                    long updated = jsonObj.get("updated").getAsLong();
                                    long pullDate = jsonObj.has("pullDate") ? jsonObj.get("pullDate").getAsLong() : updated;
                                    if (System.currentTimeMillis() / 1000 - updated < 3600 || System.currentTimeMillis() / 1000 - pullDate < 3600 && new Random().nextInt(10) < 8) {
                                        update = 0;
                                    }
                                } catch (Exception e) {
                                    Log.e("ItvDns", "读取 JSON 文件时出错", e);
                                }
                            }

                            if (update == 1) {
                                try {
                                    byte[] jsondata = get(api, Collections.emptyList())[0];
                                    if (jsondata != null && new String(jsondata, StandardCharsets.UTF_8).startsWith("{")) {
                                        JsonObject jsonObj = gson.fromJson(new String(jsondata, StandardCharsets.UTF_8), JsonObject.class);
                                        JsonArray ipsJsonArray = jsonObj.getAsJsonArray("ipsArray").getAsJsonArray();
                                        ipsArray.clear();
                                        for (int i = 0; i < ipsJsonArray.size(); i++) {
                                            ipsArray.add(ipsJsonArray.get(i).getAsString());
                                        }
                                        JsonObject jsonToSave = new JsonObject();
                                        jsonToSave.add("ipsArray", jsonObj.get("ipsArray"));
                                        jsonToSave.addProperty("pullDate", new Date().getTime() / 1000);
                                        try (FileWriter writer = new FileWriter(jsonFile)) {
                                            writer.write(gson.toJson(jsonToSave));
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e("ItvDns", "更新 JSON 文件时出错", e);
                                }
                            }

                            if (ipsArray.isEmpty()) {
                                hostip = "39.134.95.33";
                            } else {
                                Collections.shuffle(ipsArray);
                                int ct = ipsArray.size();
                                if (ct < 3) {
                                    hostip = ipsArray.get(new Random().nextInt(ct));
                                    hostipa = ipsArray.get(new Random().nextInt(ct));
                                    hostipb = ipsArray.get(new Random().nextInt(ct));
                                } else {
                                    int a = ct / 3;
                                    int b = a * 2;
                                    hostip = ipsArray.get(new Random().nextInt(a));
                                    hostipa = ipsArray.get(nextIntInRange(a, b));
                                    hostipb = ipsArray.get(nextIntInRange(b, ct));
                                }
                            }
                        }
                        try {
                            String url3 = URLEncoder.encode(new String(url2, StandardCharsets.UTF_8), StandardCharsets.UTF_8.toString());
                            url4 = https + "://" + httpHost + Uripath + "?u=" + url3 + "&hostip=" + hostip + "&hostipa=" + hostipa + "&hostipb=" + hostipb + "&mode=" + mode + "&time=" + timeStr;
                        } catch (UnsupportedEncodingException e) {
                            Log.e("ItvDns", "URLEncoder.encode 异常", e);
                            return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
                        }
                    }
                    Response response = newFixedLengthResponse(Status.REDIRECT, "text/plain", "");
                    response.addHeader("Location", url4);
                    return response;
                }
            } catch (Exception e) {
                Log.e("ItvDns", "处理请求时出错", e);
                return newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal Error");
            }
        }
    }

    private void downloadAndSaveJson() {
        try {
            byte[] jsondata = get(REMOTE_JSON_URL, Collections.emptyList())[0];
            if (jsondata != null && new String(jsondata, StandardCharsets.UTF_8).startsWith("{")) {
                JsonObject jsonObj = gson.fromJson(new String(jsondata, StandardCharsets.UTF_8), JsonObject.class);
                JsonObject jsonToSave = new JsonObject();
                jsonToSave.add("ipsArray", jsonObj.get("ipsArray"));
                jsonToSave.addProperty("updated", new Date().getTime() / 1000);
                File directory = new File(LOCAL_JSON_PATH).getParentFile();
                if (!directory.exists() && !directory.mkdirs()) {
                    Log.e("ItvDns", "目录创建失败: " + directory.getAbsolutePath());
                    return;
                }
                try (FileWriter writer = new FileWriter(LOCAL_JSON_PATH)) {
                    writer.write(gson.toJson(jsonToSave));
                }
            }
        } catch (Exception e) {
            Log.e("ItvDns", "下载并保存 JSON 文件时出错", e);
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

    private byte[][] get(String url, List<String> headers) {
        return get(url, headers, 0);
    }

    private byte[][] get(String url, List<String> headers, int timeout) {
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
            byte[] data = out.toByteArray();
            int statusCode = connection.getResponseCode();
            return new byte[][]{data, new byte[]{(byte) statusCode}};
        } catch (Exception e) {
            Log.e("ItvDns", "获取数据时出错: " + url, e);
            return new byte[][]{new byte[0], new byte[]{(byte) 500}};
        }
    }

    private Response createResponse(Status status, String contentType, String disposition, byte[] data, int length) {
        Response response = newFixedLengthResponse(status, contentType, new ByteArrayInputStream(data), length);
        response.addHeader("Content-Disposition", disposition + "; filename=" + (contentType.contains("video") ? "video.ts" : "index.m3u8"));
        return response;
    }

    private int nextIntInRange(int min, int max) {
        return new Random().nextInt(max - min) + min;
    }

    public static void main(String[] args) {
        try {
            ItvDns.startLocalProxyServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
