package com.gmail.rawlxxxviii.raws_chunk_generation.config;

import com.gmail.rawlxxxviii.raws_chunk_generation.RawsChunkGenerationMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = RawsChunkGenerationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)

public class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ServerConfig INSTANCE;

    public final ForgeConfigSpec.BooleanValue OPERATOR_COUNTS_AS_ONLINE_PLAYER;
    public final ForgeConfigSpec.IntValue GENERATION_BUFFER_RADIUS;
    public final ForgeConfigSpec.DoubleValue ACTIVE_SERVER_GENERATION_BUFFER_SPEED;
    public final ForgeConfigSpec.IntValue ACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS;
    public final ForgeConfigSpec.IntValue ACTIVATED_AREA_GENERATION_RADIUS;
    public final ForgeConfigSpec.DoubleValue INACTIVE_SERVER_GENERATION_BUFFER_SPEED;
    public final ForgeConfigSpec.IntValue INACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS;
    public final ForgeConfigSpec.BooleanValue LIMIT_INFO_SCREEN_TO_OPERATORS;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACK_LIST;

    private static final Set<String> blacklistedDimensions = new HashSet<>();

    static {
        Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    private ServerConfig(ForgeConfigSpec.Builder builder) {
        builder.push("Active Server Generation");
            builder.comment("Settings for when there are players online");
            OPERATOR_COUNTS_AS_ONLINE_PLAYER = builder.comment(
                "Whether to include operator as a player when checking whether the server is active or inactive.")
                .define("operator_counts_as_online_player", true);
            GENERATION_BUFFER_RADIUS = builder.comment(
                "Chunk (16 blocks) radius around online players to slowly pre-generate chunks.")
                .defineInRange("generation_buffer_radius", 64, 0, 128);
            ACTIVE_SERVER_GENERATION_BUFFER_SPEED = builder.comment(
                "Maximum amount of chunks per second to generate for the online player buffer.",
                "Set to 0.0 to disable the online buffer completely.")
                .defineInRange("active_server_generation_buffer_speed", 10.0, 0.0, 100.0);
            ACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS = builder.comment(
                "Maximum concurrent chunk tickets queued for the online player buffer.")
                .defineInRange("active_server_generation_buffer_max_tickets", 6, 1, 20);
        builder.pop();
        builder.push("Inactive Server Generation");
            ACTIVATED_AREA_GENERATION_RADIUS = builder
                .comment(
                "Chunk (16 blocks) radius around recent player activity areas to generate while no players are online.")
                .defineInRange("offline_player_activated_area_generation_radius", 128, 0, 512);
            INACTIVE_SERVER_GENERATION_BUFFER_SPEED = builder.comment(
                "Maximum amount of chunks per second to generate for the activated area generation.",
                "Set to 0.0 to disable the activated area generation completely.")
                .defineInRange("inactive_generation_buffer_speed", 2.0, 0.0, 40.0);
            INACTIVE_SERVER_GENERATION_BUFFER_MAX_TICKETS = builder.comment(
                    "Maximum concurrent chunk tickets queued for the activated area generation.")
                .defineInRange("inactive_server_generation_buffer_max_tickets", 10, 1, 40);
        builder.pop();
        builder.push("Server Info");
            LIMIT_INFO_SCREEN_TO_OPERATORS = builder.comment(
                "Limits the usage of the client visualiser/info screen to server operators.",
                "Prevents regular players from seeing which areas of the map have already been explored/generated.")
                .define("limit_info_screen_to_operators", true);
        builder.pop();
        builder.push("Dimensions");
            DIMENSION_BLACK_LIST = builder.comment(
                "List of dimension IDs (e.g. 'minecraft:the_end') or mod namespaces (e.g. 'compactmachines:*') to ignore.")
                .defineList(
                    "dimension_black_list",
                    List.of(
                        "compactmachines:*",
                        "ae2:spatial_storage",
                        "javd:void",
                        "voidtotem:*",
                        "dimdungeons:*",
                        "mahoutsukai:reality_marble",
                        "bloodmagic:dungeon",
                        "botania:garden_of_glass",
                        "tardis:*",
                        "immersiveportals:*",
                        "rftoolsdim:*",
                        "mystcraft:*"
                    ),
                    obj -> obj instanceof String str && !str.isBlank()
                );
        builder.pop();
    }

    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bakeBlacklist();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bakeBlacklist();
        }
    }

    private static void bakeBlacklist() {
        blacklistedDimensions.clear();
        for (String entry : INSTANCE.DIMENSION_BLACK_LIST.get()) {
            String trimmed = entry.trim().toLowerCase();
            if (trimmed.split(":").length != 2) {
                continue;
            }
            blacklistedDimensions.add(trimmed);
        }
    }

    public static boolean isDimensionEnabled(ResourceKey<Level> dimensionKey) {
        return !blacklistedDimensions.contains(dimensionKey.location().getNamespace() + ":*")
            && !blacklistedDimensions.contains(dimensionKey.location().toString());
    }
}
