package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;

import java.util.Arrays;

public class LightingEngine {

    private final ChunkCache chunkCache;

    public LightingEngine(final ChunkCache chunkCache) {
        this.chunkCache = chunkCache;
    }

    public void lightChunk(final Instance instance, final int chunkX, final int chunkZ) {
        final Chunk chunk = instance.getChunk(chunkX, chunkZ);
        if (chunk == null) return;

        for (int sectionY = 0; sectionY < chunk.getSections().size(); sectionY++) {
            relightSection(chunk, sectionY);
        }
    }

    private void relightSection(final Chunk chunk, final int sectionY) {
        final Section section = chunk.getSections().get(sectionY);
        final Palette lightPalette = section.light();

        lightPalette.fill(0);

        propagateSkyLight(chunk, sectionY);
        propagateBlockLight(chunk, sectionY);
    }

    private void propagateSkyLight(final Chunk chunk, final int sectionY) {
        final Section section = chunk.getSections().get(sectionY);
        final Palette blockPalette = section.blockPalette();
        final Palette lightPalette = section.light();

        final int[] queueX = new int[16 * 16 * 16];
        final int[] queueY = new int[16 * 16 * 16];
        final int[] queueZ = new int[16 * 16 * 16];
        final int[] queueLight = new int[16 * 16 * 16];
        int head = 0;
        int tail = 0;

        if (sectionY == chunk.getSections().size() - 1) {
            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    queueX[tail] = x;
                    queueY[tail] = Chunk.CHUNK_SECTION_SIZE - 1;
                    queueZ[tail] = z;
                    queueLight[tail] = 15;
                    tail++;
                }
            }
        } else {
            final Section sectionAbove = chunk.getSections().get(sectionY + 1);
            final Palette lightPaletteAbove = sectionAbove.light();

            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    final int skyLightAbove = getSkyLight(lightPaletteAbove, x, 0, z);
                    if (skyLightAbove > 0) {
                        queueX[tail] = x;
                        queueY[tail] = Chunk.CHUNK_SECTION_SIZE - 1;
                        queueZ[tail] = z;
                        queueLight[tail] = skyLightAbove;
                        tail++;
                    }
                }
            }
        }

        while (head < tail) {
            final int x = queueX[head];
            final int y = queueY[head];
            final int z = queueZ[head];
            final int lightLevel = queueLight[head];
            head++;

            final int currentLight = getSkyLight(lightPalette, x, y, z);
            if (lightLevel > currentLight) {
                setSkyLight(lightPalette, x, y, z, lightLevel);

                int nextLightLevel = lightLevel;
                if (blockPalette.get(x, y, z) != 0)
                    nextLightLevel -= 1;

                if (nextLightLevel > 0) {
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x + 1, y, z, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x - 1, y, z, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y + 1, z, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y - 1, z, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y, z + 1, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y, z - 1, nextLightLevel, sectionY, true);
                    tail = queueX.length == tail ? tail : tail + 1;
                }
            }
        }
        Arrays.fill(queueX, 0);
        Arrays.fill(queueY, 0);
        Arrays.fill(queueZ, 0);
        Arrays.fill(queueLight, 0);
    }

    private void propagateBlockLight(final Chunk chunk, final int sectionY) {
        final Section section = chunk.getSections().get(sectionY);
        final Palette blockPalette = section.blockPalette();
        final Palette lightPalette = section.light();

        final int[] queueX = new int[16 * 16 * 16];
        final int[] queueY = new int[16 * 16 * 16];
        final int[] queueZ = new int[16 * 16 * 16];
        final int[] queueLight = new int[16 * 16 * 16];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
            for (int y = 0; y < Chunk.CHUNK_SECTION_SIZE; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    final int blockStateId = blockPalette.get(x, y, z);
                    final Block block = Block.fromStateId((short) blockStateId);
                    if (block != null && block.luminosity() > 0) {
                        queueX[tail] = x;
                        queueY[tail] = y;
                        queueZ[tail] = z;
                        queueLight[tail] = block.luminosity();
                        tail++;
                    }
                }
            }
        }

        while (head < tail) {
            final int x = queueX[head];
            final int y = queueY[head];
            final int z = queueZ[head];
            final int lightLevel = queueLight[head];
            head++;

            final int currentLight = getBlockLight(lightPalette, x, y, z);
            if (lightLevel > currentLight) {
                setBlockLight(lightPalette, x, y, z, lightLevel);

                int nextLightLevel = lightLevel;
                if (blockPalette.get(x, y, z) != 0)
                    nextLightLevel -= 1;

                if (nextLightLevel > 0) {
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x + 1, y, z, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x - 1, y, z, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y + 1, z, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y - 1, z, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y, z + 1, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                    addNeighborData(queueX, queueY, queueZ, queueLight, tail, chunk, x, y, z - 1, nextLightLevel, sectionY, false);
                    tail = queueX.length == tail ? tail : tail + 1;
                }
            }
        }
        Arrays.fill(queueX, 0);
        Arrays.fill(queueY, 0);
        Arrays.fill(queueZ, 0);
        Arrays.fill(queueLight, 0);
    }

    private void addNeighborData(final int[] queueX, final int[] queueY, final int[] queueZ, final int[] queueLight, int tail,
                                 final Chunk chunk, final int x, final int y, final int z, final int lightLevel, final int sectionY, final boolean isSkyLight) {
        if (x >= 0 && x < Chunk.CHUNK_SIZE_X && y >= 0 && y < Chunk.CHUNK_SECTION_SIZE && z >= 0 && z < Chunk.CHUNK_SIZE_Z) {
            queueX[tail] = x;
            queueY[tail] = y;
            queueZ[tail] = z;
            queueLight[tail] = lightLevel;
        } else {
            int neighborChunkX = chunk.getChunkX();
            int neighborChunkZ = chunk.getChunkZ();
            int neighborSectionY = sectionY;

            if (x < 0) {
                neighborChunkX--;
                x = Chunk.CHUNK_SIZE_X - 1;
            } else if (x >= Chunk.CHUNK_SIZE_X) {
                neighborChunkX++;
                x = 0;
            }
            if (z < 0) {
                neighborChunkZ--;
                z = Chunk.CHUNK_SIZE_Z - 1;
            } else if (z >= Chunk.CHUNK_SIZE_Z) {
                neighborChunkZ++;
                z = 0;
            }
            if (y < 0) {
                neighborSectionY--;
                y = Chunk.CHUNK_SECTION_SIZE - 1;
            } else if (y >= Chunk.CHUNK_SECTION_SIZE) {
                neighborSectionY++;
                y = 0;
            }
            if (neighborSectionY < 0 || neighborSectionY > 15) return;

            final Chunk neighborChunk = chunkCache.getChunk(neighborChunkX, neighborChunkZ);
            if (neighborChunk != null) {
                if (neighborSectionY >= 0 && neighborSectionY < neighborChunk.getSections().size()) {
                    final Section neighborSection = neighborChunk.getSections().get(neighborSectionY);
                    final Palette neighborLightPalette = neighborSection.light();

                    final int existingLight = isSkyLight ? getSkyLight(neighborLightPalette, x, y, z) : getBlockLight(neighborLightPalette, x, y, z);
                    if (lightLevel > existingLight) {
                        queueX[tail] = x;
                        queueY[tail] = y;
                        queueZ[tail] = z;
                        queueLight[tail] = lightLevel;
                    }
                }
            }
        }
    }

    private int getSkyLight(final Palette palette, final int x, final int y, final int z) {
        return (palette.get(x, y, z) >> 4) & 0xF;
    }

    private void setSkyLight(final Palette palette, final int x, final int y, final int z, final int lightLevel) {
        final int combined = palette.get(x, y, z);
        palette.set(x, y, z, (combined & 0xF) | (lightLevel << 4));
    }

    private int getBlockLight(final Palette palette, final int x, final int y, final int z) {
        return palette.get(x, y, z) & 0xF;
    }

    private void setBlockLight(final Palette palette, final int x, final int y, final int z, final int lightLevel) {
        final int combined = palette.get(x, y, z);
        palette.set(x, y, z, (combined & 0xF0) | lightLevel);
    }
}