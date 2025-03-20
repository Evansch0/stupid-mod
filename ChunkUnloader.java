package com.example.worldgen; // Replace with your desired package

import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkUnloader {

    private final Instance instance;
    private final ChunkCache chunkCache;
    private final Set<Long> loadedChunks = new HashSet<>();
    private final ScheduledExecutorService unloadScheduler = Executors.newScheduledThreadPool(1);

    public ChunkUnloader(final Instance instance, final ChunkCache chunkCache) {
        this.instance = instance;
        this.chunkCache = chunkCache;
    }

    public void addLoadedChunk(final int chunkX, final int chunkZ) {
        loadedChunks.add(getKey(chunkX, chunkZ));
    }

    public void removeLoadedChunk(final int chunkX, final int chunkZ) {
        loadedChunks.remove(getKey(chunkX, chunkZ));
    }

    public void unloadChunks(final Player player) {
        final int playerChunkX = player.getPosition().chunkX();
        final int playerChunkZ = player.getPosition().chunkZ();
        final int viewDistance = player.getViewDistance();

        unloadFarChunks(playerChunkX, playerChunkZ, viewDistance);
    }

    private void unloadFarChunks(final int playerChunkX, final int playerChunkZ, final int viewDistance) {
        final Set<Long> chunksToUnload = new HashSet<>(loadedChunks);

        for (int x = playerChunkX - viewDistance; x <= playerChunkX + viewDistance; x++) {
            for (int z = playerChunkZ - viewDistance; z <= playerChunkZ + viewDistance; z++) {
                chunksToUnload.remove(getKey(x, z));
            }
        }

        for (final Long chunkKey : chunksToUnload) {
            scheduleChunkUnload(getChunkX(chunkKey), getChunkZ(chunkKey));
        }
    }

    public void scheduleChunkUnload(final int chunkX, final int chunkZ) {
        removeLoadedChunk(chunkX, chunkZ);

        unloadScheduler.schedule(() -> {
            instance.unloadChunk(chunkX, chunkZ);
            chunkCache.removeChunk(chunkX, chunkZ);

        }, 30, TimeUnit.SECONDS);
    }

    private long getKey(final int chunkX, final int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private int getChunkX(final long key) {
        return (int) (key >> 32);
    }

    private int getChunkZ(final long key) {
        return (int) key;
    }

    public void shutdown() {
        unloadScheduler.shutdown();
    }
}