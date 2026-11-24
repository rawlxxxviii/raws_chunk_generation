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
    private static final int TICKET_RADIUS = 1;
    private static final long TICKET_TIMEOUT_TICKS = 200L;

    private static final TicketType<ChunkPos> PREGEN_TICKET = TicketType.create(
        "raws_chunk_generation:pregen",
        Comparator.comparingLong(ChunkPos::toLong),
        (int) TICKET_TIMEOUT_TICKS
    );

    private static final Map<ResourceKey<Level>, Map<ChunkPos, Long>> ACTIVE_TICKETS = new HashMap<>();

    public static int getTotalActiveTickets() {
        int count = 0;
        for (Map<ChunkPos, Long> map : ACTIVE_TICKETS.values()) {
            count += map.size();
        }
        return count;
    }

    public static boolean isTicketActive(ResourceKey<Level> dim, ChunkPos pos) {
        Map<ChunkPos, Long> map = ACTIVE_TICKETS.get(dim);
        return map != null && map.containsKey(pos);
    }

    public static void queueTicket(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<ChunkPos, Long> map = ACTIVE_TICKETS.computeIfAbsent(dim, k -> new HashMap<>());
        if (map.putIfAbsent(pos, level.getGameTime()) == null) {
            level.getChunkSource().addRegionTicket(PREGEN_TICKET, pos, TICKET_RADIUS, pos);
        }
    }

    public static void cleanupCompletedTickets(MinecraftServer server) {
        if (ACTIVE_TICKETS.isEmpty()) return;

        Iterator<Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>>> dimIt = ACTIVE_TICKETS.entrySet().iterator();
        while (dimIt.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>> entry = dimIt.next();
            ResourceKey<Level> dim = entry.getKey();
            Map<ChunkPos, Long> tickets = entry.getValue();

            ServerLevel level = server.getLevel(dim);
            if (level == null) {
                dimIt.remove();
                continue;
            }

            long now = level.getGameTime();
            Iterator<Map.Entry<ChunkPos, Long>> posIt = tickets.entrySet().iterator();
            while (posIt.hasNext()) {
                Map.Entry<ChunkPos, Long> ticketEntry = posIt.next();
                ChunkPos pos = ticketEntry.getKey();
                boolean expired = (now - ticketEntry.getValue()) >= TICKET_TIMEOUT_TICKS;

                if (ChunkStateTracker.isGenerated(dim, pos) || expired) {
                    level.getChunkSource().removeRegionTicket(PREGEN_TICKET, pos, TICKET_RADIUS, pos);
                    posIt.remove();
                }
            }

            if (tickets.isEmpty()) {
                dimIt.remove();
            }
        }
    }

    public static void clearAllTickets(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<ChunkPos, Long>> entry : ACTIVE_TICKETS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null) {
                for (ChunkPos pos : entry.getValue().keySet()) {
                    level.getChunkSource().removeRegionTicket(PREGEN_TICKET, pos, TICKET_RADIUS, pos);
                }
            }
        }
        ACTIVE_TICKETS.clear();
    }
}
