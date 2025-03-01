/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
