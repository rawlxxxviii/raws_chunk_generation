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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ChunkVisualiserScreen extends Screen {

    private static final int VIEW_CHUNKS = 512;
    private static final int POLL_INTERVAL_TICKS = 5;

    private boolean isDraggingMap = false;

    private EditBox xInput;
    private EditBox zInput;

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

    private @Nullable ResourceKey<Level> lastCpsDim = null;
    private int lastGeneratedCount = -1;
    private long lastCpsSampleMs = 0L;
    private double smoothedCps = 0.0;

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

        int halfFieldWidth = (btnWidth - 4) / 2;
        int inputY = minY + btnHeight + 6;

        addCoordinatesInput(btnX, inputY, halfFieldWidth, btnWidth);


        requestAreaUpdate();
    }

    private void addCoordinatesInput(int btnX, int inputY, int halfFieldWidth, int btnWidth) {
        xInput = new EditBox(this.font, btnX, inputY, halfFieldWidth, 18, Component.literal("X"));
        xInput.setHint(Component.literal("X"));
        xInput.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(xInput);

        zInput = new EditBox(this.font, btnX + halfFieldWidth + 4, inputY, halfFieldWidth, 18, Component.literal("Z"));
        zInput.setHint(Component.literal("Z"));
        zInput.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(zInput);

        Button goButton = Button.builder(
                Component.translatable("button.raws_chunk_generation.go"),
                btn -> jumpToCoordinates()
            )
            .bounds(btnX, inputY + 22, btnWidth, 18)
            .build();
        this.addRenderableWidget(goButton);

        syncCoordInputs();
    }

    private void jumpToCoordinates() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || selectedDimension == null) return;

        try {
            int targetBlockX = xInput.getValue().isEmpty() ? 0 : Integer.parseInt(xInput.getValue());
            int targetBlockZ = zInput.getValue().isEmpty() ? 0 : Integer.parseInt(zInput.getValue());

            int targetChunkX = targetBlockX >> 4;
            int targetChunkZ = targetBlockZ >> 4;

            boolean isCurrentPlayerDim = mc.level.dimension().equals(selectedDimension);
            int baseX = isCurrentPlayerDim ? mc.player.chunkPosition().x : 0;
            int baseZ = isCurrentPlayerDim ? mc.player.chunkPosition().z : 0;

            panOffsetX = targetChunkX - baseX;
            panOffsetZ = targetChunkZ - baseZ;

            xInput.setFocused(false);
            zInput.setFocused(false);
            requestAreaUpdate();
        } catch (NumberFormatException ignored) {}
    }

    private void syncCoordInputs() {
        if (xInput == null || zInput == null || xInput.isFocused() || zInput.isFocused()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isCurrentPlayerDim = mc.player != null && mc.level != null && mc.level.dimension().equals(selectedDimension);
        int centerChunkX = (isCurrentPlayerDim ? mc.player.chunkPosition().x : 0) + panOffsetX;
        int centerChunkZ = (isCurrentPlayerDim ? mc.player.chunkPosition().z : 0) + panOffsetZ;

        xInput.setValue(String.valueOf(centerChunkX << 4));
        zInput.setValue(String.valueOf(centerChunkZ << 4));
    }

    private void cycleNextDimension() {
        List<ResourceKey<Level>> enabledDims = getEnabledDimensions();
        if (enabledDims.size() <= 1 || selectedDimension == null) return;

        int idx = enabledDims.indexOf(selectedDimension);
        int nextIdx = Math.floorMod(idx + 1, enabledDims.size());
        selectedDimension = enabledDims.get(nextIdx);
        panOffsetX = 0;
        panOffsetZ = 0;
        syncCoordInputs();
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
            updateCps(data);

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
                    0xFF888888
                );
            }

            Component dimensionTitle = Component.translatable(data.dimension.location().toLanguageKey("dimension"));
            graphics.drawCenteredString(this.font, dimensionTitle, this.width / 2, minY + 12, ChunkMapRenderer.PLAYER_COLOR);

            String formattedSpeed = String.format(Locale.ROOT, "%.1f", smoothedCps);
            Component footerText = Component.translatable(
                "screen.raws_chunk_generation.footer_stats",
                data.totalGeneratedChunks,
                formattedSpeed
            );
            graphics.drawCenteredString(this.font, footerText, this.width / 2, minY + mapSizePx + 8, 0xFFCCCCCC);
            renderHoverCoordinates(graphics, data, minX, minY, mapSizePx, mouseX, mouseY);
        } else {
            graphics.fill(minX - 1, minY - 1, minX + mapSizePx + 1, minY + mapSizePx + 1, 0xFF888888);
            graphics.fill(minX, minY, minX + mapSizePx, minY + mapSizePx, 0xFF000000);
            graphics.drawCenteredString(this.font, "Loading chunk data...", this.width / 2, minY + (mapSizePx / 2), 0xFFAAAAAA);
        }

        renderLegend(graphics, minX, minY, mapSizePx, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLegend(GuiGraphics graphics, int minX, int minY, int mapSizePx, int mouseX, int mouseY) {
        float scale = 0.7f;
        int lineHeight = 9;
        int padding = 6;

        int startX = minX + padding;
        int startY = (minY + mapSizePx) - padding - (LEGEND_ITEMS.size() * lineHeight);

        for (int i = 0; i < LEGEND_ITEMS.size(); i++) {
            LegendItem item = LEGEND_ITEMS.get(i);
            int y = startY + (i * lineHeight);

            graphics.pose().pushPose();
            graphics.pose().translate(startX, y, 0);
            graphics.pose().scale(scale, scale, 1.0f);

            graphics.fill(0, 1, 8, 9, 0xFF888888);
            graphics.fill(1, 2, 7, 8, item.color);

            Component label = Component.translatable(item.labelKey);
            graphics.drawString(this.font, label, 11, 1, 0xFFDDDDDD);

            graphics.pose().popPose();

            int scaledWidth = Math.round((11 + this.font.width(label)) * scale);
            if (mouseX >= startX - 2 && mouseX <= startX + scaledWidth + 2 && mouseY >= y && mouseY < y + lineHeight) {
                graphics.renderTooltip(
                    this.font,
                    Component.translatable(item.tooltipKey),
                    mouseX,
                    mouseY
                );
            }
        }
    }

    private void renderHoverCoordinates(GuiGraphics graphics, SyncChunkAreaPacket data, int minX, int minY, int mapSizePx, int mouseX, int mouseY) {
        if (!data.dimensionExists) return;
        if (mouseX < minX || mouseX >= minX + mapSizePx || mouseY < minY || mouseY >= minY + mapSizePx) {
            return;
        }

        float scale = (float) mapSizePx / data.width;
        int hoveredChunkX = data.startChunkX + (int) Math.floor((mouseX - minX) / scale);
        int hoveredChunkZ = data.startChunkZ + (int) Math.floor((mouseY - minY) / scale);

        int blockX = hoveredChunkX << 4;
        int blockZ = hoveredChunkZ << 4;

        String text = String.format("X: %d, Z: %d", blockX, blockZ);
        float textScale = 0.7f;
        int padding = 6;

        int scaledWidth = Math.round(this.font.width(text) * textScale);
        int scaledHeight = Math.round(this.font.lineHeight * textScale);

        int drawX = (minX + mapSizePx) - padding - scaledWidth;
        int drawY = (minY + mapSizePx) - padding - scaledHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(textScale, textScale, 1.0f);
        graphics.drawString(this.font, text, 0, 0, 0xFFDDDDDD);
        graphics.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((xInput != null && xInput.isFocused()) || (zInput != null && zInput.isFocused())) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                jumpToCoordinates();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (ClientKeybinds.OPEN_GENERATION_SCREEN_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        int panAmount = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 256 : 128;
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
            syncCoordInputs();
            requestAreaUpdate();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateCps(SyncChunkAreaPacket data) {
        int currentTotal = data.totalGeneratedChunks;
        long now = System.currentTimeMillis();

        if (lastCpsDim == null || !lastCpsDim.equals(data.dimension) || lastGeneratedCount < 0) {
            lastCpsDim = data.dimension;
            lastGeneratedCount = currentTotal;
            lastCpsSampleMs = now;
            smoothedCps = 0.0;
        } else {
            long elapsedMs = now - lastCpsSampleMs;
            if (elapsedMs >= 1000L) {
                int delta = Math.max(0, currentTotal - lastGeneratedCount);
                double instantCps = (delta * 1000.0) / elapsedMs;
                smoothedCps = (smoothedCps * 0.5) + (instantCps * 0.5);

                lastGeneratedCount = currentTotal;
                lastCpsSampleMs = now;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && xInput != null && zInput != null) {
            if (xInput.isMouseOver(mouseX, mouseY)) {
                xInput.setValue("");
                this.setFocused(xInput);
                xInput.setFocused(true);
                zInput.setFocused(false);
                return true;
            } else if (zInput.isMouseOver(mouseX, mouseY)) {
                zInput.setValue("");
                this.setFocused(zInput);
                zInput.setFocused(true);
                xInput.setFocused(false);
                return true;
            }
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (xInput != null && zInput != null) {
            if (xInput.isMouseOver(mouseX, mouseY)) {
                zInput.setFocused(false);
            } else if (zInput.isMouseOver(mouseX, mouseY)) {
                xInput.setFocused(false);
            }
        }

        if (button == 0 && !handled) {
            int mapSizePx = getMapSizePx();
            int minX = (this.width - mapSizePx) / 2;
            int minY = (this.height - mapSizePx) / 2 - 8;
            isDraggingMap = mouseX >= minX && mouseX <= minX + mapSizePx
                && mouseY >= minY && mouseY <= minY + mapSizePx;
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && isDraggingMap) {
            float scale = (float) getMapSizePx() / VIEW_CHUNKS;
            panOffsetX -= (int) Math.round(dragX / scale);
            panOffsetZ -= (int) Math.round(dragY / scale);
            syncCoordInputs();
            requestAreaUpdate();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingMap = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}