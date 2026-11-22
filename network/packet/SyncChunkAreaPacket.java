package com.gmail.rawlxxxviii.raws_chunk_generation.network.packet;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.network.ClientPacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.data.ActivitySavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class SyncChunkAreaPacket {

    public final ResourceKey<Level> dimension;
    public final boolean dimensionExists;
    public final int startChunkX;
    public final int startChunkZ;
    public final int width;
    public final int height;
    public final byte[] packedStates;
    public final List<ActivitySavedData.RankedActivityPoint> activityPoints;
    public final String debugInfo;

    public SyncChunkAreaPacket(
        ResourceKey<Level> dimension,
        boolean dimensionExists,
        int startChunkX,
        int startChunkZ,
        int width,
        int height,
        byte[] packedStates,
        List<ActivitySavedData.RankedActivityPoint> activityPoints,
        String debugInfo
    ) {
        this.dimension = dimension;
        this.dimensionExists = dimensionExists;
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
        this.width = width;
        this.height = height;
        this.packedStates = packedStates;
        this.activityPoints = activityPoints;
        this.debugInfo = debugInfo;
    }

    public static void encode(SyncChunkAreaPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceKey(msg.dimension);
        buf.writeBoolean(msg.dimensionExists);
        buf.writeInt(msg.startChunkX);
        buf.writeInt(msg.startChunkZ);
        buf.writeInt(msg.width);
        buf.writeInt(msg.height);
        buf.writeByteArray(msg.packedStates);
        buf.writeCollection(msg.activityPoints, (b, p) -> {
            b.writeChunkPos(p.pos());
            b.writeByte(p.rank());
            b.writeBoolean(p.completed());
        });
        buf.writeUtf(msg.debugInfo);
    }

    public static SyncChunkAreaPacket decode(FriendlyByteBuf buf) {
        return new SyncChunkAreaPacket(
            buf.readResourceKey(Registries.DIMENSION),
            buf.readBoolean(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readByteArray(),
            buf.readList(b -> new ActivitySavedData.RankedActivityPoint(
                b.readChunkPos(),
                b.readByte(),
                b.readBoolean()
            )),
            buf.readUtf()
        );
    }

    public static void handle(SyncChunkAreaPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncChunkArea(msg));
        });
        ctx.setPacketHandled(true);
    }
}
