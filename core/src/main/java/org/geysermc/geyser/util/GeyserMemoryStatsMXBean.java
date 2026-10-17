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

/**
 * Memory-related counters, exposed over JMX as {@value GeyserMemoryStats#OBJECT_NAME}
 * so metrics extensions can read them by attribute name without compiling against Geyser internals.
 */
public interface GeyserMemoryStatsMXBean {

    int getOnlineSessions();

    int getPendingSessions();

    /**
     * Sessions that have been constructed and not yet garbage collected.
     * Staying above online + pending sessions means disconnected sessions are still referenced somewhere.
     */
    long getLiveSessionObjects();

    long getCachedChunks();

    long getCachedEntities();

    long getJavaSkinCacheSize();

    long getJavaCapeCacheSize();

    long getBedrockSkinCacheSize();

    long getBedrockCapeCacheSize();

    long getResolvedProfileCacheSize();

    long getFakeHeadCacheSize();
}
