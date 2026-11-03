/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.network.bedrock.nethernet;

import io.netty.channel.Channel;
import io.netty.util.NetUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.netty.channel.nethernet.AddressSource;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannel;
import org.cloudburstmc.netty.channel.nethernet.SourcedAddress;
import org.cloudburstmc.netty.util.nethernet.IpRangeSet;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Logs every address NetherNet saw for a peer, and where each came from, to find out what a proxy such as HAProxy in
 * front of signaling hands Geyser. Off by default. {@code -DGeyser.TraceNetherNetAddresses=true} turns it on from
 * the start, and {@code geyser nethernet trace on|off} toggles it while running, without a reload, so nobody is
 * kicked. Each traced connection carries an id, which the line logged when that player logs in repeats with their
 * name, over RakNet too. Built-in signaling status queries are traced apart, with
 * {@code -DGeyser.TraceNetherNetStatusQueries=true} or {@code geyser nethernet trace pings on|off}, as every server
 * list refresh sends one. It logs at INFO, so it shows without debug mode, and it follows "log-player-ip-addresses".
 * Tracing never stops a connection: anything it fails on is logged and skipped.
 */
public final class NetherNetAddressTrace {
    private static final String PREFIX = "[NetherNet trace] ";
    /** How many of a proxy list's ranges a hint names before it only counts the rest. */
    private static final int LISTED_RANGES = 5;
    private static volatile boolean enabled = Boolean.getBoolean("Geyser.TraceNetherNetAddresses");
    private static volatile boolean statusQueries = Boolean.getBoolean("Geyser.TraceNetherNetStatusQueries");

    private final GeyserImpl geyser;
    /** The proxies built-in signaling reads PROXY headers from, parsed once, as an entry may be a URL to fetch. */
    private final IpRangeSet proxyProtocolProxies;
    /** The proxies built-in signaling reads X-Forwarded-For from. */
    private final IpRangeSet forwardedForProxies;

    public NetherNetAddressTrace(GeyserImpl geyser, IpRangeSet proxyProtocolProxies, IpRangeSet forwardedForProxies) {
        this.geyser = geyser;
        this.proxyProtocolProxies = proxyProtocolProxies;
        this.forwardedForProxies = forwardedForProxies;
    }

