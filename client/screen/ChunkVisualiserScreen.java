package com.gmail.rawlxxxviii.raws_chunk_generation.client.screen;

import com.gmail.rawlxxxviii.raws_chunk_generation.client.ClientKeybinds;
import com.gmail.rawlxxxviii.raws_chunk_generation.client.render.ChunkMapRenderer;
import com.gmail.rawlxxxviii.raws_chunk_generation.config.ServerConfig;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.PacketHandler;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.RequestChunkAreaPacket;
import com.gmail.rawlxxxviii.raws_chunk_generation.network.packet.SyncChunkAreaPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class ChunkVisualiserScreen extends Screen {

    private static final int VIEW_CHUNKS = 512;
    private static final int POLL_INTERVAL_TICKS = 5;

    private record LegendItem(int color, String labelKey, String tooltipKey) {}

    private static final List<LegendItem> LEGEND_ITEMS = List.of(
        new LegendItem(ChunkMapRenderer.GENERATED_COLOR,          "legend.raws_chunk_generation.generated",     "legend.raws_chunk_generation.generated.desc"),
        new LegendItem(ChunkMapRenderer.QUEUED_COLOR,             "legend.raws_chunk_generation.queued",        "legend.raws_chunk_generation.queued.desc"),
        new LegendItem(ChunkMapRenderer.PLAYER_COLOR,             "legend.raws_chunk_generation.player",        "legend.raws_chunk_generation.player.desc"),
        new LegendItem(ChunkMapRenderer.ONLINE_BUFFER_COLOR,      "legend.raws_chunk_generation.online_buffer", "legend.raws_chunk_generation.online_buffer.desc"),
        new LegendItem(ChunkMapRenderer.ACTIVITY_AREA_COLOR,      "legend.raws_chunk_generation.activity_area", "legend.raws_chunk_generation.activity_area.desc"),
        new LegendItem(ChunkMapRenderer.ACTIVITY_AREA_DONE_COLOR, "legend.raws_chunk_generation.activity_done", "legend.raws_chunk_generation.activity_done.desc")
    );

    private @Nullable ResourceKey<Level> selectedDimension = null;
    private int panOffsetX = 0;
    private int panOffsetZ = 0;
    private int tickCounter = 0;

    public ChunkVisualiserScreen() {
        super(Component.translatable("screen.raws_chunk_generation.generation_screen_title"));
    }

    @Override
    protected void init() {
        super.init();

        List<ResourceKey<Level>> enabledDims = getEnabledDimensions();
        Minecraft mc = Minecraft.getInstance();

        if (selectedDimension == null && mc.level != null) {
            ResourceKey<Level> currentDim = mc.level.dimension();
            if (ServerConfig.isDimensionEnabled(currentDim)) {
                this.selectedDimension = currentDim;
            } else {
                this.selectedDimension = enabledDims.isEmpty() ? currentDim : enabledDims.get(0);
            }
        }

        int mapSizePx = getMapSizePx();
        int minX = (this.width - mapSizePx) / 2;
        int minY = (this.height - mapSizePx) / 2 - 8;

        int btnWidth = 96;
        int btnHeight = 20;
        int btnX = Math.min(minX + mapSizePx + 8, this.width - btnWidth - 8);

        Button nextDimButton = Button.builder(
                Component.translatable("button.raws_chunk_generation.next_dimension"),
                btn -> cycleNextDimension()
            )
            .bounds(btnX, minY, btnWidth, btnHeight)
            .build();

        nextDimButton.active = enabledDims.size() > 1;
        this.addRenderableWidget(nextDimButton);

        requestAreaUpdate();
    }

    private void cycleNextDimension() {
        List<ResourceKey<Level>> enabledDims = getEnabledDimensions();
        if (enabledDims.size() <= 1 || selectedDimension == null) return;

        int idx = enabledDims.indexOf(selectedDimension);
        int nextIdx = Math.floorMod(idx + 1, enabledDims.size());
        selectedDimension = enabledDims.get(nextIdx);
        panOffsetX = 0;
        panOffsetZ = 0;
        requestAreaUpdate();
    }

    private List<ResourceKey<Level>> getEnabledDimensions() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();

        return connection.levels().stream()
            .filter(ServerConfig::isDimensionEnabled)
            .sorted(Comparator
                .comparingInt((ResourceKey<Level> k) -> {
                    if (k.equals(Level.OVERWORLD)) return 0;
                    if (k.equals(Level.NETHER)) return 1;
                    if (k.equals(Level.END)) return 2;
                    return 3;
                })
                .thenComparing(k -> k.location().toString())
            )
            .toList();
    }

    private int getMapSizePx() {
        return Math.min(VIEW_CHUNKS, Math.min(this.width - 40, this.height - 60));
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % POLL_INTERVAL_TICKS == 0) {
            requestAreaUpdate();
        }
    }

    private void requestAreaUpdate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || selectedDimension == null) return;

        boolean isCurrentPlayerDim = mc.level.dimension().equals(selectedDimension);
        int baseX = isCurrentPlayerDim ? mc.player.chunkPosition().x : 0;
        int baseZ = isCurrentPlayerDim ? mc.player.chunkPosition().z : 0;
        int halfView = VIEW_CHUNKS / 2;

        PacketHandler.sendToServer(new RequestChunkAreaPacket(
            selectedDimension,
            baseX + panOffsetX - halfView,
            baseZ + panOffsetZ - halfView,
            VIEW_CHUNKS,
            VIEW_CHUNKS
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int mapSizePx = getMapSizePx();
        int minX = (this.width - mapSizePx) / 2;
        int minY = (this.height - mapSizePx) / 2 - 8;

        SyncChunkAreaPacket data = ChunkMapRenderer.getLatestPacket();

        if (data != null && data.dimension.equals(selectedDimension)) {
            if (data.dimensionExists) {
                ChunkMapRenderer.renderMap(graphics, minX, minY, mapSizePx, true);
            } else {
                graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
                graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xFF000000);
                graphics.drawCenteredString(
                    this.font,
                    Component.translatable("screen.raws_chunk_generation.dimension_non_existing_message", data.dimension.location().toString()),
                    this.width / 2,
                    minY + (mapSizePx / 2),
                    0xFF3366AA
                );
            }

            Component dimensionTitle = Component.translatable(data.dimension.location().toLanguageKey("dimension"));
            graphics.drawCenteredString(this.font, dimensionTitle, this.width / 2, minY + 12, ChunkMapRenderer.PLAYER_COLOR);
            graphics.drawCenteredString(this.font, data.debugInfo, this.width / 2, minY + mapSizePx + 8, 0xFFCCCCCC);
        } else {
            graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
            graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xFF000000);
            graphics.drawCenteredString(this.font, "Loading chunk data...", this.width / 2, minY + (mapSizePx / 2), 0xFFAAAAAA);
        }

        renderLegend(graphics, minX, minY, mapSizePx, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLegend(GuiGraphics graphics, int minX, int minY, int mapSizePx, int mouseX, int mouseY) {
        int lineHeight = 12;
        int startY = (minY + mapSizePx) - (LEGEND_ITEMS.size() * lineHeight);
        int startX = minX >= 115 ? minX - 108 : 8;

        for (int i = 0; i < LEGEND_ITEMS.size(); i++) {
            LegendItem item = LEGEND_ITEMS.get(i);
            int y = startY + (i * lineHeight);

            graphics.fill(startX - 1, y + 1, startX + 7, y + 9, 0xFF888888);
            graphics.fill(startX, y + 2, startX + 6, y + 8, item.color);

            Component label = Component.translatable(item.labelKey);
            graphics.drawString(this.font, label, startX + 10, y + 1, 0xFFDDDDDD);

            int hoverWidth = 10 + this.font.width(label);
            if (mouseX >= startX - 2 && mouseX <= startX + hoverWidth + 2 && mouseY >= y && mouseY < y + lineHeight) {
                graphics.renderTooltip(
                    this.font,
                    Component.translatable(item.tooltipKey),
                    mouseX,
                    mouseY
                );
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientKeybinds.OPEN_GENERATION_SCREEN_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        int panAmount = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 64 : 16;
        boolean panned = false;

        if (keyCode == GLFW.GLFW_KEY_UP) {
            panOffsetZ -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            panOffsetZ += panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            panOffsetX -= panAmount;
            panned = true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            panOffsetX += panAmount;
            panned = true;
        }
        if (panned) {
            requestAreaUpdate();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            float scale = (float) getMapSizePx() / VIEW_CHUNKS;
            panOffsetX -= (int) Math.round(dragX / scale);
            panOffsetZ -= (int) Math.round(dragY / scale);
            requestAreaUpdate();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}