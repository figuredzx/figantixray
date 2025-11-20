package com.figuredzx.antixray.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XrayCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("figantixray")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("status")
                        .executes(XrayCommand::showStatus)
                )
                .then(CommandManager.literal("threshold")
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> setThreshold(context, IntegerArgumentType.getInteger(context, "amount")))
                        )
                )
                .then(CommandManager.literal("blockthreshold")
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                .then(CommandManager.argument("block_id", StringArgumentType.string())
                                        .executes(context -> setBlockThreshold(
                                                context,
                                                StringArgumentType.getString(context, "block_id"),
                                                IntegerArgumentType.getInteger(context, "amount")
                                        ))
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray blockthreshold <数量> \"<方块ID>\"").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray blockthreshold 32 \"minecraft:diamond_ore\"").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("设置特定方块的检测阈值，设置为0使用全局阈值").formatted(Formatting.GRAY));
                            displayCurrentBlockThresholds(context.getSource());
                            return 0;
                        })
                )
                // 修复：简化 addblock 命令，只保留一个参数版本
                .then(CommandManager.literal("addblock")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .executes(context -> {
                                    // 直接在这里调用方法，避免方法引用歧义
                                    return addMonitoredBlock(context, StringArgumentType.getString(context, "block_id"));
                                })
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray addblock \"<方块ID>\"").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray addblock \"minecraft:diamond_ore\"").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("添加后可使用 /figantixray setblockname 设置自定义名称").formatted(Formatting.GRAY));
                            context.getSource().sendMessage(Text.literal("注意: 方块ID需要用引号包裹").formatted(Formatting.RED));

                            context.getSource().sendMessage(Text.literal("常见的稀有方块ID:").formatted(Formatting.AQUA));
                            context.getSource().sendMessage(Text.literal(" - \"minecraft:diamond_ore\" (钻石矿)").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal(" - \"minecraft:deepslate_diamond_ore\" (深层钻石矿)").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal(" - \"minecraft:emerald_ore\" (绿宝石矿)").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal(" - \"minecraft:gold_ore\" (金矿)").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal(" - \"minecraft:ancient_debris\" (下界残骸)").formatted(Formatting.WHITE));
                            return 0;
                        })
                )
                .then(CommandManager.literal("removeblock")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .executes(context -> removeMonitoredBlock(context, StringArgumentType.getString(context, "block_id")))
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray removeblock \"<方块ID>\"").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray removeblock \"minecraft:diamond_ore\"").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("使用 /figantixray listblocks 查看当前监控的方块").formatted(Formatting.GRAY));
                            context.getSource().sendMessage(Text.literal("注意: 方块ID需要用引号包裹").formatted(Formatting.RED));
                            displayCurrentMonitoredBlocks(context.getSource());
                            return 0;
                        })
                )
                .then(CommandManager.literal("setblockname")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .then(CommandManager.argument("custom_name", StringArgumentType.string())
                                        .executes(context -> setBlockCustomName(
                                                context,
                                                StringArgumentType.getString(context, "block_id"),
                                                StringArgumentType.getString(context, "custom_name")
                                        ))
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray setblockname \"<方块ID>\" \"<自定义名称>\"").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray setblockname \"minecraft:diamond_ore\" \"珍贵钻石矿\"").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("注意: 方块ID和自定义名称都需要用引号包裹").formatted(Formatting.RED));
                            return 0;
                        })
                )
                .then(CommandManager.literal("check")
                        .executes(XrayCommand::checkAllPlayers)
                        .then(CommandManager.argument("player", StringArgumentType.string())
                                .executes(context -> checkPlayer(context, StringArgumentType.getString(context, "player")))
                        )
                )
                .then(CommandManager.literal("listblocks")
                        .executes(XrayCommand::listMonitoredBlocks)
                )
                .then(CommandManager.literal("deleteblockdata")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .then(CommandManager.argument("password", StringArgumentType.string())
                                        .executes(context -> deleteBlockData(
                                                context,
                                                StringArgumentType.getString(context, "block_id"),
                                                StringArgumentType.getString(context, "password")
                                        ))
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray deleteblockdata \"<方块ID>\" <密码>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray deleteblockdata \"minecraft:diamond_ore\" my_password").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("警告: 此操作将永久删除该方块的所有历史挖掘数据，不可恢复！").formatted(Formatting.RED));
                            context.getSource().sendMessage(Text.literal("注意: 默认密码是 'default_password_123'").formatted(Formatting.GRAY));
                            context.getSource().sendMessage(Text.literal("注意: 方块ID需要用引号包裹").formatted(Formatting.RED));
                            return 0;
                        })
                )
                .then(CommandManager.literal("changepassword")
                        .then(CommandManager.argument("old_password", StringArgumentType.string())
                                .then(CommandManager.argument("new_password", StringArgumentType.string())
                                        .executes(context -> changePassword(
                                                context,
                                                StringArgumentType.getString(context, "old_password"),
                                                StringArgumentType.getString(context, "new_password")
                                        ))
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray changepassword <旧密码> <新密码>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray changepassword old_password new_secure_password").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("注意: 默认密码是 'default_password_123'").formatted(Formatting.GRAY));
                            context.getSource().sendMessage(Text.literal("注意: 密码不能包含空格").formatted(Formatting.RED));
                            return 0;
                        })
                )
                .executes(context -> {
                    context.getSource().sendMessage(Text.literal("=== Figanti反透视模组命令帮助 ===").formatted(Formatting.GOLD));
                    context.getSource().sendMessage(Text.literal("/figantixray status - 查看模组状态").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray threshold <数量> - 设置全局警告阈值").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray blockthreshold <数量> \"<方块ID>\" - 设置特定方块警告阈值").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray addblock \"<方块ID>\" - 添加监控方块").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray setblockname \"<方块ID>\" \"<自定义名称>\" - 设置方块自定义名称").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray removeblock \"<方块ID>\" - 移除监控方块").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray listblocks - 列出所有监控方块").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray check - 检查所有玩家").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray check <玩家名> - 检查特定玩家").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray deleteblockdata \"<方块ID>\" <密码> - 删除方块历史数据").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("/figantixray changepassword <旧密码> <新密码> - 修改删除密码").formatted(Formatting.YELLOW));
                    context.getSource().sendMessage(Text.literal("注意: 所有带冒号的方块ID都需要用引号包裹").formatted(Formatting.RED));
                    return 1;
                })
        );
    }

    // 修复：明确定义 addMonitoredBlock 方法
    private static int addMonitoredBlock(CommandContext<ServerCommandSource> context, String blockId) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("请使用引号包裹方块ID: /figantixray addblock \"minecraft:diamond_ore\"").formatted(Formatting.RED));
            return 0;
        }

        Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();
        if (monitoredBlocks.contains(blockId)) {
            String displayName = ConfigManager.getBlockDisplayName(blockId);
            source.sendMessage(Text.literal("方块 " + displayName + " (" + blockId + ") 已经在监控列表中").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            ConfigManager.addMonitoredBlock(blockId);
            String displayName = ConfigManager.getBlockDisplayName(blockId);

            source.sendMessage(Text.literal("✅ 已添加监控方块: " + displayName).formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("当前监控方块数量: " + ConfigManager.getMonitoredBlocks().size()).formatted(Formatting.GRAY));
            source.sendMessage(Text.literal("💡 提示: 使用 /figantixray setblockname \"" + blockId + "\" \"<名称>\" 设置自定义名称").formatted(Formatting.AQUA));

            return 1;
        } catch (Exception e) {
            String errorMsg = "添加方块失败";
            if (e.getMessage() != null) {
                errorMsg += ": " + e.getMessage();
            } else {
                errorMsg += "，请检查控制台日志获取详细信息";
            }
            source.sendMessage(Text.literal(errorMsg).formatted(Formatting.RED));
            source.sendMessage(Text.literal("请确保使用正确的方块ID格式并用引号包裹").formatted(Formatting.YELLOW));

            FigantiXray.LOGGER.error("添加方块失败: {}", blockId, e);
            return 0;
        }
    }

    // 其他方法保持不变...
    private static void displayCurrentMonitoredBlocks(ServerCommandSource source) {
        Set<String> blocks = ConfigManager.getMonitoredBlocks();
        if (!blocks.isEmpty()) {
            source.sendMessage(Text.literal("当前监控的方块:").formatted(Formatting.AQUA));
            for (String block : blocks) {
                String displayName = ConfigManager.getBlockDisplayName(block);
                int threshold = ConfigManager.getBlockThreshold(block);
                String customNameInfo = ConfigManager.getBlockCustomName(block) != null ? " [自定义]" : "";
                source.sendMessage(Text.literal(" - " + displayName + customNameInfo + " (" + block + ") - 阈值: " + threshold).formatted(Formatting.WHITE));
            }
        } else {
            source.sendMessage(Text.literal("当前没有监控任何方块").formatted(Formatting.GRAY));
        }
    }

    private static void displayCurrentBlockThresholds(ServerCommandSource source) {
        Map<String, Integer> blockThresholds = ConfigManager.getBlockThresholds();
        int globalThreshold = ConfigManager.getThreshold();

        source.sendMessage(Text.literal("全局检测阈值: " + globalThreshold).formatted(Formatting.GOLD));

        if (!blockThresholds.isEmpty()) {
            source.sendMessage(Text.literal("设置了特殊阈值的方块:").formatted(Formatting.AQUA));
            for (Map.Entry<String, Integer> entry : blockThresholds.entrySet()) {
                String displayName = ConfigManager.getBlockDisplayName(entry.getKey());
                String customNameInfo = ConfigManager.getBlockCustomName(entry.getKey()) != null ? " [自定义]" : "";
                source.sendMessage(Text.literal(" - " + displayName + customNameInfo + " (" + entry.getKey() + ") - 阈值: " + entry.getValue()).formatted(Formatting.WHITE));
            }
        } else {
            source.sendMessage(Text.literal("当前没有设置任何特殊方块阈值").formatted(Formatting.GRAY));
        }
    }

    private static boolean isValidBlockId(String blockId) {
        if (blockId == null || blockId.trim().isEmpty()) {
            return false;
        }
        if (!blockId.contains(":")) {
            return false;
        }
        if (blockId.length() < 3 || blockId.length() > 100) {
            return false;
        }
        return !blockId.contains(" ") && !blockId.contains("\"") && !blockId.contains("'");
    }

    private static int showStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String currentDate = DATE_FORMAT.format(new Date());
        List<PlayerDataManager.PlayerMiningData> exceedingPlayers = PlayerDataManager.getPlayersExceedingThreshold(currentDate);

        source.sendMessage(Text.literal("=== Figanti反透视状态 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("全局警告阈值: " + ConfigManager.getThreshold() + " 个方块"));

        Map<String, Integer> blockThresholds = ConfigManager.getBlockThresholds();
        if (!blockThresholds.isEmpty()) {
            source.sendMessage(Text.literal("特殊方块阈值:").formatted(Formatting.AQUA));
            for (Map.Entry<String, Integer> entry : blockThresholds.entrySet()) {
                String displayName = ConfigManager.getBlockDisplayName(entry.getKey());
                source.sendMessage(Text.literal(" - " + displayName + ": " + entry.getValue() + " 个").formatted(Formatting.WHITE));
            }
        }

        source.sendMessage(Text.literal("监控方块数量: " + ConfigManager.getMonitoredBlocks().size()));
        source.sendMessage(Text.literal("今天超过阈值的玩家: " + exceedingPlayers.size() + " 名"));

        if (!exceedingPlayers.isEmpty()) {
            source.sendMessage(Text.literal("超过阈值的玩家:").formatted(Formatting.YELLOW));
            for (PlayerDataManager.PlayerMiningData data : exceedingPlayers) {
                source.sendMessage(Text.literal(" - " + data.playerName + ": " + data.getTotalMonitoredBlocks() + " 个稀有方块"));
            }
        }

        return 1;
    }

    private static int setThreshold(CommandContext<ServerCommandSource> context, int threshold) {
        ConfigManager.setThreshold(threshold);
        context.getSource().sendMessage(Text.literal("全局警告阈值已设置为 " + threshold).formatted(Formatting.GREEN));
        return 1;
    }

    private static int setBlockThreshold(CommandContext<ServerCommandSource> context, String blockId, int threshold) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            ConfigManager.setBlockThreshold(blockId, threshold);
            String displayName = ConfigManager.getBlockDisplayName(blockId);

            if (threshold > 0) {
                source.sendMessage(Text.literal("✅ 方块 " + displayName + " 的检测阈值已设置为 " + threshold).formatted(Formatting.GREEN));
            } else {
                source.sendMessage(Text.literal("✅ 方块 " + displayName + " 的特殊阈值已移除，将使用全局阈值").formatted(Formatting.GREEN));
            }

            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("设置方块阈值失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int removeMonitoredBlock(CommandContext<ServerCommandSource> context, String blockId) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();
            if (!monitoredBlocks.contains(blockId)) {
                String displayName = ConfigManager.getBlockDisplayName(blockId);
                source.sendMessage(Text.literal("方块 " + displayName + " (" + blockId + ") 不在监控列表中").formatted(Formatting.YELLOW));
                displayCurrentMonitoredBlocks(source);
                return 0;
            }

            ConfigManager.removeMonitoredBlock(blockId);
            String displayName = ConfigManager.getBlockDisplayName(blockId);
            source.sendMessage(Text.literal("✅ 已移除监控方块: " + displayName).formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("当前监控方块数量: " + ConfigManager.getMonitoredBlocks().size()).formatted(Formatting.GRAY));
            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("移除方块失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setBlockCustomName(CommandContext<ServerCommandSource> context, String blockId, String customName) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            return 0;
        }

        if (!ConfigManager.getMonitoredBlocks().contains(blockId)) {
            source.sendMessage(Text.literal("错误: 方块 " + blockId + " 不在监控列表中").formatted(Formatting.RED));
            source.sendMessage(Text.literal("请先使用 /figantixray addblock 添加该方块").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            ConfigManager.setBlockCustomName(blockId, customName);
            String displayName = ConfigManager.getBlockDisplayName(blockId);

            if (customName != null && !customName.trim().isEmpty()) {
                source.sendMessage(Text.literal("✅ 方块 " + blockId + " 的自定义名称已设置为: " + displayName).formatted(Formatting.GREEN));
            } else {
                source.sendMessage(Text.literal("✅ 方块 " + blockId + " 的自定义名称已移除，使用默认名称: " + displayName).formatted(Formatting.GREEN));
            }

            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("设置方块名称失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int checkAllPlayers(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String date = DATE_FORMAT.format(new Date());
        List<PlayerDataManager.PlayerMiningData> exceedingPlayers = PlayerDataManager.getPlayersExceedingThreshold(date);

        if (exceedingPlayers.isEmpty()) {
            source.sendMessage(Text.literal("今天没有玩家超过警告阈值").formatted(Formatting.GREEN));
        } else {
            source.sendMessage(Text.literal("今天超过警告阈值的玩家:").formatted(Formatting.YELLOW));
            for (PlayerDataManager.PlayerMiningData data : exceedingPlayers) {
                source.sendMessage(Text.literal("=== " + data.playerName + " ===").formatted(Formatting.GOLD));
                source.sendMessage(Text.literal("总计稀有方块: " + data.getTotalMonitoredBlocks() + " 个").formatted(Formatting.AQUA));

                for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
                    String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                    int threshold = ConfigManager.getBlockThreshold(entry.getKey());
                    String thresholdInfo = entry.getValue() >= threshold ? " (超过阈值 " + threshold + ")" : " (阈值: " + threshold + ")";
                    source.sendMessage(Text.literal("  - " + blockName + ": " + entry.getValue() + " 个" + thresholdInfo).formatted(
                            entry.getValue() >= threshold ? Formatting.RED : Formatting.WHITE
                    ));
                }
                source.sendMessage(Text.literal(""));
            }
        }

        return exceedingPlayers.size();
    }

    private static int checkPlayer(CommandContext<ServerCommandSource> context, String playerName) {
        ServerCommandSource source = context.getSource();

        PlayerDataManager.PlayerMiningData data = PlayerDataManager.getPlayerDataByName(playerName);
        if (data == null || data.getTotalMonitoredBlocks() == 0) {
            source.sendMessage(Text.literal("未找到 " + playerName + " 的挖掘数据").formatted(Formatting.GREEN));
            return 0;
        }

        source.sendMessage(Text.literal("=== " + playerName + " 的挖掘数据 ===").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("总计稀有方块: " + data.getTotalMonitoredBlocks() + " 个").formatted(Formatting.GOLD));

        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
            int threshold = ConfigManager.getBlockThreshold(entry.getKey());
            String thresholdInfo = entry.getValue() >= threshold ? " (超过阈值 " + threshold + ")" : " (阈值: " + threshold + ")";
            source.sendMessage(Text.literal(" - " + blockName + ": " + entry.getValue() + " 个" + thresholdInfo).formatted(
                    entry.getValue() >= threshold ? Formatting.RED : Formatting.WHITE
            ));
        }

        return 1;
    }

    private static int listMonitoredBlocks(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        var blocks = ConfigManager.getMonitoredBlocks();

        source.sendMessage(Text.literal("监控方块列表 (" + blocks.size() + " 种):").formatted(Formatting.YELLOW));
        for (String block : blocks) {
            String displayName = ConfigManager.getBlockDisplayName(block);
            int threshold = ConfigManager.getBlockThreshold(block);
            String customNameInfo = ConfigManager.getBlockCustomName(block) != null ? " [自定义]" : "";
            source.sendMessage(Text.literal(" - " + displayName + customNameInfo + " (" + block + ") - 阈值: " + threshold).formatted(Formatting.WHITE));
        }

        return blocks.size();
    }

    private static int deleteBlockData(CommandContext<ServerCommandSource> context, String blockId, String password) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            return 0;
        }

        if (!ConfigManager.verifyDeletePassword(password)) {
            source.sendMessage(Text.literal("错误: 密码不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("请检查密码是否正确，默认密码是 'default_password_123'").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            int deletedCount = PlayerDataManager.deleteBlockHistoryData(blockId);
            String displayName = ConfigManager.getBlockDisplayName(blockId);

            source.sendMessage(Text.literal("✅ 已成功删除方块 " + displayName + " 的历史数据").formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("共清理了 " + deletedCount + " 条数据记录").formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("注意: 此操作不可恢复，相关数据已永久删除").formatted(Formatting.RED));

            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("删除方块数据失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int changePassword(CommandContext<ServerCommandSource> context, String oldPassword, String newPassword) {
        ServerCommandSource source = context.getSource();

        if (newPassword == null || newPassword.trim().isEmpty()) {
            source.sendMessage(Text.literal("错误: 新密码不能为空").formatted(Formatting.RED));
            return 0;
        }

        if (newPassword.contains(" ")) {
            source.sendMessage(Text.literal("错误: 新密码不能包含空格").formatted(Formatting.RED));
            return 0;
        }

        if (newPassword.length() < 6) {
            source.sendMessage(Text.literal("警告: 密码长度建议至少6位").formatted(Formatting.YELLOW));
        }

        try {
            boolean success = ConfigManager.changeDeletePassword(oldPassword, newPassword);

            if (success) {
                source.sendMessage(Text.literal("✅ 密码已成功修改").formatted(Formatting.GREEN));
                source.sendMessage(Text.literal("新密码: " + newPassword).formatted(Formatting.GRAY));
                source.sendMessage(Text.literal("请妥善保管此密码，删除操作需要验证此密码").formatted(Formatting.YELLOW));
                return 1;
            } else {
                source.sendMessage(Text.literal("错误: 旧密码不正确").formatted(Formatting.RED));
                source.sendMessage(Text.literal("请检查旧密码是否正确，默认密码是 'default_password_123'").formatted(Formatting.YELLOW));
                return 0;
            }
        } catch (Exception e) {
            source.sendMessage(Text.literal("修改密码失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
}