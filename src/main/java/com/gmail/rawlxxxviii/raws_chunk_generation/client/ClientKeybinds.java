package com.gmail.rawlxxxviii.raws_chunk_generation.client;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.network.ClientPacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.render.ChunkHudOverlay;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.screen.ChunkVisualiserScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientKeybinds {


    public static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping(
        "key.raws_chunk_generation.toggle_generation_hud",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F7,
        "key.categories.raws_chunk_generation"
    );

    public static final KeyMapping OPEN_GENERATION_SCREEN_KEY = new KeyMapping(
        "key.raws_chunk_generation.open_generation_screen",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F8,
        "key.categories.raws_chunk_generation"
    );


    @Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_HUD_KEY);
            event.register(OPEN_GENERATION_SCREEN_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;

            while (TOGGLE_HUD_KEY.consumeClick()) {
                if (ClientPacketHandler.canUseVisualiser()) {
                    ChunkHudOverlay.toggle();
                }
            }

            while (OPEN_GENERATION_SCREEN_KEY.consumeClick()) {
                if (ClientPacketHandler.canUseVisualiser()) {
                    mc.setScreen(new ChunkVisualiserScreen());
                }
            }
        }
    }

}
