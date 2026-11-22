package com.gmail.rawlxxxviii.raws_chunk_generation.client.render;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.network.ClientPacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.screen.ChunkVisualiserScreen;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChunkHudOverlay {

    private static final int HUD_VIEW_CHUNKS = 192;
    private static final int HUD_SIZE_PX = 140;
    private static final int POLL_INTERVAL_TICKS = 5;

    private static boolean enabled = false;
    private static int tickCounter = 0;

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            requestHudUpdate();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen instanceof ChunkVisualiserScreen) return;
        if (!ClientPacketHandler.canUseVisualiser()) return;

        if (++tickCounter % POLL_INTERVAL_TICKS == 0) {
            requestHudUpdate();
        }
    }

    private static void requestHudUpdate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ChunkPos pPos = mc.player.chunkPosition();
        int halfView = HUD_VIEW_CHUNKS / 2;

        PacketHandler.sendToServer(new RequestChunkAreaPacket(
            mc.level.dimension(),
            pPos.x - halfView,
            pPos.z - halfView,
            HUD_VIEW_CHUNKS,
            HUD_VIEW_CHUNKS
        ));
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!enabled || event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen instanceof ChunkVisualiserScreen) return;
        if (!ClientPacketHandler.canUseVisualiser()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int minX = screenWidth - HUD_SIZE_PX - 10;
        int minY = 10;

        ChunkMapRenderer.renderMap(graphics, minX, minY, HUD_SIZE_PX, true);
    }
}
