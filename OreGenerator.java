package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;

public class OreGenerator extends FeatureGenerator {

    private static final int COAL_VEIN_SIZE = 17;
    private static final int COAL_VEINS_PER_CHUNK = 20;
    private static final int COAL_MIN_HEIGHT = 0;
    private static final int COAL_MAX_HEIGHT = 128;
    private static final int COAL_ID = Block.COAL_ORE.stateId();
    private static final float ORE_NOISE_SCALE = 0.05f;
    private final FastNoiseLite oreNoise;
    private final Block targetBlock;

    public OreGenerator(final long seed, final Block targetBlock) {
        super(seed);
        oreNoise = new FastNoiseLite((int) seed);
        oreNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        oreNoise.SetFrequency(ORE_NOISE_SCALE);
        this.targetBlock = targetBlock;
    }

    @Override
    public void generate(final Section section, final int chunkX, final int chunkZ, final int sectionY) {
        final Palette palette = section.blockPalette();
        for (int i = 0; i < COAL_VEINS_PER_CHUNK; i++) {
            if (random.nextInt(100) < 50) {
                int x = random.nextInt(Chunk.CHUNK_SIZE_X);
                int z = random.nextInt(Chunk.CHUNK_SIZE_Z);
                int y = random.nextInt(Chunk.CHUNK_SECTION_SIZE);
                final int worldY = sectionY * Chunk.CHUNK_SECTION_SIZE + y;

                if (worldY >= COAL_MIN_HEIGHT && worldY <= COAL_MAX_HEIGHT) {
                    int airCount = 0;

                    if (x > 0 && palette.get(x - 1, y, z) == 0)
                        airCount++;
                    if (x < Chunk.CHUNK_SIZE_X - 1 && palette.get(x + 1, y, z) == 0)
                        airCount++;
                    if (y > 0 && palette.get(x, y - 1, z) == 0)
                        airCount++;
                    if (y < Chunk.CHUNK_SECTION_SIZE - 1 && palette.get(x, y + 1, z) == 0)
                        airCount++;
                    if (z > 0 && palette.get(x, y, z - 1) == 0)
                        airCount++;
                    if (z < Chunk.CHUNK_SIZE_Z - 1 && palette.get(x, y, z + 1) == 0)
                        airCount++;

                    if (airCount >= 4) continue;
                    generateVein(palette, x, y, z, chunkX, chunkZ);
                }
            }
        }
    }

    private void generateVein(final Palette palette, int startX, int startY, int startZ, final int chunkX, final int chunkZ) {
        for (int i = 0; i < COAL_VEIN_SIZE; i++) {
            final int worldX = chunkX * Chunk.CHUNK_SIZE_X + startX;
            final int worldZ = chunkZ * Chunk.CHUNK_SIZE_Z + startZ;
            final int worldY = startY;

            final float noiseValue = oreNoise.GetNoise(worldX, worldY, worldZ);

            if (noiseValue > 0.6) {
                if (palette.get(startX, startY, startZ) == targetBlock.stateId()) {
                    safeSetBlock(palette, startX, worldY, startZ, COAL_ID);
                }
            }

            startX += random.nextInt(3) - 1;
            startY += random.nextInt(3) - 1;
            startZ += random.nextInt(3) - 1;

            startX = Math.max(0, Math.min(startX, Chunk.CHUNK_SIZE_X - 1));
            startY = Math.max(0, Math.min(startY, Chunk.CHUNK_SECTION_SIZE - 1));
            startZ = Math.max(0, Math.min(startZ, Chunk.CHUNK_SIZE_Z - 1));
        }
    }
}