    /** For a server without built-in signaling, where no proxy is in front of signaling. */
    public static NetherNetAddressTrace withoutProxies(GeyserImpl geyser) {
        return new NetherNetAddressTrace(geyser, IpRangeSet.empty(), IpRangeSet.empty());
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean statusQueriesEnabled() {
        return statusQueries;
    }

    public static void setStatusQueriesEnabled(boolean value) {
        statusQueries = value;
    }

    /**
     * Logs who a player is and the address Geyser uses for them, over either transport, so a connection traced before
     * login can be matched to a name by its id.
     */
    public static void login(GeyserSession session) {
        if (!enabled) {
            return;
        }
        GeyserImpl geyser = session.getGeyser();
        try {
            Channel channel = session.getUpstream().getSession().getPeer().getChannel();
            InetSocketAddress used = session.getUpstream().getAddress();
            String message = PREFIX + session.getAuthData().name() + " (xuid " + session.getAuthData().xuid() + ") logged in over "
                + (channel instanceof NetherNetChannel ? "NetherNet" : "RakNet") + ", connection " + channel.id().asShortText()
                + ", as " + address(geyser, used);
            if (channel.remoteAddress() instanceof InetSocketAddress remote && !remote.equals(used)) {
                // A RakNet player behind a PROXY protocol proxy
                message += ", connected from " + address(geyser, remote);
            }
            geyser.getLogger().info(message);
        } catch (RuntimeException e) {
            geyser.getLogger().warning(PREFIX + "Could not trace a login: " + e);
        }
    }

    /**
     * Logs a status query built-in signaling answered. Signaling hands over only the address it settled on, so this
     * cannot say whether a PROXY header or X-Forwarded-For named it.
     *
     * @param host the HTTP Host header, which may be absent
     */
    void statusQuery(InetSocketAddress remote, @Nullable String host) {
        if (!statusQueries) {
            return;
        }
        try {
            logStatusQuery(remote, host);
        } catch (RuntimeException e) {
            geyser.getLogger().warning(PREFIX + "Could not trace a status query: " + e);
        }
    }

    private void logStatusQuery(InetSocketAddress remote, @Nullable String host) {
        String message = PREFIX + "Built-in signaling status query from " + address(remote)
            + (host == null ? "" : " (Host: " + host + ")");
        if (proxyProtocolProxies.contains(remote)) {
            message += ". That is a proxy in \"haproxy-protocol-whitelisted-ips\", so it sent no PROXY header: "
                + "check \"send-proxy\" or \"send-proxy-v2\" on its HAProxy server line.";
        }
        geyser.getLogger().info(message);
    }

    /**
     * Logs where a NetherNet peer is, once its channel is active.
     *
     * @param listener which signaling brought the peer
     */
    void connection(NetherNetChannel channel, String listener) {
        if (!enabled) {
            return;
        }
        try {
            logConnection(channel, listener);
        } catch (RuntimeException e) {
            geyser.getLogger().warning(PREFIX + "Could not trace connection " + channel.id().asShortText() + ": " + e);
        }
    }

    private void logConnection(NetherNetChannel channel, String listener) {
        SourcedAddress signaled = channel.signaledAddress();
        SourcedAddress media = channel.mediaAddress();
        SourcedAddress reported = channel.reportedAddress();
        NetherNetChannel.Path path = channel.selectedPath();

        List<String> lines = new ArrayList<>();
        lines.add(PREFIX + "Connection " + channel.id().asShortText() + " through " + listener + ", local " + channel.localAddress());
        lines.add("  signaling: " + sourced(signaled));
        lines.add("  media:     " + sourced(media));
        lines.add("  ICE pair:  " + (path == null ? "none"
            : "local " + address(path.local()) + " (" + path.localType() + "), remote "
                + address(path.remote()) + " (" + path.remoteType() + ")"));
        lines.add("  reported:  " + sourced(reported) + ", what Geyser uses for this player");
        lines.addAll(hints(signaled, media, reported));
        geyser.getLogger().info(String.join("\n", lines));
    }

    private List<String> hints(SourcedAddress signaled, SourcedAddress media, SourcedAddress reported) {
        List<String> hints = new ArrayList<>();
        InetSocketAddress peer = signaled.address();
        if (signaled.source() == AddressSource.SIGNALING) {
            if (proxyProtocolProxies.contains(peer)) {
                hints.add("  hint: the signaling peer is in \"haproxy-protocol-whitelisted-ips\" but sent no PROXY header. "
                    + "Check \"send-proxy\" or \"send-proxy-v2\" on its HAProxy server line.");
            } else if (forwardedForProxies.contains(peer)) {
                hints.add("  hint: the signaling peer is in \"signaling-trusted-proxies\" but sent no usable X-Forwarded-For.");
            } else if (!proxyProtocolProxies.isEmpty()) {
                hints.add("  hint: the signaling peer is not in \"haproxy-protocol-whitelisted-ips\" ("
                    + describe(proxyProtocolProxies) + "). Either it reached signaling directly, or the "
                    + "proxy connects from an address the list does not cover, such as a container's NAT address.");
            }
        }
        boolean proxied = signaled.source() == AddressSource.SIGNALING_PROXY_PROTOCOL
            || signaled.source() == AddressSource.SIGNALING_FORWARDED_FOR;
        boolean mediaFromProxy = media.source() == AddressSource.ICE
            && (proxyProtocolProxies.contains(media.address()) || forwardedForProxies.contains(media.address()));
        if (mediaFromProxy) {
            // Forwarded without PROXY protocol v2, a signaling proxy stands in for every player on the WebRTC port
            hints.add("  hint: the media address is a proxy from \"haproxy-protocol-whitelisted-ips\" or "
                + "\"signaling-trusted-proxies\", so it forwards the WebRTC port without a PROXY v2 header. "
                + (reported.source() != media.source()
                    ? "Geyser reports the signaled address instead, as \"prefer-signaling-proxy-address\" is on."
                    : proxied
                        ? "Turn on \"prefer-signaling-proxy-address\" to report the address signaling's proxy sent, or "
                            + "list the proxy in \"webrtc-proxy-protocol-relays\" if it can send PROXY v2 there."
                        : "Signaling did not learn the player's address from the proxy either, so fix that first."));
        } else if (proxied && reported.source() != signaled.source()) {
            // prefer-signaling-proxy-address makes the channel report the proxied address, so it is off here
            hints.add("  hint: Geyser reports the media address, not the one the proxy named. "
                + "Turn on \"prefer-signaling-proxy-address\" to report the proxy's instead.");
        }
        if (media.source() == AddressSource.TURN) {
            hints.add("  hint: media runs through a TURN relay, so the media address is the relay's, not the player's.");
        }
        return hints;
    }

    private String sourced(SourcedAddress sourced) {
        if (sourced.source() == AddressSource.UNKNOWN) {
            return "unknown";
        }
        return address(sourced.address()) + " (" + sourced.source()
            + (sourced.via() == null ? "" : " via " + address(sourced.via())) + ")";
    }

    /** Names the first few ranges of a list, which a link expands into many. */
    private static String describe(IpRangeSet ranges) {
        List<String> entries = ranges.entries();
        if (entries.size() <= LISTED_RANGES) {
            return String.join(", ", entries);
        }
        return String.join(", ", entries.subList(0, LISTED_RANGES)) + " and " + (entries.size() - LISTED_RANGES) + " more";
    }

    private String address(@Nullable InetSocketAddress address) {
        return address(geyser, address);
    }

    private static String address(GeyserImpl geyser, @Nullable InetSocketAddress address) {
        if (address == null) {
            return "none";
        }
        return geyser.config().logPlayerIpAddresses() ? NetUtil.toSocketAddressString(address) : "<IP address withheld>";
    }
}
