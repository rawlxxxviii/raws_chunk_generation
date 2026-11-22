package com.gmail.rawlxxxviii.raws_chunk_generation.network.packet;

import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.data.ActivitySavedData;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.util.ChunkStateTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class RequestChunkAreaPacket {

    private final ResourceKey<Level> dimension;
    private final int startChunkX;
    private final int startChunkZ;
    private final int width;
    private final int height;

    public RequestChunkAreaPacket(ResourceKey<Level> dimension, int startChunkX, int startChunkZ, int width, int height) {
        this.dimension = dimension;
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
        this.width = width;
        this.height = height;
    }

    public static void encode(RequestChunkAreaPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceKey(msg.dimension);
        buf.writeInt(msg.startChunkX);
        buf.writeInt(msg.startChunkZ);
        buf.writeInt(msg.width);
        buf.writeInt(msg.height);
    }

    public static RequestChunkAreaPacket decode(FriendlyByteBuf buf) {
        return new RequestChunkAreaPacket(
            buf.readResourceKey(Registries.DIMENSION),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt()
        );
    }

    public static void handle(RequestChunkAreaPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            if (ServerConfig.INSTANCE.LIMIT_INFO_SCREEN_TO_OPERATORS.get() && !sender.hasPermissions(2)) {
                return;
            }

            if (!ServerConfig.isDimensionEnabled(msg.dimension)) {
                return;
            }

            int safeWidth = Math.min(Math.max(msg.width, 1), 512);
            int safeHeight = Math.min(Math.max(msg.height, 1), 512);

            ServerLevel targetLevel = sender.getServer().getLevel(msg.dimension);
            int generatedCount = ChunkStateTracker.getGeneratedCount(msg.dimension);
            boolean exists = targetLevel != null && generatedCount > 0;

            byte[] packedStates = exists
                ? ChunkStateTracker.getAreaStates(targetLevel, msg.startChunkX, msg.startChunkZ, safeWidth, safeHeight)
                : new byte[0];

            List<ActivitySavedData.RankedActivityPoint> activityPoints = ActivitySavedData.get(sender.getServer())
                .getPointsForDimension(msg.dimension);

            PacketHandler.sendToPlayer(
                new SyncChunkAreaPacket(msg.dimension, exists, msg.startChunkX, msg.startChunkZ, safeWidth, safeHeight, packedStates, activityPoints, generatedCount),
                sender
            );
        });
        ctx.setPacketHandled(true);
    }
}
