package com.gmail.rawlxxxviii.raws_chunk_generation.client.render;

import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.data.ActivitySavedData;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class ChunkMapRenderer {

    private static @Nullable SyncChunkAreaPacket latestPacket = null;
    private static @Nullable DynamicTexture mapTexture = null;
    private static @Nullable ResourceLocation mapTextureLocation = null;
    private static int texWidth = 0;
    private static int texHeight = 0;

    public static void updateData(SyncChunkAreaPacket packet) {
        latestPacket = packet;

        if (mapTexture == null || texWidth != packet.width || texHeight != packet.height) {
            if (mapTexture != null) {
                mapTexture.close();
            }
            texWidth = packet.width;
            texHeight = packet.height;
            mapTexture = new DynamicTexture(texWidth, texHeight, false);
            mapTextureLocation = Minecraft.getInstance().getTextureManager()
                .register("raws_chunk_map", mapTexture);
        }

        NativeImage pixels = mapTexture.getPixels();
        if (pixels != null) {
            int index = 0;
            for (int dz = 0; dz < packet.height; dz++) {
                for (int dx = 0; dx < packet.width; dx++) {
                    int byteIndex = index >> 2;
                    int state = 0;
                    if (byteIndex < packet.packedStates.length) {
                        state = (packet.packedStates[byteIndex] >> ((index & 3) << 1)) & 3;
                    }

                    int abgrColor = switch (state) {
                        case 1 -> 0xFF555555;
                        case 2 -> 0xFFFF8833;
                        default -> 0xFF000000;
                    };
                    pixels.setPixelRGBA(dx, dz, abgrColor);
                    index++;
                }
            }
            mapTexture.upload();
        }
    }

    public static @Nullable SyncChunkAreaPacket getLatestPacket() {
        return latestPacket;
    }

    public static void renderMap(GuiGraphics graphics, int minX, int minY, int mapSizePx, boolean showActivityAreas) {
        graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
        graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xCC000000);

        SyncChunkAreaPacket data = latestPacket;
        if (data == null || mapTextureLocation == null) return;

        float scale = (float) mapSizePx / data.width;
        graphics.blit(mapTextureLocation, minX, minY, mapSizePx, mapSizePx, 0, 0, data.width, data.height, data.width, data.height);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.level.dimension().equals(data.dimension)) {
            ChunkPos pPos = mc.player.chunkPosition();
            float playerRelX = pPos.x - data.startChunkX + 0.5f;
            float playerRelZ = pPos.z - data.startChunkZ + 0.5f;

            int playerPxX = minX + Math.round(playerRelX * scale);
            int playerPxY = minY + Math.round(playerRelZ * scale);

            if (playerPxX >= minX && playerPxX < minX + mapSizePx && playerPxY >= minY && playerPxY < minY + mapSizePx) {
                graphics.fill(playerPxX - 1, playerPxY - 1, playerPxX + 2, playerPxY + 2, 0xFFFFFFFF);
            }

            int bufferRadiusChunks = ServerConfig.INSTANCE.GENERATION_BUFFER_RADIUS.get();
            int radiusPx = Math.round(bufferRadiusChunks * scale);
            drawCircleOutline(graphics, playerPxX, playerPxY, radiusPx, minX, minY, mapSizePx, 0xFF00FF00);

            // Vierkant stippellijntje voor de actieve Render Distance van de speler
            int renderDistChunks = mc.options.getEffectiveRenderDistance();
            int leftPx   = minX + Math.round((pPos.x - renderDistChunks - data.startChunkX) * scale);
            int rightPx  = minX + Math.round((pPos.x + renderDistChunks + 1 - data.startChunkX) * scale);
            int topPx    = minY + Math.round((pPos.z - renderDistChunks - data.startChunkZ) * scale);
            int bottomPx = minY + Math.round((pPos.z + renderDistChunks + 1 - data.startChunkZ) * scale);

            drawDashedRect(graphics, leftPx, topPx, rightPx, bottomPx, minX, minY, mapSizePx, 0xAAFFFFFF);

        }

        if (showActivityAreas && !data.activityPoints.isEmpty()) {
            int offlineRadius = ServerConfig.INSTANCE.ACTIVATED_AREA_GENERATION_RADIUS.get();
            int offlineRadiusPx = Math.round(offlineRadius * scale);

            for (ActivitySavedData.RankedActivityPoint point : data.activityPoints) {
                int ptPxX = minX + Math.round((point.pos().x - data.startChunkX + 0.5f) * scale);
                int ptPxY = minY + Math.round((point.pos().z - data.startChunkZ + 0.5f) * scale);

                // Nieuwste punten (rank 0) zijn fel oranje, oudere punten (rank 7) zijn zachter transparant
                int alpha = point.completed() ? 0xCC : Math.max(0x44, 0xFF - (point.rank() * 24));
                int rgb = point.completed() ? 0x00AAFF : 0xFF9900;
                int color = (alpha << 24) | rgb;

                if (ptPxX >= minX && ptPxX < minX + mapSizePx && ptPxY >= minY && ptPxY < minY + mapSizePx) {
                    graphics.fill(ptPxX - 1, ptPxY - 1, ptPxX + 2, ptPxY + 2, color);
                }
                drawCircleOutline(graphics, ptPxX, ptPxY, offlineRadiusPx, minX, minY, mapSizePx, color);
            }
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int minX, int minY, int size, int color) {
        if (radius <= 0) return;
        int steps = Math.max(64, radius * 4);
        for (int i = 0; i < steps; i += 2) {
            double angle = (2.0 * Math.PI * i) / steps;
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            if (px >= minX && px < minX + size && py >= minY && py < minY + size) {
                graphics.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static void drawDashedRect(GuiGraphics graphics, int x1, int y1, int x2, int y2, int minX, int minY, int size, int color) {
        for (int x = x1; x <= x2; x += 3) {
            if (x >= minX && x < minX + size) {
                if (y1 >= minY && y1 < minY + size) graphics.fill(x, y1, Math.min(x + 2, minX + size), y1 + 1, color);
                if (y2 >= minY && y2 < minY + size) graphics.fill(x, y2, Math.min(x + 2, minX + size), y2 + 1, color);
            }
        }
        for (int y = y1; y <= y2; y += 3) {
            if (y >= minY && y < minY + size) {
                if (x1 >= minX && x1 < minX + size) graphics.fill(x1, y, x1 + 1, Math.min(y + 2, minY + size), color);
                if (x2 >= minX && x2 < minX + size) graphics.fill(x2, y, x2 + 1, Math.min(y + 2, minY + size), color);
            }
        }
    }

}
