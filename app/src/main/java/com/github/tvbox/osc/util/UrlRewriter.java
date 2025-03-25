package com.github.tvbox.osc.util;

public class UrlRewriter {
    public static String rewriteProxyUrl(String url) {
        // 只处理特定代理格式
        if (url != null && url.startsWith("http://127.0.0.1:9978/?channel-id=")) {
            return url.split("channel-id=")[1];
        }
        return url;
    }
}
