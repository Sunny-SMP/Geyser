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

package org.geysermc.geyser.api.event.connection;

import org.geysermc.event.Cancellable;
import org.geysermc.event.Event;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * Called when a Bedrock player asks built-in NetherNet signaling to join, once their Xbox identity is validated and
 * before any WebRTC connection is set up for them. Cancelling it turns the player away at signaling with
 * "503 Service Unavailable", so nothing more is spent on them, for example while the server drains.
 * <p>
 * It fires on a signaling thread, so listeners must not block. Joins through external signaling (NXS) and RakNet do
 * not fire it; {@link ConnectionRequestEvent} covers every transport, once the connection is up.
 */
public final class NetherNetJoinRequestEvent implements Event, Cancellable {

    private final String xuid;
    private final String gamertag;
    private final @Nullable InetSocketAddress address;
    private final @Nullable String host;
    private boolean cancelled;

    public NetherNetJoinRequestEvent(String xuid, String gamertag, @Nullable InetSocketAddress address, @Nullable String host) {
        this.xuid = xuid;
        this.gamertag = gamertag;
        this.address = address;
        this.host = host;
    }

    /**
     * The XUID from the player's validated Xbox identity
     *
     * @return the player's XUID
     */
    public String xuid() {
        return xuid;
    }

    /**
     * The gamertag from the player's validated Xbox identity
     *
     * @return the player's gamertag
     */
    public String gamertag() {
        return gamertag;
    }

    /**
     * The player's address, as signaling learned it: from a trusted proxy's PROXY header or X-Forwarded-For, or else
     * the connection itself. It may differ from the address the WebRTC connection later comes from.
     *
     * @return the player's address, or null if signaling does not know it
     */
    public @Nullable InetSocketAddress address() {
        return address;
    }

    /**
     * The HTTP Host header of the join request, which names the address the player typed
     *
     * @return the Host header, or null if the request had none
     */
    public @Nullable String host() {
        return host;
    }

    /**
     * The cancel status of this event. If this event is cancelled, the player is turned away.
     *
     * @return the cancel status of this event
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancel status of this event. If this event is cancelled, the player is turned away.
     *
     * @param cancelled the cancel status of this event
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
