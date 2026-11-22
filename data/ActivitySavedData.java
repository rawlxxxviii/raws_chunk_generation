package com.gmail.rawlxxxviii.raws_chunk_generation.data;

import com.gmail.rawlxxxviii.raws_chunk_generation.engine.CircleChunkFinder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ActivitySavedData extends SavedData {

    public record ActivityPoint(ResourceKey<Level> dimension, ChunkPos pos, boolean completed) {}
    public record RankedActivityPoint(ChunkPos pos, int rank, boolean completed) {}

    private static final String DATA_NAME = "raws_chunk_generation_activity";
    private static final int MAX_POINTS_PER_PLAYER = 8;
    private static final int MIN_DISTANCE_CHUNKS = 16;

    // Speler UUID -> geordende lijst van max 8 ActivityPoints over alle dimensies samen (nieuwste op index 0)
    private final Map<UUID, List<ActivityPoint>> activityMap = new HashMap<>();

    public static ActivitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            ActivitySavedData::load,
            ActivitySavedData::new,
            DATA_NAME
        );
    }

    /**
     * Registreert een activiteitspunt voor een speler (pure FIFO-lijst van max 8 totaal, nieuwste vooraan).
     */
    public void recordActivity(ResourceKey<Level> dim, UUID playerUuid, ChunkPos pos) {
        List<ActivityPoint> points = activityMap.computeIfAbsent(playerUuid, k -> new ArrayList<>());

        points.add(0, new ActivityPoint(dim, pos, false));
        while (points.size() > MAX_POINTS_PER_PLAYER) {
            points.remove(points.size() - 1);
        }

        this.setDirty();
    }


    /**
     * Geeft alle activiteitspunten (inclusief hun leeftijd-rank 0..7) terug voor een specifieke dimensie.
     */
    public List<RankedActivityPoint> getPointsForDimension(ResourceKey<Level> dim) {
        if (activityMap.isEmpty()) return List.of();

        List<RankedActivityPoint> result = new ArrayList<>();
        for (List<ActivityPoint> playerPoints : activityMap.values()) {
            for (int rank = 0; rank < playerPoints.size(); rank++) {
                ActivityPoint point = playerPoints.get(rank);
                if (point.dimension.equals(dim)) {
                    result.add(new RankedActivityPoint(point.pos, rank, point.completed));
                }
            }
        }
        return result;
    }

    /**
     * Kiest binnen een dimensie de volgende chunk op basis van onze gewogen score:
     * - Afstand: 1.00 (midden) -> 0.01 (buitenrand)
     * - Leeftijd (rank 0..7): 1.00 (nieuwste) -> 0.10 (oudste)
     */
    public @Nullable ChunkPos findBestOfflineChunkInDimension(ResourceKey<Level> dim, int maxRadius) {
        if (maxRadius <= 0 || activityMap.isEmpty()) return null;

        ChunkPos bestChunk = null;
        double bestScore = -1.0;

        for (List<ActivityPoint> playerPoints : activityMap.values()) {
            for (int rank = 0; rank < playerPoints.size(); rank++) {
                ActivityPoint point = playerPoints.get(rank);
                if (point.completed || !point.dimension.equals(dim)) continue;

                ChunkPos candidate = CircleChunkFinder.findNextChunkInCircle(dim, point.pos.x, point.pos.z, maxRadius);
                if (candidate == null) {
                    // Cirkel is 100% vol! Markeer direct als completed zodat hij niet meer gecheckt wordt
                    playerPoints.set(rank, new ActivityPoint(point.dimension, point.pos, true));
                    this.setDirty();
                    continue;
                }

                double dist = Math.hypot(candidate.x - point.pos.x, candidate.z - point.pos.z);
                double normalizedDist = Math.min(1.0, dist / maxRadius);
                double distFactor = 1.0 - (0.99 * normalizedDist); // 1.00 (midden) -> 0.01 (rand)

                double ageFactor = 1.0 - (0.90 * (rank / (double) (MAX_POINTS_PER_PLAYER - 1))); // 1.00 -> 0.10
                double score = distFactor * ageFactor;

                if (score > bestScore) {
                    bestScore = score;
                    bestChunk = candidate;
                }
            }
        }

        return bestChunk;
    }

    /**
     * Geeft alle dimensies terug die momenteel nog onvoltooide activiteitspunten hebben.
     */
    public Set<ResourceKey<Level>> getDimensionsWithPendingWork() {
        Set<ResourceKey<Level>> dims = new LinkedHashSet<>();
        for (List<ActivityPoint> playerPoints : activityMap.values()) {
            for (ActivityPoint point : playerPoints) {
                if (!point.completed) {
                    dims.add(point.dimension);
                }
            }
        }
        return dims;
    }

    // --- NBT OPSLAG ---

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag playersList = new ListTag();

        for (Map.Entry<UUID, List<ActivityPoint>> entry : activityMap.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());

            ListTag pointsList = new ListTag();
            for (ActivityPoint point : entry.getValue()) {
                CompoundTag pTag = new CompoundTag();
                pTag.putString("Dimension", point.dimension.location().toString());
                pTag.putLong("Pos", point.pos.toLong());
                pTag.putBoolean("Completed", point.completed);
                pointsList.add(pTag);
            }
            playerTag.put("Points", pointsList);
            playersList.add(playerTag);
        }

        root.put("Players", playersList);
        return root;
    }

    public static ActivitySavedData load(CompoundTag root) {
        ActivitySavedData data = new ActivitySavedData();
        ListTag playersList = root.getList("Players", Tag.TAG_COMPOUND);

        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerTag = playersList.getCompound(i);
            if (!playerTag.hasUUID("Player")) continue;

            UUID uuid = playerTag.getUUID("Player");
            ListTag pointsList = playerTag.getList("Points", Tag.TAG_COMPOUND);

            List<ActivityPoint> points = new ArrayList<>();
            for (int j = 0; j < Math.min(pointsList.size(), MAX_POINTS_PER_PLAYER); j++) {
                CompoundTag pTag = pointsList.getCompound(j);
                ResourceLocation dimLoc = ResourceLocation.tryParse(pTag.getString("Dimension"));
                if (dimLoc == null) continue;

                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                points.add(new ActivityPoint(
                    dimKey,
                    new ChunkPos(pTag.getLong("Pos")),
                    pTag.getBoolean("Completed")
                ));
            }

            if (!points.isEmpty()) {
                data.activityMap.put(uuid, points);
            }
        }
        return data;
    }
}
