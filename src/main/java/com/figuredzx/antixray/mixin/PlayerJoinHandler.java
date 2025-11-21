package com.figuredzx.antixray.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class PlayerJoinHandler {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            // 检查玩家是否是OP（权限等级2或以上）
            if (player.hasPermissionLevel(2)) {
                // 延迟一下，确保玩家完全进入游戏
                server.execute(() -> notifyOpAboutExceedingPlayers(player));
            }
        });
    }

    /**
     * 通知OP玩家关于超过阈值的玩家
     */
    private static void notifyOpAboutExceedingPlayers(ServerPlayerEntity opPlayer) {
        // 获取超过阈值的玩家列表（无参数调用）
        List<PlayerDataManager.PlayerMiningData> exceedingPlayers = PlayerDataManager.getPlayersExceedingThreshold();

        if (!exceedingPlayers.isEmpty()) {
            opPlayer.sendMessage(Text.literal("=== 反透视警告 ===").formatted(Formatting.RED));
            opPlayer.sendMessage(Text.literal("当前有 " + exceedingPlayers.size() + " 名玩家超过挖掘阈值:").formatted(Formatting.YELLOW));

            for (PlayerDataManager.PlayerMiningData data : exceedingPlayers) {
                // 显示每个超过阈值玩家的详细信息
                opPlayer.sendMessage(Text.literal("● " + data.playerName + ": " + data.getTotalMonitoredBlocks() + " 个稀有方块").formatted(Formatting.WHITE));

                // 显示该玩家的具体方块挖掘情况
                for (var entry : data.blockCounts.entrySet()) {
                    String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                    int threshold = ConfigManager.getBlockThreshold(entry.getKey());
                    if (entry.getValue() >= threshold) {
                        opPlayer.sendMessage(Text.literal("  └ " + blockName + ": " + entry.getValue() + " 个 (超过阈值 " + threshold + ")").formatted(Formatting.RED));
                    } else {
                        opPlayer.sendMessage(Text.literal("  └ " + blockName + ": " + entry.getValue() + " 个 (阈值: " + threshold + ")").formatted(Formatting.GRAY));
                    }
                }
                opPlayer.sendMessage(Text.literal(""));
            }

            opPlayer.sendMessage(Text.literal("使用 /figantixray check 查看详细信息").formatted(Formatting.AQUA));
            opPlayer.sendMessage(Text.literal("使用 /figantixray violationhistory <玩家名> 查看违规记录").formatted(Formatting.AQUA)); // 新增提示
            opPlayer.sendMessage(Text.literal("使用 /figantixray status 查看模组状态").formatted(Formatting.AQUA));
        }
    }
}