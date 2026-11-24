package com.gmail.rawlxxxviii.raws_chunk_generation.data;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerActivityTracker {

    private static final int TRACK_INTERVAL_TICKS = 20 * 60 * 4;

    private static void tryRecordPlayer(ServerPlayer player) {

        ServerLevel level = player.serverLevel();

        if (
            (!ServerConfig.INSTANCE.OPERATORS_ACTIVATE_AREAS.get() && player.hasPermissions(2))
            || (player.isSpectator() && !level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS))
            || !ServerConfig.isDimensionEnabled(level.dimension())
        ){
            return;
        }

        ActivitySavedData.get(player.server).recordActivity(
            level.dimension(),
            player.getUUID(),
            player.chunkPosition()
        );
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tryRecordPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (event.getServer().getTickCount() % TRACK_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                tryRecordPlayer(player);
            }
        }
    }
}
