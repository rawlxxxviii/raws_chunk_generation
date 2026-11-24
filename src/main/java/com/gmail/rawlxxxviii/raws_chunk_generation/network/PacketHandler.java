package com.gmail.rawlxxxviii.raws_chunk_generation.network;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder.named(
            new ResourceLocation(RawsChunkGenerationMod.MODID, "main"))
            .serverAcceptedVersions((version) -> true)
            .clientAcceptedVersions((version) -> true)
            .networkProtocolVersion(() -> "1")
            .simpleChannel();

    public static void register() {
        INSTANCE.messageBuilder(RequestChunkAreaPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestChunkAreaPacket::encode)
            .decoder(RequestChunkAreaPacket::decode)
            .consumerMainThread(RequestChunkAreaPacket::handle)
            .add();

        INSTANCE.messageBuilder(SyncChunkAreaPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncChunkAreaPacket::encode)
            .decoder(SyncChunkAreaPacket::decode)
            .consumerMainThread(SyncChunkAreaPacket::handle)
            .add();
    }

    public static <T> void sendToServer(T msg) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), msg);
    }

    public static <T> void sendToPlayer(T msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static <T> void sendToClients(T msg) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), msg);
    }


}
