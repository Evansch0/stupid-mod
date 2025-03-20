package com.example.worldgen; // Replace with your desired package

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoader {

    private final FastWorldGenerator chunkGenerator;
    private final Instance instance;
    private final Map<Long, CompletableFuture<Chunk>> pendingLoads = new ConcurrentHashMap<>();


    public ChunkLoader(final FastWorldGenerator chunkGenerator, final Instance instance) {
        this.chunkGenerator = chunkGenerator;
        this.instance = instance;
    }

    public void loadChunksAroundPlayer(final Player player) {
        final Point playerPosition = player.getPosition();
        final int playerChunkX = playerPosition.chunkX();
        final int playerChunkZ = playerPosition.chunkZ();
        final int viewDistance = player.getViewDistance();

        cancelOutOfRangeLoads(playerChunkX, playerChunkZ, viewDistance);

        final PriorityQueue<ChunkLoadTask> chunkLoadQueue = new PriorityQueue<>(
                Comparator.comparingInt(ChunkLoadTask::getPriority)
        );

        for (int x = playerChunkX - viewDistance; x <= playerChunkX + viewDistance; x++) {
            for (int z = playerChunkZ - viewDistance; z <= playerChunkZ + viewDistance; z++) {
                final double distance = Math.sqrt(
                        Math.pow(x - playerChunkX, 2) + Math.pow(z - playerChunkZ, 2)
                );
                final long chunkKey = getKey(x, z);
                if (!pendingLoads.containsKey(chunkKey)) {
                    chunkLoadQueue.add(new ChunkLoadTask(x, z, (int) distance, instance, chunkGenerator));
                }
            }
        }

        while (!chunkLoadQueue.isEmpty()) {
            final ChunkLoadTask task = chunkLoadQueue.poll();
            final int chunkX = task.getChunkX();
            final int chunkZ = task.getChunkZ();
            final long chunkKey = getKey(chunkX, chunkZ);
            final CompletableFuture<Chunk> future = task.getChunkGenerator().generate(task.getInstance(), chunkX, chunkZ);
            pendingLoads.put(chunkKey, future);

            future.thenAccept(chunk -> {
                pendingLoads.remove(chunkKey);
            });
        }
    }

    private void cancelOutOfRangeLoads(final int playerChunkX, final int playerChunkZ, final int viewDistance) {
        for (final Map.Entry<Long, CompletableFuture<Chunk>> entry : pendingLoads.entrySet()) {
            final long chunkKey = entry.getKey();
            final int chunkX = getChunkX(chunkKey);
            final int chunkZ = getChunkZ(chunkKey);

            if (Math.abs(chunkX - playerChunkX) > viewDistance || Math.abs(chunkZ - playerChunkZ) > viewDistance) {
                final CompletableFuture<Chunk> future = entry.getValue();
                if (!future.isDone() && !future.isCancelled()) {
                    future.cancel(false);
                }
                pendingLoads.remove(chunkKey);
            }
        }
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
        chunkGenerator.shutdown();
        for (final CompletableFuture<Chunk> future : pendingLoads.values()) {
            if (!future.isDone() && !future.isCancelled()) {
                future.cancel(false);
            }
        }
        pendingLoads.clear();
    }

    private static class ChunkLoadTask {
        private final int chunkX;
        private final int chunkZ;
        private final int priority;
        private final Instance instance;
        private final FastWorldGenerator chunkGenerator;

        public ChunkLoadTask(final int chunkX, final int chunkZ, final int priority, final Instance instance, final FastWorldGenerator chunkGenerator) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.priority = priority;
            this.instance = instance;
            this.chunkGenerator = chunkGenerator;
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        public Instance getInstance() {
            return instance;
        }

        public FastWorldGenerator getChunkGenerator() {
            return chunkGenerator;
        }

        public int getPriority() {
            return priority;
        }
    }
}