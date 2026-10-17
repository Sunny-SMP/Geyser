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

package org.geysermc.geyser.util;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.SessionManager;
import org.geysermc.geyser.skin.FakeHeadProvider;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.skin.SkinProvider;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;

public final class GeyserMemoryStats implements GeyserMemoryStatsMXBean {
    public static final String OBJECT_NAME = "org.geysermc.geyser:type=MemoryStats";

    private static final AtomicLong LIVE_SESSIONS = new AtomicLong();
    private static final Cleaner CLEANER = Cleaner.create();

    private final GeyserImpl geyser;

    private GeyserMemoryStats(GeyserImpl geyser) {
        this.geyser = geyser;
    }

    /**
     * Counts the session until it is garbage collected. The cleaning action only captures the counter,
     * capturing the session would keep it reachable forever.
     */
    public static void trackSession(GeyserSession session) {
        LIVE_SESSIONS.incrementAndGet();
        CLEANER.register(session, LIVE_SESSIONS::decrementAndGet);
    }

    public static void register(GeyserImpl geyser) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(OBJECT_NAME);
            // Left behind by a previous instance when reloading
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
            server.registerMBean(new GeyserMemoryStats(geyser), name);
        } catch (Exception e) {
            geyser.getLogger().debug("Unable to register memory stats MBean: " + e);
        }
    }

    public static void unregister() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(OBJECT_NAME);
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public int getOnlineSessions() {
        return geyser.getSessionManager().getSessions().size();
    }

    @Override
    public int getPendingSessions() {
        SessionManager sessionManager = geyser.getSessionManager();
        return sessionManager.size() - sessionManager.getSessions().size();
    }

    @Override
    public long getLiveSessionObjects() {
        return LIVE_SESSIONS.get();
    }

    // The per-session sizes below are read off the session's event loop without locking.
    // A slightly stale number is fine for metrics.

    @Override
    public long getCachedChunks() {
        long total = 0;
        for (GeyserSession session : geyser.getSessionManager().getAllSessions()) {
            total += session.getChunkCache().size();
        }
        return total;
    }

    @Override
    public long getCachedEntities() {
        long total = 0;
        for (GeyserSession session : geyser.getSessionManager().getAllSessions()) {
            total += session.getEntityCache().getEntitiesUnsafe().size();
        }
        return total;
    }

    @Override
    public long getJavaSkinCacheSize() {
        return SkinProvider.javaSkinCacheSize();
    }

    @Override
    public long getJavaCapeCacheSize() {
        return SkinProvider.javaCapeCacheSize();
    }

    @Override
    public long getBedrockSkinCacheSize() {
        return SkinProvider.bedrockSkinCacheSize();
    }

    @Override
    public long getBedrockCapeCacheSize() {
        return SkinProvider.bedrockCapeCacheSize();
    }

    @Override
    public long getResolvedProfileCacheSize() {
        return SkinManager.resolvedProfileCacheSize();
    }

    @Override
    public long getFakeHeadCacheSize() {
        return FakeHeadProvider.cacheSize();
    }
}
