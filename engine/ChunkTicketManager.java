package com.gmail.rawlxxxviii.raws_chunk_generation.engine;

import com.gmail.rawlxxxviii.raws_chunk_generation.util.ChunkStateTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class ChunkTicketManager {

    private static final TicketType<ChunkPos> PREGEN_TICKET = TicketType.create(
        "raws_chunk_generation:pregen",
        Comparator.comparingLong(ChunkPos::toLong),
        200
    );

    private static final Map<ResourceKey<Level>, Set<ChunkPos>> ACTIVE_TICKETS = new HashMap<>();

    public static int getTotalActiveTickets() {
        int count = 0;
        for (Set<ChunkPos> set : ACTIVE_TICKETS.values()) {
            count += set.size();
        }
        return count;
    }

    public static boolean isTicketActive(ResourceKey<Level> dim, ChunkPos pos) {
        Set<ChunkPos> set = ACTIVE_TICKETS.get(dim);
        return set != null && set.contains(pos);
    }

    public static void queueTicket(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Set<ChunkPos> set = ACTIVE_TICKETS.computeIfAbsent(dim, k -> new HashSet<>());
        if (set.add(pos)) {
            level.getChunkSource().addRegionTicket(PREGEN_TICKET, pos, 0, pos);
        }
    }

    public static void cleanupCompletedTickets(MinecraftServer server) {
        if (ACTIVE_TICKETS.isEmpty()) return;

        Iterator<Map.Entry<ResourceKey<Level>, Set<ChunkPos>>> dimIt = ACTIVE_TICKETS.entrySet().iterator();
        while (dimIt.hasNext()) {
            Map.Entry<ResourceKey<Level>, Set<ChunkPos>> entry = dimIt.next();
            ResourceKey<Level> dim = entry.getKey();
            Set<ChunkPos> tickets = entry.getValue();

            ServerLevel level = server.getLevel(dim);
            if (level == null) {
                dimIt.remove();
                continue;
            }

            Iterator<ChunkPos> posIt = tickets.iterator();
            while (posIt.hasNext()) {
                ChunkPos pos = posIt.next();
                if (ChunkStateTracker.isGenerated(dim, pos)) {
                    level.getChunkSource().removeRegionTicket(PREGEN_TICKET, pos, 0, pos);
                    posIt.remove();
                }
            }

            if (tickets.isEmpty()) {
                dimIt.remove();
            }
        }
    }

    public static void clearAllTickets(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Set<ChunkPos>> entry : ACTIVE_TICKETS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null) {
                for (ChunkPos pos : entry.getValue()) {
                    level.getChunkSource().removeRegionTicket(PREGEN_TICKET, pos, 0, pos);
                }
            }
        }
        ACTIVE_TICKETS.clear();
    }
}
