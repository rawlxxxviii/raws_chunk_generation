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

import java.awt.Color;

public class ChunkMapRenderer {

    public static final int GENERATED_COLOR          = new Color(0x555555).getRGB();
    public static final int QUEUED_COLOR             = new Color(0x3388FF).getRGB();
    public static final int PLAYER_COLOR             = new Color(0xFFFFFF).getRGB();
    public static final int ONLINE_BUFFER_COLOR      = new Color(0x00FF00).getRGB();
    public static final int ACTIVITY_AREA_COLOR      = new Color(0xFF9900).getRGB();
    public static final int ACTIVITY_AREA_DONE_COLOR = new Color(0x27814B).getRGB();

    private static @Nullable SyncChunkAreaPacket latestPacket = null;
    private static @Nullable DynamicTexture mapTexture = null;
    private static @Nullable ResourceLocation mapTextureLocation = null;
    private static int texWidth = 0;
    private static int texHeight = 0;

    private static int toAbgr(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    public static void updateData(SyncChunkAreaPacket packet) {
        latestPacket = packet;
        if (!packet.dimensionExists || packet.width <= 0 || packet.height <= 0) return;

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
            int abgrGenerated = toAbgr(GENERATED_COLOR);
            int abgrQueued = toAbgr(QUEUED_COLOR);

            int index = 0;
            for (int dz = 0; dz < packet.height; dz++) {
                for (int dx = 0; dx < packet.width; dx++) {
                    int byteIndex = index >> 2;
                    int state = 0;
                    if (byteIndex < packet.packedStates.length) {
                        state = (packet.packedStates[byteIndex] >> ((index & 3) << 1)) & 3;
                    }

                    int abgrColor = switch (state) {
                        case 1 -> abgrGenerated;
                        case 2 -> abgrQueued;
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
        SyncChunkAreaPacket data = latestPacket;
        if (data == null || !data.dimensionExists || mapTextureLocation == null) return;

        graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
        graphics.blit(mapTextureLocation, minX, minY, 0, 0, mapSizePx, mapSizePx, mapSizePx, mapSizePx);

        Minecraft mc = Minecraft.getInstance();
        float scale = (float) mapSizePx / data.width;

        if (showActivityAreas && !data.activityPoints.isEmpty()) {
            int offlineRadius = ServerConfig.INSTANCE.ACTIVATED_AREA_GENERATION_RADIUS.get();
            int offlineRadiusPx = Math.round(offlineRadius * scale);

            for (ActivitySavedData.RankedActivityPoint point : data.activityPoints) {
                int ptPxX = minX + Math.round((point.pos().x - data.startChunkX + 0.5f) * scale);
                int ptPxY = minY + Math.round((point.pos().z - data.startChunkZ + 0.5f) * scale);

                int alpha = point.completed() ? 0xCC : Math.max(0x44, 0xFF - (point.rank() * 24));
                int baseRgb = (point.completed() ? ACTIVITY_AREA_DONE_COLOR : ACTIVITY_AREA_COLOR) & 0x00FFFFFF;
                int color = (alpha << 24) | baseRgb;

                if (ptPxX >= minX && ptPxX < minX + mapSizePx && ptPxY >= minY && ptPxY < minY + mapSizePx) {
                    graphics.fill(ptPxX - 1, ptPxY - 1, ptPxX + 2, ptPxY + 2, color);
                }
                drawCircleOutline(graphics, ptPxX, ptPxY, offlineRadiusPx, minX, minY, mapSizePx, color);
            }
        }

        if (mc.player != null && mc.level != null && mc.level.dimension().equals(data.dimension)) {
            ChunkPos pPos = mc.player.chunkPosition();
            int pPxX = minX + Math.round((pPos.x - data.startChunkX + 0.5f) * scale);
            int pPxY = minY + Math.round((pPos.z - data.startChunkZ + 0.5f) * scale);

            if (pPxX >= minX && pPxX < minX + mapSizePx && pPxY >= minY && pPxY < minY + mapSizePx) {
                graphics.fill(pPxX - 1, pPxY - 1, pPxX + 2, pPxY + 2, PLAYER_COLOR);
            }

            int renderDistChunks = mc.options.getEffectiveRenderDistance();
            int leftPx   = minX + Math.round((pPos.x - renderDistChunks - data.startChunkX) * scale);
            int rightPx  = minX + Math.round((pPos.x + renderDistChunks + 1 - data.startChunkX) * scale);
            int topPx    = minY + Math.round((pPos.z - renderDistChunks - data.startChunkZ) * scale);
            int bottomPx = minY + Math.round((pPos.z + renderDistChunks + 1 - data.startChunkZ) * scale);
            drawDashedRect(graphics, leftPx, topPx, rightPx, bottomPx, minX, minY, mapSizePx, 0xAAFFFFFF);

            int bufferRadius = ServerConfig.INSTANCE.GENERATION_BUFFER_RADIUS.get();
            if (bufferRadius > 0) {
                int bufferRadiusPx = Math.round(bufferRadius * scale);
                drawCircleOutline(graphics, pPxX, pPxY, bufferRadiusPx, minX, minY, mapSizePx, ONLINE_BUFFER_COLOR);
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

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radiusPx, int minX, int minY, int size, int color) {
        if (radiusPx <= 0) return;
        int steps = Math.max(60, radiusPx * 2);
        for (int i = 0; i < steps; i++) {
            if (i % 2 == 1) continue;
            double angle = (2.0 * Math.PI * i) / steps;
            int px = cx + (int) Math.round(Math.cos(angle) * radiusPx);
            int py = cy + (int) Math.round(Math.sin(angle) * radiusPx);
            if (px >= minX && px < minX + size && py >= minY && py < minY + size) {
                graphics.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    public static void clear() {
        latestPacket = null;
    }
}
