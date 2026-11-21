package com.gmail.rawlxxxviii.raws_chunk_generation.engine;

import com.gmail.rawlxxxviii.raws_chunk_generation.util.ChunkStateTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CircleChunkFinder {

    private record Offset(int dx, int dz, int distSq) {}

    private static final Map<Integer, List<Offset>> CIRCLE_OFFSET_CACHE = new ConcurrentHashMap<>();

    private static List<Offset> getSortedOffsets(int radius) {
        return CIRCLE_OFFSET_CACHE.computeIfAbsent(radius, r -> {
            int maxDistSq = r * r;
            List<Offset> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq <= maxDistSq) {
                        list.add(new Offset(dx, dz, distSq));
                    }
                }
            }
            list.sort(Comparator.comparingInt(Offset::distSq));
            return List.copyOf(list);
        });
    }

    public static @Nullable ChunkPos findNextChunkInCircle(ResourceKey<Level> dim, int centerX, int centerZ, int maxRadius) {
        if (maxRadius <= 0) return null;

        for (Offset offset : getSortedOffsets(maxRadius)) {
            int targetX = centerX + offset.dx;
            int targetZ = centerZ + offset.dz;

            if (ChunkStateTracker.isGenerated(dim, targetX, targetZ)) continue;

            ChunkPos candidate = new ChunkPos(targetX, targetZ);
            if (ChunkTicketManager.isTicketActive(dim, candidate)) continue;

            return candidate;
        }
        return null;
    }
}
