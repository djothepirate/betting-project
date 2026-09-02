package com.bettingproject.collection.adapter.configuration;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Test-only resolver proving whether Boot attempted to dereference a forbidden TLS location. */
public final class J7SentinelProtocolResolver implements ProtocolResolver {

    private static final String SCHEME = "j7sentinel:";
    private static final AtomicInteger RESOLUTION_COUNT = new AtomicInteger();

    @Override
    public Resource resolve(String location, ResourceLoader resourceLoader) {
        if (location != null && location.startsWith(SCHEME)) {
            RESOLUTION_COUNT.incrementAndGet();
            return new ByteArrayResource(new byte[] {0});
        }
        return null;
    }

    static void reset() {
        RESOLUTION_COUNT.set(0);
    }

    static int resolutionCount() {
        return RESOLUTION_COUNT.get();
    }
}
