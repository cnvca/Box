public class ProxyUrlHelper {
    public static String buildProxyUrl(String originalUrl) {
        return "http://127.0.0.1:9978/?channel-id=" + 
            Base64.encodeToString(originalUrl.getBytes(), Base64.URL_SAFE);
    }

    public static boolean isProxyUrl(String url) {
        return url != null && url.startsWith("http://127.0.0.1:9978/?channel-id=");
    }
}
