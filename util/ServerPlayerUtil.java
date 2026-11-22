package com.gmail.rawlxxxviii.raws_chunk_generation.util;

import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ServerPlayerUtil {

    public static List<ServerPlayer> getEffectiveOnlinePlayers(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ServerConfig.INSTANCE.OPERATORS_ACTIVATE_SERVER.get()) {
            return players;
        }
        return players.stream()
            .filter(player -> !player.hasPermissions(2))
            .toList();
    }


}
