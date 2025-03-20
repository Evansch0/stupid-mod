package com.example.worldgen; // Replace with your desired package
import net.minestom.server.instance.Section;
import net.minestom.server.instance.palette.Palette;

import java.util.Random;

public abstract class FeatureGenerator {

    protected final Random random;

    public FeatureGenerator(final long seed) {
        this.random = new Random(seed);
    }

    public abstract void generate(final Section section, final int chunkX, final int chunkZ, final int sectionY);

    protected void safeSetBlock(final Palette palette, final int x, final int y, final int z, final int blockStateId) {
        if (y >= 0 && y < 256) {
            palette.set(x, y, z, blockStateId);
        }
    }
}