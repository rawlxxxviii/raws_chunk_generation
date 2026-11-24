package com.gmail.rawlxxxviii.raws_chunk_generation.util;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChunkStateTracker {

    private static final Map<ResourceKey<Level>, LongSet> GENERATED_CHUNKS = new ConcurrentHashMap<>();

    public static boolean isGenerated(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        LongSet set = GENERATED_CHUNKS.get(dim);
        return set != null && set.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static boolean isGenerated(ResourceKey<Level> dim, ChunkPos pos) {
        return isGenerated(dim, pos.x, pos.z);
    }

    public static int getGeneratedCount(ResourceKey<Level> dim) {
        LongSet set = GENERATED_CHUNKS.get(dim);
        return set != null ? set.size() : 0;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GENERATED_CHUNKS.clear();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (ServerConfig.isDimensionEnabled(level.dimension())) {
                scanRegionFiles(level);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (event.getChunk() instanceof LevelChunk || event.getChunk().getStatus().isOrAfter(ChunkStatus.FULL)) {
                ResourceKey<Level> dim = serverLevel.dimension();
                if (!ServerConfig.isDimensionEnabled(dim)) return;

                GENERATED_CHUNKS
                    .computeIfAbsent(dim, k -> new LongOpenHashSet())
                    .add(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GENERATED_CHUNKS.clear();
    }

    private static void scanRegionFiles(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        LongSet set = GENERATED_CHUNKS.computeIfAbsent(dim, k -> new LongOpenHashSet());

        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimFolder = DimensionType.getStorageFolder(dim, worldRoot);
        Path regionDir = dimFolder.resolve("region");

        if (!Files.exists(regionDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
            byte[] headerBytes = new byte[4096];
            ByteBuffer buffer = ByteBuffer.wrap(headerBytes);

            for (Path mcaFile : stream) {
                String fileName = mcaFile.getFileName().toString();
                String[] parts = fileName.split("\\.");
                if (parts.length != 4) continue;

                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(parts[1]);
                    regionZ = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }

                try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r")) {
                    if (raf.length() < 4096) continue;
                    raf.readFully(headerBytes);
                    buffer.rewind();

                    for (int i = 0; i < 1024; i++) {
                        int locationData = buffer.getInt();
                        if (locationData != 0) {
                            int localX = i & 31;        // i % 32
                            int localZ = (i >> 5) & 31; // i / 32
                            int chunkX = (regionX << 5) + localX;
                            int chunkZ = (regionZ << 5) + localZ;
                            set.add(ChunkPos.asLong(chunkX, chunkZ));
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static byte[] getAreaStates(ServerLevel level, int startChunkX, int startChunkZ, int width, int height) {
        ResourceKey<Level> dim = level.dimension();
        LongSet genSet = GENERATED_CHUNKS.get(dim);
        var chunkSource = level.getChunkSource();

        int totalChunks = width * height;
        byte[] packedData = new byte[(totalChunks + 3) / 4];

        int index = 0;
        for (int dz = 0; dz < height; dz++) {
            int cz = startChunkZ + dz;
            for (int dx = 0; dx < width; dx++) {
                int cx = startChunkX + dx;

                byte state = 0;
                if (genSet != null && genSet.contains(ChunkPos.asLong(cx, cz))) {
                    state = 1;
                } else if (chunkSource.hasChunk(cx, cz) && chunkSource.getChunkNow(cx, cz) == null) {
                    state = 2;
                }

                if (state != 0) {
                    int byteIndex = index >> 2;
                    int bitShift = (index & 3) << 1;
                    packedData[byteIndex] |= (byte) (state << bitShift);
                }
                index++;
            }
        }
        return packedData;
    }

}
