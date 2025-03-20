package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;

public class TreeGenerator extends FeatureGenerator {

    private static final int TREE_CHANCE = 5;
    private static final int MIN_TREE_HEIGHT = 4;
    private static final int MAX_TREE_HEIGHT = 7;
    private static final int LEAVES_RADIUS = 2;
    private static final int LOG_ID = Block.OAK_LOG.stateId();
    private static final int LEAVES_ID = Block.OAK_LEAVES.stateId();

    public TreeGenerator(final long seed) {
        super(seed);
    }

    @Override
    public void generate(final Section section, final int chunkX, final int chunkZ, final int sectionY) {
        if (random.nextInt(100) < TREE_CHANCE) {
            final Palette palette = section.blockPalette();
            final int x = random.nextInt(Chunk.CHUNK_SIZE_X);
            final int z = random.nextInt(Chunk.CHUNK_SIZE_Z);

            final int worldX = chunkX * Chunk.CHUNK_SIZE_X + x;
            final int worldZ = chunkZ * Chunk.CHUNK_SIZE_Z + z;

            final int groundY = getGroundHeight(section, chunkX, chunkZ, x, z, sectionY);
            if (groundY + MAX_TREE_HEIGHT >= 256 || groundY == -1) return;

            final int treeHeight = MIN_TREE_HEIGHT + random.nextInt(MAX_TREE_HEIGHT - MIN_TREE_HEIGHT + 1);
            for (int i = 0; i < treeHeight; i++) {
                safeSetBlock(palette, x, groundY + i, z, LOG_ID);
            }

            final int leavesY = groundY + treeHeight;
            for (int ix = -LEAVES_RADIUS; ix <= LEAVES_RADIUS; ix++) {
                for (int iz = -LEAVES_RADIUS; iz <= LEAVES_RADIUS; iz++) {
                    for (int iy = -LEAVES_RADIUS; iy <= LEAVES_RADIUS; iy++) {
                        if (Math.sqrt(ix * ix + iz * iz + iy * iy) <= LEAVES_RADIUS) {
                            final int worldY = leavesY + iy;
                            if (worldY >= 0 && worldY < 256)
                                safeSetBlock(palette, x + ix, worldY, z + iz, LEAVES_ID);
                        }
                    }
                }
            }
        }
    }

    private int getGroundHeight(final Section section, final int chunkX, final int chunkZ, final int x, final int z, final int sectionY) {
        final Palette palette = section.blockPalette();
        for (int y = Chunk.CHUNK_SECTION_SIZE - 1; y >= 0; y--) {
            final int worldY = sectionY * Chunk.CHUNK_SECTION_SIZE + y;
            final int blockStateId = palette.get(x, y, z);
            if (blockStateId != 0) {
                return worldY;
            }
        }
        return -1;
    }
}