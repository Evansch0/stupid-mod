package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.Chunk;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChunkCache {

    private final int capacity;
    private final Map<Long, Chunk> cache;

    public ChunkCache(final int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<Long, Chunk> eldest) {
                return size() > capacity;
            }
        };
    }

    public Chunk getChunk(final int chunkX, final int chunkZ) {
        return cache.get(getKey(chunkX, chunkZ));
    }

    public void putChunk(final int chunkX, final int chunkZ, final Chunk chunk) {
        cache.put(getKey(chunkX, chunkZ), chunk);
    }

    public void removeChunk(final int chunkX, final int chunkZ) {
        cache.remove(getKey(chunkX, chunkZ));
    }

    public void clear() {
        cache.clear();
    }

    private long getKey(final int chunkX, final int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}