package okhttp3.dnsoverhttps;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

public class DnsOverHttps implements Dns {
    private HttpUrl url;
    private final OkHttpClient client;
    private final ConcurrentHashMap<String, String> customHostsMap; // 自定义 HOSTS 映射
    private final List<InetAddress> bootstrapDnsHosts; // 初始 DNS 服务器列表

    public DnsOverHttps(Builder builder) {
        this.url = builder.url;
        this.client = builder.client;
        this.customHostsMap = new ConcurrentHashMap<>();
        this.bootstrapDnsHosts = builder.bootstrapDnsHosts;
    }

    /**
     * 设置 DNS-over-HTTPS 的 URL。
     */
    public void setUrl(HttpUrl url) {
        this.url = url;
    }

    /**
     * 添加自定义 HOSTS 映射。
     *
     * @param hosts 格式为 "原始域名=映射后的IP或域名" 的字符串列表。
     */
    public synchronized void addCustomHosts(List<String> hosts) {
        for (String host : hosts) {
            if (!host.contains("=")) continue;
            String[] splits = host.split("=");
            String originalHost = splits[0];
            String newHost = splits[1];
            customHostsMap.put(originalHost, newHost);
        }
    }

    /**
     * 清空所有自定义 HOSTS 映射。
     */
    public void clearCustomHosts() {
        customHostsMap.clear();
    }

    /**
     * 同步执行 DNS-over-HTTPS 查询。
     */
    public byte[] lookupHttpsForwardSync(String hostname) throws IOException {
        if (url == null) {
            throw new UnknownHostException("DNS-over-HTTPS URL is not set");
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().bytes();
        }
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        // 检查自定义 HOSTS 映射
        if (customHostsMap.containsKey(hostname)) {
            String mappedHost = customHostsMap.get(hostname);
            return Dns.SYSTEM.lookup(mappedHost);
        }

        // 如果没有自定义映射，继续 DNS-over-HTTPS 解析
        if (url == null) {
            return Dns.SYSTEM.lookup(hostname);
        }

        try {
            byte[] response = lookupHttpsForwardSync(hostname);
            return DnsRecordCodec.decodeAnswers(hostname, ByteString.of(response));
        } catch (IOException e) {
            throw new UnknownHostException("Failed to resolve host: " + hostname);
        }
    }

    public static final class Builder {
        private HttpUrl url;
        private OkHttpClient client;
        private List<InetAddress> bootstrapDnsHosts;

        public Builder client(OkHttpClient client) {
            this.client = client;
            return this;
        }

        public Builder url(HttpUrl url) {
            this.url = url;
            return this;
        }

        public Builder bootstrapDnsHosts(List<InetAddress> bootstrapDnsHosts) {
            this.bootstrapDnsHosts = bootstrapDnsHosts;
            return this;
        }

        public DnsOverHttps build() {
            return new DnsOverHttps(this);
        }
    }
}
