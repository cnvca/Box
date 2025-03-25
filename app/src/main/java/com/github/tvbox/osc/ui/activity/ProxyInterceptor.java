public class ProxyInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        
        // 拦截代理地址
        if (url.host().equals("127.0.0.1") && url.port() == 9978) {
            String channelId = url.queryParameter("channel-id");
            if (channelId != null) {
                String originalUrl = new String(Base64.decode(channelId, Base64.URL_SAFE));
                return chain.proceed(request.newBuilder()
                    .url(originalUrl) // 替换为真实地址
                    .build());
            }
        }
        return chain.proceed(request);
    }
}
