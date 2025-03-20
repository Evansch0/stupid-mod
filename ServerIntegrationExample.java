import com.example.worldgen.ChunkLoader;
import com.example.worldgen.ChunkUnloader;
import com.example.worldgen.FastWorldGenerator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.world.DimensionType;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class ServerIntegrationExample {

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();

        //Create the instance
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);

        // --- World Generation Setup ---
        int cacheCapacity = 1024; // Example capacity
        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;  // Example: 2x cores
        FastWorldGenerator chunkGenerator = new FastWorldGenerator(cacheCapacity, threadPoolSize);

        //Set the chunk generator
        BiFunction<InstanceContainer, int[], CompletableFuture<Chunk>> chunkSupplier = (instance, ints) -> {
            int chunkX = ints[0];
            int chunkZ = ints[1];

            // Get the chunk from the cache if present
			// USE THE SAME CACHE.
            Chunk cachedChunk = chunkGenerator.getChunkCache().getChunk(chunkX, chunkZ);
            if (cachedChunk != null) {
                return CompletableFuture.completedFuture(cachedChunk);
            }

            // Otherwise, use the generator
            return chunkGenerator.generate(instance, chunkX, chunkZ);
        };

        instanceContainer.setChunkSupplier(chunkSupplier);
        instanceContainer.setChunkGenerator(chunkGenerator); //also register the chunk generator.

        //Create the chunk loader and unloader
        ChunkLoader chunkLoader = new ChunkLoader(chunkGenerator, instanceContainer);
        ChunkUnloader chunkUnloader = new ChunkUnloader(instanceContainer, chunkGenerator.getChunkCache());

        // --- Event Handlers ---
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 65, 0)); // Example spawn point

        });

        globalEventHandler.addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();

            //load chunks on spawn.
            chunkLoader.loadChunksAroundPlayer(player);
        });

        //load and unload chunks: (example).
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
            for (Player player : players) {
                //load + unload.
                chunkLoader.loadChunksAroundPlayer(player);
                chunkUnloader.unloadChunks(player); //unload chunks no longer in view.
            }
        }).repeat(50, net.minestom.server.timer.TimeUnit.MILLISECOND).schedule(); //repeat task.

        // --- Start the Server ---
        minecraftServer.start("0.0.0.0", 25565);
    }
}