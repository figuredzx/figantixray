package com.figuredzx.antixray.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XrayCommand {

    // 玩家名称自动补全提供器
    private static final SuggestionProvider<ServerCommandSource> PLAYER_NAME_SUGGESTIONS =
            (context, builder) -> {
                List<String> playerNames = PlayerDataManager.getAllPlayerNames();
                for (String name : playerNames) {
                    builder.suggest(name);
                }
                return builder.buildFuture();
            };

    // 方块ID自动补全提供器（自动添加引号）
    private static final SuggestionProvider<ServerCommandSource> BLOCK_ID_SUGGESTIONS =
            (context, builder) -> {
                Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();
                for (String blockId : monitoredBlocks) {
                    // 自动为方块ID添加引号
                    String displayName = ConfigManager.getBlockDisplayName(blockId);
                    builder.suggest("\"" + blockId + "\"", Text.literal(displayName));
                }
                return builder.buildFuture();
            };

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
                                        .suggests(BLOCK_ID_SUGGESTIONS)  // 方块ID自动补全
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
                .then(CommandManager.literal("addblock")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .executes(context -> addMonitoredBlock(context, StringArgumentType.getString(context, "block_id")))
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
                                .suggests(BLOCK_ID_SUGGESTIONS)  // 方块ID自动补全
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
                                .suggests(BLOCK_ID_SUGGESTIONS)  // 方块ID自动补全
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
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(context -> checkPlayer(context, StringArgumentType.getString(context, "player")))
                        )
                )
                .then(CommandManager.literal("listblocks")
                        .executes(XrayCommand::listMonitoredBlocks)
                )
                // OP玩家记录开关命令
                .then(CommandManager.literal("oprecord")
                        .then(CommandManager.literal("on")
                                .executes(context -> {
                                    ConfigManager.setOpRecordEnabled(true);
                                    context.getSource().sendMessage(Text.literal("✅ 已开启OP玩家记录").formatted(Formatting.GREEN));
                                    return 1;
                                })
                        )
                        .then(CommandManager.literal("off")
                                .executes(context -> {
                                    ConfigManager.setOpRecordEnabled(false);
                                    context.getSource().sendMessage(Text.literal("✅ 已关闭OP玩家记录").formatted(Formatting.GREEN));
                                    context.getSource().sendMessage(Text.literal("注意: OP玩家的挖掘行为将不再被记录").formatted(Formatting.YELLOW));
                                    return 1;
                                })
                        )
                        .executes(context -> {
                            boolean isEnabled = ConfigManager.isOpRecordEnabled();
                            context.getSource().sendMessage(Text.literal("OP玩家记录状态: " + (isEnabled ? "已开启" : "已关闭")).formatted(
                                    isEnabled ? Formatting.GREEN : Formatting.RED
                            ));
                            context.getSource().sendMessage(Text.literal("用法: /figantixray oprecord <on|off>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray oprecord off - 关闭OP玩家记录").formatted(Formatting.WHITE));
                            return 1;
                        })
                )
                // 减少玩家方块数量命令
                .then(CommandManager.literal("reduceblock")
                        .then(CommandManager.argument("player_name", StringArgumentType.string())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .then(CommandManager.argument("block_id", StringArgumentType.string())
                                        .suggests(BLOCK_ID_SUGGESTIONS)  // 方块ID自动补全
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                        .executes(context -> reducePlayerBlock(
                                                                context,
                                                                StringArgumentType.getString(context, "player_name"),
                                                                StringArgumentType.getString(context, "block_id"),
                                                                IntegerArgumentType.getInteger(context, "amount"),
                                                                StringArgumentType.getString(context, "reason")
                                                        ))
                                                )
                                        )
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray reduceblock <玩家名> <方块ID> <数量> <原因>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray reduceblock Steve minecraft:diamond_ore 5 \"工会奖励发放\"").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("功能: 减少玩家特定方块的数量，用于奖励发放等场景").formatted(Formatting.GRAY));
                            context.getSource().sendMessage(Text.literal("注意: 方块ID需要用引号包裹（如果包含冒号）").formatted(Formatting.RED));

                            // 显示有数据的玩家列表
                            List<String> playerNames = PlayerDataManager.getAllPlayerNames();
                            if (!playerNames.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("当前有数据的玩家 (" + playerNames.size() + " 名):").formatted(Formatting.AQUA));
                                for (String name : playerNames) {
                                    context.getSource().sendMessage(Text.literal(" - " + name).formatted(Formatting.WHITE));
                                }
                            }
                            return 0;
                        })
                )
                // 查看减少记录历史命令
                .then(CommandManager.literal("reductionhistory")
                        .then(CommandManager.argument("player_name", StringArgumentType.string())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(context -> showReductionHistory(
                                        context,
                                        StringArgumentType.getString(context, "player_name")
                                ))
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray reductionhistory <玩家名>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray reductionhistory Steve").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("功能: 查看玩家的方块减少记录历史").formatted(Formatting.GRAY));
                            return 0;
                        })
                )
                // 查看玩家违规记录命令
                .then(CommandManager.literal("violationhistory")
                        .then(CommandManager.argument("player_name", StringArgumentType.string())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(context -> showViolationHistory(
                                        context,
                                        StringArgumentType.getString(context, "player_name")
                                ))
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray violationhistory <玩家名>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray violationhistory Steve").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("功能: 查看玩家的违规记录历史").formatted(Formatting.GRAY));
                            return 0;
                        })
                )
                // 查看玩家违规时间戳命令
                .then(CommandManager.literal("violationtimestamps")
                        .then(CommandManager.argument("player_name", StringArgumentType.string())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(context -> showViolationTimestamps(
                                        context,
                                        StringArgumentType.getString(context, "player_name")
                                ))
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray violationtimestamps <玩家名>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray violationtimestamps Steve").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("功能: 查看玩家的违规时间戳记录，方便服务器回放查找").formatted(Formatting.GRAY));
                            return 0;
                        })
                )
                // 删除玩家数据命令
                .then(CommandManager.literal("deleteplayer")
                        .then(CommandManager.argument("player_name", StringArgumentType.string())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .then(CommandManager.argument("password", StringArgumentType.string())
                                        .executes(context -> deletePlayerData(
                                                context,
                                                StringArgumentType.getString(context, "player_name"),
                                                StringArgumentType.getString(context, "password")
                                        ))
                                )
                        )
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("用法: /figantixray deleteplayer <玩家名> <密码>").formatted(Formatting.YELLOW));
                            context.getSource().sendMessage(Text.literal("例如: /figantixray deleteplayer Steve my_password").formatted(Formatting.WHITE));
                            context.getSource().sendMessage(Text.literal("警告: 此操作将永久删除该玩家的所有挖掘数据，不可恢复！").formatted(Formatting.RED));
                            context.getSource().sendMessage(Text.literal("注意: 默认密码是 'default_password_123'").formatted(Formatting.GRAY));

                            // 显示所有玩家列表
                            List<String> playerNames = PlayerDataManager.getAllPlayerNames();
                            if (!playerNames.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("当前有数据的玩家 (" + playerNames.size() + " 名):").formatted(Formatting.AQUA));
                                for (String name : playerNames) {
                                    context.getSource().sendMessage(Text.literal(" - " + name).formatted(Formatting.WHITE));
                                }
                            } else {
                                context.getSource().sendMessage(Text.literal("当前没有玩家数据").formatted(Formatting.GRAY));
                            }
                            return 0;
                        })
                )
                .then(CommandManager.literal("deleteblockdata")
                        .then(CommandManager.argument("block_id", StringArgumentType.string())
                                .suggests(BLOCK_ID_SUGGESTIONS)  // 方块ID自动补全
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
                // 详细帮助命令
                .then(CommandManager.literal("help")
                        .executes(XrayCommand::showDetailedHelp)
                )
                .executes(context -> {
                    showQuickHelp(context.getSource());
                    return 1;
                })
        );
    }

    /**
     * 显示详细帮助信息
     */
    private static int showDetailedHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        source.sendMessage(Text.literal("=== Figanti反透视模组详细帮助 ===").formatted(Formatting.GOLD));

        source.sendMessage(Text.literal("📊 状态监控命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray status - 查看模组运行状态、全局阈值、监控方块数量等").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray check - 检查所有玩家的挖掘数据，显示超过阈值的玩家").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray check <玩家名> - 检查特定玩家的详细挖掘数据").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("🎯 阈值设置命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray threshold <数量> - 设置全局警告阈值（默认64）").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray blockthreshold <数量> \"<方块ID>\" - 设置特定方块的检测阈值").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    设置为0时使用全局阈值，例如: /figantixray blockthreshold 32 \"minecraft:diamond_ore\"").formatted(Formatting.GRAY));

        source.sendMessage(Text.literal("🧱 方块管理命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray addblock \"<方块ID>\" - 添加监控方块").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    例如: /figantixray addblock \"minecraft:diamond_ore\"").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("  /figantixray setblockname \"<方块ID>\" \"<自定义名称>\" - 设置方块显示名称").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    例如: /figantixray setblockname \"minecraft:diamond_ore\" \"珍贵钻石矿\"").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("  /figantixray removeblock \"<方块ID>\" - 移除监控方块").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray listblocks - 列出所有监控方块及其阈值").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("👮 OP管理命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray oprecord on - 开启OP玩家记录（默认开启）").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray oprecord off - 关闭OP玩家记录").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray oprecord - 查看当前OP记录状态").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("🎁 奖励管理命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray reduceblock <玩家名> <方块ID> <数量> <原因> - 减少玩家方块数量").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    用于工会奖励发放、活动奖励等场景").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("    例如: /figantixray reduceblock Steve minecraft:diamond_ore 5 \"工会奖励发放\"").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("  /figantixray reductionhistory <玩家名> - 查看玩家的方块减少记录历史").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("⚠️ 违规记录命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray violationhistory <玩家名> - 查看玩家的违规记录历史").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray violationtimestamps <玩家名> - 查看玩家的违规时间戳记录").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    自动记录超过阈值的玩家数据，便于审查和服务器回放查找").formatted(Formatting.GRAY));

        source.sendMessage(Text.literal("🗑️ 数据清理命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray deleteplayer <玩家名> <密码> - 删除指定玩家的所有数据").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    例如: /figantixray deleteplayer Steve my_password").formatted(Formatting.GRAY));
        source.sendMessage(Text.literal("  /figantixray deleteblockdata \"<方块ID>\" <密码> - 删除指定方块的所有历史数据").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    例如: /figantixray deleteblockdata \"minecraft:diamond_ore\" my_password").formatted(Formatting.GRAY));

        source.sendMessage(Text.literal("🔐 安全设置命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray changepassword <旧密码> <新密码> - 修改删除操作的密码").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("    默认密码: default_password_123").formatted(Formatting.GRAY));

        source.sendMessage(Text.literal("🎯 便捷功能:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  • 玩家名称自动补全 - 输入玩家名时按Tab键自动补全").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 方块ID自动补全 - 输入方块ID时按Tab键自动补全并添加引号").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 智能建议 - 只显示有数据的玩家和已监控的方块").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 实时更新 - 新玩家和方块数据立即反映在补全中").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("📖 帮助命令:").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("  /figantixray help - 显示此详细帮助信息").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  /figantixray - 显示快速命令列表").formatted(Formatting.WHITE));

        source.sendMessage(Text.literal("⚠️ 重要注意事项:").formatted(Formatting.RED));
        source.sendMessage(Text.literal("  • 所有包含冒号的方块ID必须用引号包裹").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("  • 删除操作需要密码验证，请妥善保管密码").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("  • 所有命令需要OP权限（权限等级2）").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("  • 数据会自动保存，服务器关闭时也会保存").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("  • 违规数据自动存储在 config/figantixray/data/violations/ 目录").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("  • 时间戳记录方便服务器回放查找违规行为").formatted(Formatting.YELLOW));

        source.sendMessage(Text.literal("💡 使用技巧:").formatted(Formatting.GREEN));
        source.sendMessage(Text.literal("  • 初始设置: 先修改密码，然后添加需要监控的方块").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 日常监控: 定期使用 status 和 check 命令查看状态").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 精细调整: 为稀有方块设置较低的阈值").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 奖励管理: 使用 reduceblock 命令处理工会奖励").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 违规审查: 使用 violationhistory 查看玩家违规记录").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 回放定位: 使用 violationtimestamps 获取服务器回放时间戳").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 便捷操作: 使用Tab键自动补全玩家名称和方块ID").formatted(Formatting.WHITE));
        source.sendMessage(Text.literal("  • 数据清理: 定期清理不需要的历史数据").formatted(Formatting.WHITE));

        return 1;
    }

    /**
     * 显示快速帮助信息
     */
    private static void showQuickHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("=== Figanti反透视模组命令帮助 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("/figantixray status - 查看模组状态").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray threshold <数量> - 设置全局警告阈值").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray blockthreshold <数量> \"<方块ID>\" - 设置特定方块警告阈值").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray addblock \"<方块ID>\" - 添加监控方块").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray setblockname \"<方块ID>\" \"<自定义名称>\" - 设置方块自定义名称").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray removeblock \"<方块ID>\" - 移除监控方块").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray listblocks - 列出所有监控方块").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray check - 检查所有玩家").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray check <玩家名> - 检查特定玩家").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray oprecord <on|off> - 开关OP玩家记录").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray reduceblock <玩家名> <方块ID> <数量> <原因> - 减少玩家方块数量").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray reductionhistory <玩家名> - 查看减少记录历史").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray violationhistory <玩家名> - 查看违规记录历史").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray violationtimestamps <玩家名> - 查看违规时间戳记录").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray deleteplayer <玩家名> <密码> - 删除玩家数据").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray deleteblockdata \"<方块ID>\" <密码> - 删除方块历史数据").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray changepassword <旧密码> <新密码> - 修改删除密码").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("/figantixray help - 显示详细帮助信息").formatted(Formatting.YELLOW));
        source.sendMessage(Text.literal("💡 便捷功能: 输入玩家名时按Tab键自动补全，输入方块ID时按Tab键自动补全并添加引号").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("注意: 所有带冒号的方块ID都需要用引号包裹").formatted(Formatting.RED));
        source.sendMessage(Text.literal("输入 /figantixray help 查看详细使用说明").formatted(Formatting.AQUA));
    }

    /**
     * 显示玩家违规记录历史
     */
    private static int showViolationHistory(CommandContext<ServerCommandSource> context, String playerName) {
        ServerCommandSource source = context.getSource();

        List<File> violationFiles = PlayerDataManager.getPlayerViolationFiles(playerName);

        if (violationFiles.isEmpty()) {
            source.sendMessage(Text.literal("玩家 " + playerName + " 没有违规记录").formatted(Formatting.GREEN));
            return 0;
        }

        source.sendMessage(Text.literal("=== " + playerName + " 的违规记录历史 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("总计违规记录: " + violationFiles.size() + " 条").formatted(Formatting.AQUA));

        // 显示最近的10条记录
        for (int i = 0; i < Math.min(violationFiles.size(), 10); i++) {
            File violationFile = violationFiles.get(i);
            Map<String, Object> violationData = PlayerDataManager.readViolationFile(violationFile);

            source.sendMessage(Text.literal("--- 违规记录 #" + (i + 1) + " ---").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("记录时间: " + violationData.get("记录时间")).formatted(Formatting.WHITE));

            @SuppressWarnings("unchecked")
            Map<String, Object> thresholdInfo = (Map<String, Object>) violationData.get("阈值信息");
            if (thresholdInfo != null) {
                boolean exceedsGlobal = Boolean.TRUE.equals(thresholdInfo.get("超过全局阈值"));
                source.sendMessage(Text.literal("超过全局阈值: " + (exceedsGlobal ? "是" : "否")).formatted(
                        exceedsGlobal ? Formatting.RED : Formatting.GREEN
                ));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> exceededBlocks = (Map<String, Object>) violationData.get("超过阈值的方块");
            if (exceededBlocks != null && !exceededBlocks.isEmpty()) {
                source.sendMessage(Text.literal("超过阈值的方块:").formatted(Formatting.RED));
                for (Map.Entry<String, Object> entry : exceededBlocks.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> blockInfo = (Map<String, Object>) entry.getValue();
                    source.sendMessage(Text.literal("  - " + blockInfo.get("方块名称") + ": " +
                            blockInfo.get("当前数量") + " 个 (阈值: " + blockInfo.get("方块阈值") + ")").formatted(Formatting.WHITE));
                }
            }

            Object totalMonitored = violationData.get("监控方块总数");
            if (totalMonitored != null) {
                source.sendMessage(Text.literal("监控方块总数: " + totalMonitored + " 个").formatted(Formatting.AQUA));
            }

            // 显示时间戳信息
            Object replayTimestamp = violationData.get("服务器回放时间戳");
            if (replayTimestamp != null) {
                source.sendMessage(Text.literal("服务器回放时间戳: " + replayTimestamp).formatted(Formatting.GRAY));
            }

            // 显示位置信息
            Object position = violationData.get("position_readable");
            if (position != null) {
                source.sendMessage(Text.literal("位置: " + position).formatted(Formatting.GRAY));
            }

            // 显示维度信息
            Object dimension = violationData.get("dimension");
            if (dimension != null) {
                source.sendMessage(Text.literal("维度: " + dimension).formatted(Formatting.GRAY));
            }

            source.sendMessage(Text.literal(""));
        }

        if (violationFiles.size() > 10) {
            source.sendMessage(Text.literal("... 还有 " + (violationFiles.size() - 10) + " 条更早的记录").formatted(Formatting.GRAY));
        }

        source.sendMessage(Text.literal("💡 提示: 违规数据保存在 config/figantixray/data/violations/ 目录下").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("💡 提示: 使用 /figantixray violationtimestamps " + playerName + " 查看时间戳记录").formatted(Formatting.AQUA));

        return violationFiles.size();
    }

    /**
     * 显示玩家违规时间戳记录
     */
    private static int showViolationTimestamps(CommandContext<ServerCommandSource> context, String playerName) {
        ServerCommandSource source = context.getSource();

        List<Map<String, Object>> timestampRecords = PlayerDataManager.getPlayerViolationTimestamps(playerName);

        if (timestampRecords.isEmpty()) {
            source.sendMessage(Text.literal("玩家 " + playerName + " 没有违规时间戳记录").formatted(Formatting.GREEN));
            return 0;
        }

        source.sendMessage(Text.literal("=== " + playerName + " 的违规时间戳记录 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("总计记录: " + timestampRecords.size() + " 条").formatted(Formatting.AQUA));
        source.sendMessage(Text.literal("💡 提示: 使用这些时间戳可以方便地在服务器回放中定位").formatted(Formatting.AQUA));

        // 显示最近的记录
        for (int i = timestampRecords.size() - 1; i >= Math.max(0, timestampRecords.size() - 10); i--) {
            Map<String, Object> record = timestampRecords.get(i);

            source.sendMessage(Text.literal("--- 时间戳记录 #" + (timestampRecords.size() - i) + " ---").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("可读时间: " + record.get("readable_time")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("Unix时间戳: " + record.get("unix_timestamp")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("位置: " + record.get("position")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("维度: " + record.get("dimension")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("总方块数: " + record.get("total_blocks")).formatted(Formatting.AQUA));

            @SuppressWarnings("unchecked")
            Map<String, Object> blockDetails = (Map<String, Object>) record.get("block_details");
            if (blockDetails != null && !blockDetails.isEmpty()) {
                source.sendMessage(Text.literal("方块详情:").formatted(Formatting.GRAY));
                for (Map.Entry<String, Object> entry : blockDetails.entrySet()) {
                    source.sendMessage(Text.literal("  - " + entry.getKey() + ": " + entry.getValue()).formatted(Formatting.WHITE));
                }
            }

            source.sendMessage(Text.literal(""));
        }

        if (timestampRecords.size() > 10) {
            source.sendMessage(Text.literal("... 还有 " + (timestampRecords.size() - 10) + " 条更早的记录").formatted(Formatting.GRAY));
        }

        source.sendMessage(Text.literal("💡 提示: 时间戳记录保存在玩家数据目录下的 violation_timestamps.json").formatted(Formatting.AQUA));

        return timestampRecords.size();
    }

    /**
     * 减少玩家方块数量方法
     */
    private static int reducePlayerBlock(CommandContext<ServerCommandSource> context, String playerName,
                                         String blockId, int amount, String reason) {
        ServerCommandSource source = context.getSource();

        if (!isValidBlockId(blockId)) {
            source.sendMessage(Text.literal("错误: 方块ID格式不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("方块ID应该是 '命名空间:方块名' 格式，例如 'minecraft:diamond_ore'").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("请使用引号包裹方块ID: /figantixray reduceblock " + playerName + " \"minecraft:diamond_ore\" " + amount + " \"" + reason + "\"").formatted(Formatting.RED));
            return 0;
        }

        if (amount <= 0) {
            source.sendMessage(Text.literal("错误: 减少数量必须大于0").formatted(Formatting.RED));
            return 0;
        }

        if (reason == null || reason.trim().isEmpty()) {
            source.sendMessage(Text.literal("错误: 必须提供减少原因").formatted(Formatting.RED));
            source.sendMessage(Text.literal("例如: \"工会奖励发放\"、\"活动奖励\"、\"数据修正\"等").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            boolean success = PlayerDataManager.reducePlayerBlockData(playerName, blockId, amount, reason.trim());

            if (success) {
                String displayName = ConfigManager.getBlockDisplayName(blockId);
                source.sendMessage(Text.literal("✅ 已成功减少玩家 " + playerName + " 的 " + displayName + " 数量 " + amount + " 个").formatted(Formatting.GREEN));
                source.sendMessage(Text.literal("原因: " + reason).formatted(Formatting.GRAY));

                // 显示玩家当前数据
                PlayerDataManager.PlayerMiningData data = PlayerDataManager.getPlayerDataByName(playerName);
                if (data != null) {
                    int currentCount = data.blockCounts.getOrDefault(blockId, 0);
                    source.sendMessage(Text.literal("当前 " + displayName + " 数量: " + currentCount + " 个").formatted(Formatting.AQUA));
                }
            } else {
                source.sendMessage(Text.literal("错误: 无法减少玩家 " + playerName + " 的方块 " + blockId + " 数量").formatted(Formatting.RED));
                source.sendMessage(Text.literal("可能原因: 玩家不存在、方块数据不存在或数量不足").formatted(Formatting.YELLOW));
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            source.sendMessage(Text.literal("减少玩家方块数量失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    /**
     * 显示玩家减少记录历史
     */
    private static int showReductionHistory(CommandContext<ServerCommandSource> context, String playerName) {
        ServerCommandSource source = context.getSource();

        List<Map<String, Object>> records = PlayerDataManager.getPlayerReductionRecords(playerName);

        if (records.isEmpty()) {
            source.sendMessage(Text.literal("玩家 " + playerName + " 没有方块减少记录").formatted(Formatting.GREEN));
            return 0;
        }

        source.sendMessage(Text.literal("=== " + playerName + " 的方块减少记录历史 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("总计记录: " + records.size() + " 条").formatted(Formatting.AQUA));

        // 按时间倒序显示最近的记录
        for (int i = records.size() - 1; i >= Math.max(0, records.size() - 10); i--) {
            Map<String, Object> record = records.get(i);

            source.sendMessage(Text.literal("--- 记录 #" + (i + 1) + " ---").formatted(Formatting.YELLOW));
            source.sendMessage(Text.literal("时间: " + record.get("操作时间")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("方块: " + record.get("方块名称") + " (" + record.get("方块ID") + ")").formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("减少数量: " + record.get("减少数量") + " 个").formatted(Formatting.RED));
            source.sendMessage(Text.literal("变化: " + record.get("原数量") + " → " + record.get("新数量")).formatted(Formatting.WHITE));
            source.sendMessage(Text.literal("原因: " + record.get("操作原因")).formatted(Formatting.GRAY));
            source.sendMessage(Text.literal(""));
        }

        if (records.size() > 10) {
            source.sendMessage(Text.literal("... 还有 " + (records.size() - 10) + " 条更早的记录").formatted(Formatting.GRAY));
        }

        return records.size();
    }

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
        List<PlayerDataManager.PlayerMiningData> exceedingPlayers = PlayerDataManager.getPlayersExceedingThreshold();

        source.sendMessage(Text.literal("=== Figanti反透视状态 ===").formatted(Formatting.GOLD));
        source.sendMessage(Text.literal("全局警告阈值: " + ConfigManager.getThreshold() + " 个方块"));
        source.sendMessage(Text.literal("OP玩家记录: " + (ConfigManager.isOpRecordEnabled() ? "已开启" : "已关闭")).formatted(
                ConfigManager.isOpRecordEnabled() ? Formatting.GREEN : Formatting.RED
        ));

        Map<String, Integer> blockThresholds = ConfigManager.getBlockThresholds();
        if (!blockThresholds.isEmpty()) {
            source.sendMessage(Text.literal("特殊方块阈值:").formatted(Formatting.AQUA));
            for (Map.Entry<String, Integer> entry : blockThresholds.entrySet()) {
                String displayName = ConfigManager.getBlockDisplayName(entry.getKey());
                source.sendMessage(Text.literal(" - " + displayName + ": " + entry.getValue() + " 个").formatted(Formatting.WHITE));
            }
        }

        source.sendMessage(Text.literal("监控方块数量: " + ConfigManager.getMonitoredBlocks().size()));
        source.sendMessage(Text.literal("超过阈值的玩家: " + exceedingPlayers.size() + " 名"));

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
        List<PlayerDataManager.PlayerMiningData> exceedingPlayers = PlayerDataManager.getPlayersExceedingThreshold();

        if (exceedingPlayers.isEmpty()) {
            source.sendMessage(Text.literal("当前没有玩家超过警告阈值").formatted(Formatting.GREEN));
        } else {
            source.sendMessage(Text.literal("超过警告阈值的玩家:").formatted(Formatting.YELLOW));
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

    /**
     * 删除玩家数据方法
     */
    private static int deletePlayerData(CommandContext<ServerCommandSource> context, String playerName, String password) {
        ServerCommandSource source = context.getSource();

        // 验证密码
        if (!ConfigManager.verifyDeletePassword(password)) {
            source.sendMessage(Text.literal("错误: 密码不正确").formatted(Formatting.RED));
            source.sendMessage(Text.literal("请检查密码是否正确，默认密码是 'default_password_123'").formatted(Formatting.YELLOW));
            return 0;
        }

        try {
            // 检查玩家是否存在
            PlayerDataManager.PlayerMiningData playerData = PlayerDataManager.getPlayerDataByName(playerName);
            if (playerData == null) {
                source.sendMessage(Text.literal("错误: 未找到玩家 " + playerName + " 的数据").formatted(Formatting.RED));

                // 显示所有玩家列表
                List<String> playerNames = PlayerDataManager.getAllPlayerNames();
                if (!playerNames.isEmpty()) {
                    source.sendMessage(Text.literal("当前有数据的玩家:").formatted(Formatting.AQUA));
                    for (String name : playerNames) {
                        source.sendMessage(Text.literal(" - " + name).formatted(Formatting.WHITE));
                    }
                }
                return 0;
            }

            // 执行删除操作
            int deletedCount = PlayerDataManager.deletePlayerData(playerName);

            source.sendMessage(Text.literal("✅ 已成功删除玩家 " + playerName + " 的所有数据").formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("共清理了 " + deletedCount + " 条数据记录").formatted(Formatting.GREEN));
            source.sendMessage(Text.literal("注意: 此操作不可恢复，相关数据已永久删除").formatted(Formatting.RED));

            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("删除玩家数据失败: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
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