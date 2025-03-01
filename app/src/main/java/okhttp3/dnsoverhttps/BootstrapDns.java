package okhttp3.dnsoverhttps;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Dns;

/**
 * Internal Bootstrap DNS implementation with custom HOSTS support.
 * Returns hardcoded results for the known host or custom HOSTS mappings.
 */
final class BootstrapDns implements Dns {
    private final String dnsHostname;
    private final List<InetAddress> dnsServers;
    private final ConcurrentHashMap<String, String> customHostsMap;

    BootstrapDns(String dnsHostname, List<InetAddress> dnsServers) {
        this.dnsHostname = dnsHostname;
        this.dnsServers = dnsServers;
        this.customHostsMap = new ConcurrentHashMap<>();
    }

    /**
     * Add custom HOSTS mappings.
     *
     * @param hosts A list of strings in the format "originalHost=newHost".
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
     * Clear all custom HOSTS mappings.
     */
    public void clearCustomHosts() {
        customHostsMap.clear();
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        // Check custom HOSTS mappings first
        if (customHostsMap.containsKey(hostname)) {
            String mappedHost = customHostsMap.get(hostname);
            return Dns.SYSTEM.lookup(mappedHost);
        }

        // Fallback to original BootstrapDns logic
        if (!this.dnsHostname.equals(hostname)) {
            throw new UnknownHostException(
                "BootstrapDns called for " + hostname + " instead of " + dnsHostname);
        }

        return dnsServers;
    }
}
