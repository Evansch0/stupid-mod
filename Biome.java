package com.example.worldgen; // Replace with your desired package

import net.minestom.server.instance.block.Block;

enum Biome {
    PLAINS(0, 5, Block.GRASS_BLOCK.stateId(), Block.DIRT.stateId()),
    FOREST(1, 15, Block.GRASS_BLOCK.stateId(), Block.DIRT.stateId()),
    DESERT(2, 3, Block.SAND.stateId(), Block.SANDSTONE.stateId());

    public final int id;
    public final int heightVariation;
    public final short topBlock;
    public final short underBlock;

    Biome(final int id, final int heightVariation, final short topBlock, final short underBlock) {
        this.id = id;
        this.heightVariation = heightVariation;
        this.topBlock = topBlock;
        this.underBlock = underBlock;
    }

    private static final Biome[] byId = new Biome[values().length];

    static {
        for (final Biome biome : values()) {
            byId[biome.id] = biome;
        }
    }

    public static Biome getById(final int id) {
        return byId[id];
    }
}