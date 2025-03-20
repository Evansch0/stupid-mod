package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.ChunkGenerator;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FastWorldGenerator implements ChunkGenerator {

    private static final int STONE_ID = Block.STONE.stateId();
    private static final int GRASS_ID = Block.GRASS_BLOCK.stateId();
    private static final int DIRT_ID = Block.DIRT.stateId();
    private static final int BASE_HEIGHT = 60;
    private static final float NOISE_SCALE = 0.01f;
    private static final int NOISE_HEIGHT_RANGE = 20;
    private static final float BIOME_NOISE_SCALE = 0.002f;
    private static final float NOISE_3D_SCALE = 0.02f;
    private static final float NOISE_3D_THRESHOLD = 0.2f;

    private final FastNoiseLite noise;
    private final FastNoiseLite biomeNoise;
    private final FastNoiseLite noise3D;
    private final List<FeatureGenerator> featureGenerators;
    private final ExecutorService chunkExecutor;
    private final ExecutorService lightingExecutor;
    private final ChunkCache chunkCache;
    private final LightingEngine lightingEngine;

    public FastWorldGenerator(final int cacheCapacity, final int threadPoolSize) {
        noise = new FastNoiseLite();
        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetSeed((int) (Math.random() * Integer.MAX_VALUE));
        noise.SetFrequency(NOISE_SCALE);

        biomeNoise = new FastNoiseLite();
        biomeNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        biomeNoise.SetSeed((int) (Math.random() * Integer.MAX_VALUE));
        biomeNoise.SetFrequency(BIOME_NOISE_SCALE);

        noise3D = new FastNoiseLite();
        noise3D.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise3D.SetSeed((int) (Math.random() * Integer.MAX_VALUE));
        noise3D.SetFrequency(NOISE_3D_SCALE);

        final long seed = ThreadLocalRandom.current().nextLong();
        featureGenerators.add(new TreeGenerator(seed));
        featureGenerators.add(new OreGenerator(seed, Block.STONE));

        chunkExecutor = Executors.newFixedThreadPool(threadPoolSize);
        lightingExecutor = Executors.newFixedThreadPool(threadPoolSize); // Separate thread pool
        chunkCache = new ChunkCache(cacheCapacity);
        lightingEngine = new LightingEngine(chunkCache); // Pass ChunkCache to LightingEngine

    }

    @Override
    public @NotNull CompletableFuture<Chunk> generate(@NotNull final Instance instance, final int chunkX, final int chunkZ) {
        final Chunk cachedChunk = chunkCache.getChunk(chunkX, chunkZ);
        if (cachedChunk != null) {
            return CompletableFuture.completedFuture(cachedChunk);
        }

        return CompletableFuture.supplyAsync(() -> {
            final Chunk chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);
            // Generate sections in parallel
            final List<CompletableFuture<Section>> sectionFutures = new ArrayList<>();
            for (int sectionY = 0; sectionY < 16; sectionY++) {
                final int finalSectionY = sectionY;
                sectionFutures.add(CompletableFuture.supplyAsync(() -> generateSection(chunkX, chunkZ, finalSectionY), chunkExecutor));
            }

            // Combine the results of all section generation tasks
            final CompletableFuture<Void> allSectionsFuture = CompletableFuture.allOf(sectionFutures.toArray(new CompletableFuture[0]));

            // When all sections are complete, set them in the chunk and calculate lighting
            allSectionsFuture.thenRunAsync(() -> {
                for (int sectionY = 0; sectionY < sectionFutures.size(); sectionY++) {
                    try {
                        final Section section = sectionFutures.get(sectionY).get();
                        chunk.getSections().set(sectionY, section);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace(); // Handle exceptions appropriately
                    }
                }

                // --- LIGHTING ---
                lightingEngine.lightChunk(instance, chunkX, chunkZ);

                chunkCache.putChunk(chunkX, chunkZ, chunk); // Add to cache AFTER lighting
            }, lightingExecutor).join(); // Use lightingExecutor and .join()

            return chunk;

        }, chunkExecutor);
    }


    private Section generateSection(final int chunkX, final int chunkZ, final int sectionY) {
        final Section section = new Section();
        final Palette palette = section.blockPalette();

        final float[][][] biomeNoiseValues = precalculateBiomeNoise(chunkX, chunkZ);

        // Create a list of futures for column generation
        final List<CompletableFuture<Void>> columnFutures = new ArrayList<>();

        for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                final int finalX = x;
                final int finalZ = z;
                columnFutures.add(CompletableFuture.runAsync(() -> {
                    generateColumn(palette, biomeNoiseValues, chunkX, chunkZ, sectionY, finalX, finalZ);
                }, chunkExecutor));
            }
        }

        // Wait for all column generation tasks to complete
        CompletableFuture.allOf(columnFutures.toArray(new CompletableFuture[0])).join();

        // Feature Generation (Parallel)
        final List<CompletableFuture<Void>> featureFutures = new ArrayList<>();
        for (final FeatureGenerator generator : featureGenerators) {
            final int finalChunkX = chunkX;
            final int finalChunkZ = chunkZ;
            final int finalSectionY = sectionY;
            featureFutures.add(CompletableFuture.runAsync(() -> {
                generator.generate(section, finalChunkX, finalChunkZ, finalSectionY);
            }, chunkExecutor));
        }
        CompletableFuture.allOf(featureFutures.toArray(new CompletableFuture[0])).join();

        return section;
    }

    private void generateColumn(final Palette palette, final float[][][] biomeNoiseValues,
                                final int chunkX, final int chunkZ, final int sectionY, final int x, final int z) {
        final int worldX = chunkX * Chunk.CHUNK_SIZE_X + x;
        final int worldZ = chunkZ * Chunk.CHUNK_SIZE_Z + z;

        // Biome Selection and Blending
        final Biome primaryBiome = getPrimaryBiome(biomeNoiseValues, x, z);
        final float biomeHeightVariation;
        final short topBlock;
        final short underBlock;

        if (isBiomeEdge(biomeNoiseValues, x, z)) {
            biomeHeightVariation = calculateBlendedHeightVariation(biomeNoiseValues, x, z);
            topBlock = calculateBlendedTopBlock(biomeNoiseValues, x, z);
            underBlock = calculateBlendedUnderBlock(biomeNoiseValues, x, z);
        } else {
            biomeHeightVariation = primaryBiome.heightVariation;
            topBlock = primaryBiome.topBlock;
            underBlock = primaryBiome.underBlock;
        }

        // Terrain Generation
        final float noiseValue = noise.GetNoise(worldX, worldZ);
        int height = (int) (BASE_HEIGHT + noiseValue * biomeHeightVariation);
        height = Math.max(0, Math.min(height, 255));

        for (int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
            final int worldY = sectionY * Chunk.CHUNK_SECTION_SIZE + y;

            // 3D Noise for Caves
            final float noise3DValue = noise3D.GetNoise(worldX, worldY, worldZ);
            final boolean isCave = noise3DValue > NOISE_3D_THRESHOLD;

            if (!isCave) {
                if (worldY < height - 3) {
                    palette.set(x, y, z, STONE_ID);
                } else if (worldY < height - 1) {
                    palette.set(x, y, z, underBlock);
                } else if (worldY < height) {
                    palette.set(x, y, z, topBlock);
                }
            }
        }
    }

    // --- Biome Helper Methods ---
    private float[][][] precalculateBiomeNoise(final int chunkX, final int chunkZ) {
        final float[][][] biomeNoiseValues = new float[Chunk.CHUNK_SIZE_X + 2][1][Chunk.CHUNK_SIZE_Z + 2];
        for (int x = -1; x < Chunk.CHUNK_SIZE_X + 1; x++) {
            for (int z = -1; z < Chunk.CHUNK_SIZE_Z + 1; z++) {
                final int worldX = chunkX * Chunk.CHUNK_SIZE_X + x;
                final int worldZ = chunkZ * Chunk.CHUNK_SIZE_Z + z;
                biomeNoiseValues[x + 1][0][z + 1] = biomeNoise.GetNoise(worldX, worldZ);
            }
        }
        return biomeNoiseValues;
    }

    private Biome getPrimaryBiome(final float[][][] biomeNoiseValues, final int x, final int z) {
        final float centerNoise = biomeNoiseValues[x + 1][0][z + 1];
        return getBiomeFromNoise(centerNoise);
    }

    private boolean isBiomeEdge(final float[][][] biomeNoiseValues, final int x, final int z) {
        final float n00 = biomeNoiseValues[x][0][z];
        final float n10 = biomeNoiseValues[x + 1][0][z];
        final float n01 = biomeNoiseValues[x][0][z + 1];
        final float n11 = biomeNoiseValues[x + 1][0][z + 1];
        final Biome primaryBiome = getPrimaryBiome(biomeNoiseValues, x, z);
        return getBiomeFromNoise(n00) != primaryBiome || getBiomeFromNoise(n10) != primaryBiome ||
                getBiomeFromNoise(n01) != primaryBiome || getBiomeFromNoise(n11) != primaryBiome;
    }

    private float calculateBlendedHeightVariation(final float[][][] biomeNoiseValues, final int x, final int z) {
        final float blendFactorX = (x / (float) Chunk.CHUNK_SIZE_X);
        final float blendFactorZ = (z / (float) Chunk.CHUNK_SIZE_Z);

        final Biome biome00 = getBiomeFromNoise(biomeNoiseValues[x][0][z]);
        final Biome biome10 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z]);
        final Biome biome01 = getBiomeFromNoise(biomeNoiseValues[x][0][z + 1]);
        final Biome biome11 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z + 1]);

        return (
                biome00.heightVariation * (1 - blendFactorX) * (1 - blendFactorZ) +
                        biome10.heightVariation * blendFactorX * (1 - blendFactorZ) +
                        biome01.heightVariation * (1 - blendFactorX) * blendFactorZ +
                        biome11.heightVariation * blendFactorX * blendFactorZ
        );
    }

    private short calculateBlendedTopBlock(final float[][][] biomeNoiseValues, final int x, final int z) {
        final int[] topBlockCounts = new int[65536];
        final Biome biome00 = getBiomeFromNoise(biomeNoiseValues[x][0][z]);
        final Biome biome10 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z]);
        final Biome biome01 = getBiomeFromNoise(biomeNoiseValues[x][0][z + 1]);
        final Biome biome11 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z + 1]);
        topBlockCounts[biome00.topBlock]++;
        topBlockCounts[biome10.topBlock]++;
        topBlockCounts[biome01.topBlock]++;
        topBlockCounts[biome11.topBlock]++;
        short topBlock = 0;
        int maxTopCount = 0;
        for (int i = 0; i < topBlockCounts.length; i++) {
            if (topBlockCounts[i] > maxTopCount) {
                maxTopCount = topBlockCounts[i];
                topBlock = (short) i;
            }
        }
        return topBlock;
    }

    private short calculateBlendedUnderBlock(final float[][][] biomeNoiseValues, final int x, final int z) {
        final int[] underBlockCounts = new int[65536];
        final Biome biome00 = getBiomeFromNoise(biomeNoiseValues[x][0][z]);
        final Biome biome10 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z]);
        final Biome biome01 = getBiomeFromNoise(biomeNoiseValues[x][0][z + 1]);
        final Biome biome11 = getBiomeFromNoise(biomeNoiseValues[x + 1][0][z + 1]);
        underBlockCounts[biome00.underBlock]++;
        underBlockCounts[biome10.underBlock]++;
        underBlockCounts[biome01.underBlock]++;
        underBlockCounts[biome11.underBlock]++;
        short underBlock = 0;
        int maxUnderCount = 0;
        for (int i = 0; i < underBlockCounts.length; i++) {
            if (underBlockCounts[i] > maxUnderCount) {
                maxUnderCount = underBlockCounts[i];
                underBlock = (short) i;
            }
        }
        return underBlock;
    }

    private Biome getBiomeFromNoise(final float noiseValue) {
        if (noiseValue < -0.3) {
            return Biome.DESERT;
        } else if (noiseValue < 0.3) {
            return Biome.PLAINS;
        } else {
            return Biome.FOREST;
        }
    }

    @Override
    public boolean shouldGenerate(final int chunkX, final int chunkZ) {
        return true;
    }

    @Override
    public void generateChunkData(@NotNull final ChunkBatch batch, final int chunkX, final int chunkZ) {
        // Not used, section based generation.
    }

    public void shutdown() {
        chunkExecutor.shutdown();
        lightingExecutor.shutdown();
    }
}