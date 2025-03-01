package okhttp3.dnsoverhttps;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;

final class BootstrapDns implements Dns {
    private final String dnsHostname;
    private final List<InetAddress> dnsServers;

    BootstrapDns(String dnsHostname, List<InetAddress> dnsServers) {
        this.dnsHostname = dnsHostname;
        this.dnsServers = dnsServers;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (!this.dnsHostname.equals(hostname)) {
            throw new UnknownHostException(
                    "BootstrapDns called for " + hostname + " instead of " + dnsHostname);
        }

        return dnsServers;
    }
}
