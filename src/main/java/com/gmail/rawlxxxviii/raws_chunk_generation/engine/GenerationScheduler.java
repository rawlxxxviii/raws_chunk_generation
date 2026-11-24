package com.gmail.rawlxxxviii.raws_chunk_generation.engine;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.data.ActivitySavedData;
import com.gmail.rawlxxxviii.raws_chunk_generation.util.ServerPlayerUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GenerationScheduler {

    private static final int DIMENSION_ROTATION_TICKS = 20 * 60 * 60;

    private static double chunkBudget = 0.0;
    private static int roundRobinPlayerIndex = 0;

    private static @Nullable ResourceKey<Level> currentOfflineDimension = null;
    private static int offlineDimensionTicks = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();

        ChunkTicketManager.cleanupCompletedTickets(server);

        List<ServerPlayer> onlinePlayers = ServerPlayerUtil.getEffectiveOnlinePlayers(server);
        if (!onlinePlayers.isEmpty()) {
            tickOnlineBuffer(onlinePlayers);
        } else {
            tickOfflineActivatedAreas(server);
        }
    }

    private static void tickOnlineBuffer(List<ServerPlayer> players) {
        int radius = ServerConfig.INSTANCE.GENERATION_BUFFER_RADIUS.get();
        double speedCps = ServerConfig.INSTANCE.ACTIVE_SERVER_GENERATION_BUFFER_SPEED.get();
        int maxTickets = ServerConfig.INSTANCE.ACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS.get();

        if (radius <= 0 || speedCps <= 0.0 || maxTickets <= 0) {
            chunkBudget = 0.0;
            return;
        }

        double chunksPerTick = speedCps / 20.0;
        chunkBudget = Math.min(Math.max(1.0, chunksPerTick), chunkBudget + chunksPerTick);

        while (chunkBudget >= 1.0 && ChunkTicketManager.getTotalActiveTickets() < maxTickets) {
            ChunkPos queued = tryQueueForNextPlayer(players, radius);
            if (queued != null) {
                chunkBudget -= 1.0;
            } else {
                chunkBudget = 0.0;
                break;
            }
        }
    }

    private static @Nullable ChunkPos tryQueueForNextPlayer(List<ServerPlayer> players, int radius) {
        int playerCount = players.size();
        for (int attempt = 0; attempt < playerCount; attempt++) {
            int idx = Math.floorMod(roundRobinPlayerIndex++, playerCount);
            ServerPlayer player = players.get(idx);

            ServerLevel level = player.serverLevel();
            if (player.isSpectator() && !level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS)) {
                continue;
            }
            if (!ServerConfig.isDimensionEnabled(level.dimension())) continue;

            ChunkPos playerChunk = player.chunkPosition();
            ChunkPos nextChunk = CircleChunkFinder.findNextChunkInCircle(
                level.dimension(), playerChunk.x, playerChunk.z, radius
            );

            if (nextChunk != null) {
                ChunkTicketManager.queueTicket(level, nextChunk);
                return nextChunk;
            }
        }
        return null;
    }

    private static void tickOfflineActivatedAreas(MinecraftServer server) {
        if (server.getAverageTickTime() > 45.0f) {
            return;
        }

        int radius = ServerConfig.INSTANCE.ACTIVATED_AREA_GENERATION_RADIUS.get();
        double speedCps = ServerConfig.INSTANCE.INACTIVE_SERVER_GENERATION_BUFFER_SPEED.get();
        int maxTickets = ServerConfig.INSTANCE.INACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS.get();

        if (radius <= 0 || speedCps <= 0.0 || maxTickets <= 0) {
            chunkBudget = 0.0;
            return;
        }

        ActivitySavedData activityData = ActivitySavedData.get(server);
        List<ResourceKey<Level>> pendingDims = activityData.getDimensionsWithPendingWork().stream()
            .filter(ServerConfig::isDimensionEnabled)
            .sorted(Comparator.comparing(k -> k.location().toString()))
            .toList();

        if (pendingDims.isEmpty()) {
            currentOfflineDimension = null;
            offlineDimensionTicks = 0;
            chunkBudget = 0.0;
            return;
        }

        if (currentOfflineDimension == null || !pendingDims.contains(currentOfflineDimension)) {
            currentOfflineDimension = pendingDims.get(0);
            offlineDimensionTicks = 0;
        } else if (++offlineDimensionTicks >= DIMENSION_ROTATION_TICKS && pendingDims.size() > 1) {
            int idx = pendingDims.indexOf(currentOfflineDimension);
            currentOfflineDimension = pendingDims.get((idx + 1) % pendingDims.size());
            offlineDimensionTicks = 0;
        }

        ServerLevel targetLevel = server.getLevel(currentOfflineDimension);
        if (targetLevel == null) {
            currentOfflineDimension = null;
            return;
        }

        double chunksPerTick = speedCps / 20.0;
        chunkBudget = Math.min(Math.max(1.0, chunksPerTick), chunkBudget + chunksPerTick);

        while (chunkBudget >= 1.0 && ChunkTicketManager.getTotalActiveTickets() < maxTickets) {
            ChunkPos nextChunk = activityData.findBestOfflineChunkInDimension(currentOfflineDimension, radius);
            if (nextChunk != null) {
                ChunkTicketManager.queueTicket(targetLevel, nextChunk);
                chunkBudget -= 1.0;
            } else {
                currentOfflineDimension = null;
                chunkBudget = 0.0;
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        chunkBudget = 0.0;
        roundRobinPlayerIndex = 0;
        currentOfflineDimension = null;
        offlineDimensionTicks = 0;
        ChunkTicketManager.clearAllTickets(event.getServer());
    }
}
