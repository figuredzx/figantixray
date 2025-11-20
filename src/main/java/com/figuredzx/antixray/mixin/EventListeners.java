package com.figuredzx.antixray.mixin;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class EventListeners {

    public static void register() {
        // Listen for block break events
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                try {
                    // 检查是否记录OP玩家
                    if (!ConfigManager.isOpRecordEnabled() && serverPlayer.hasPermissionLevel(2)) {
                        return; // 不记录OP玩家
                    }
                    Identifier blockId = Registries.BLOCK.getId(state.getBlock());

                    if (ConfigManager.isBlockMonitored(blockId)) {
                        PlayerDataManager.recordBlockBreak(serverPlayer, blockId.toString());
                    }
                } catch (Exception e) {
                    FigantiXray.LOGGER.warn("Failed to process block break event", e);
                }
            }
        });
    }
}