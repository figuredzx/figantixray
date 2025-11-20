package com.figuredzx.antixray.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class PlayerJoinHandler {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // Check for suspicious players when OP logs in
            if (player.hasPermissionLevel(2)) {
                String currentDate = DATE_FORMAT.format(new Date());
                List<PlayerDataManager.PlayerMiningData> exceedingPlayers =
                        PlayerDataManager.getPlayersExceedingThreshold(currentDate);

                if (!exceedingPlayers.isEmpty()) {
                    // Chinese alert message
                    player.sendMessage(Text.literal("⚠️ ").formatted(Formatting.RED)
                            .append(Text.literal(" Figanti反透视警告: 今天有 " + exceedingPlayers.size() +
                                    " 名玩家超过挖掘阈值").formatted(Formatting.YELLOW)));

                    // Show top 3 suspicious players with detailed mining records
                    int count = Math.min(exceedingPlayers.size(), 3);
                    for (int i = 0; i < count; i++) {
                        PlayerDataManager.PlayerMiningData data = exceedingPlayers.get(i);

                        // Show player overview
                        player.sendMessage(Text.literal("   " + data.playerName + ": 总共 " +
                                data.getTotalMonitoredBlocks() + " 个稀有方块").formatted(Formatting.GOLD));

                        // Show detailed mining records with threshold info
                        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
                            String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                            int threshold = ConfigManager.getBlockThreshold(entry.getKey());
                            String thresholdInfo = entry.getValue() >= threshold ?
                                    " (超过阈值 " + threshold + ")" : " (阈值: " + threshold + ")";
                            player.sendMessage(Text.literal("     - " + blockName + ": " +
                                    entry.getValue() + " 个" + thresholdInfo).formatted(
                                    entry.getValue() >= threshold ? Formatting.RED : Formatting.WHITE
                            ));
                        }

                        // Add spacing between players
                        if (i < count - 1) {
                            player.sendMessage(Text.literal(""));
                        }
                    }

                    if (exceedingPlayers.size() > 3) {
                        player.sendMessage(Text.literal("   ... 还有 " + (exceedingPlayers.size() - 3) +
                                " 名玩家，使用 /figantixray check 查看详情").formatted(Formatting.GRAY));
                    }

                    player.sendMessage(Text.literal("💡 提示: 使用 /figantixray check 查看完整报告").formatted(Formatting.AQUA));
                }
            }
        });
    }
}