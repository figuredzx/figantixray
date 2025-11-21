package com.figuredzx.antixray.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private static final Logger LOGGER = LogManager.getLogger("FigantiXray/DataManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private static final Map<UUID, PlayerMiningData> playerData = new ConcurrentHashMap<>();
    private static File dataDirectory;
    private static File playersDirectory;
    private static File violationsDirectory; // 违规玩家数据目录

    public static void initialize() {
        dataDirectory = new File("config/figantixray/data");
        playersDirectory = new File(dataDirectory, "players");
        violationsDirectory = new File(dataDirectory, "violations"); // 新增违规数据目录
        ensureDataDirectoryExists();
        ensureViolationsDirectoryExists(); // 确保违规目录存在
        loadQuickAccessData();
    }

    /**
     * 确保违规数据目录存在
     */
    private static void ensureViolationsDirectoryExists() {
        if (!violationsDirectory.exists()) {
            boolean created = violationsDirectory.mkdirs();
            if (created) {
                LOGGER.info("Created violations directory: {}", violationsDirectory.getAbsolutePath());
            } else {
                LOGGER.error("Failed to create violations directory: {}", violationsDirectory.getAbsolutePath());
            }
        }
    }

    public static void recordBlockBreak(ServerPlayerEntity player, String blockId, BlockPos pos, World world) {
        // 检查该方块是否在当前监控列表中
        if (!ConfigManager.getMonitoredBlocks().contains(blockId)) {
            return; // 如果不是当前监控的方块，不记录
        }

        UUID playerId = player.getUuid();
        String playerName = player.getGameProfile().getName();
        String date = DATE_FORMAT.format(new Date());
        String currentTime = TIME_FORMAT.format(new Date());
        long unixTimestamp = System.currentTimeMillis(); // 获取Unix时间戳

        PlayerMiningData data = playerData.computeIfAbsent(playerId,
                k -> new PlayerMiningData(playerId));

        data.incrementBlockCount(blockId);
        data.setPlayerName(playerName);
        data.setLastMiningTime(currentTime);
        data.setLastActiveDate(date);

        // 记录最后一次挖掘的位置和时间戳
        data.setLastMiningPosition(pos);
        data.setLastMiningDimension(world.getRegistryKey().getValue().toString());
        data.setLastMiningTimestamp(unixTimestamp);

        // 检查是否超过阈值，如果超过则保存违规数据
        checkAndSaveViolationData(playerName, data, pos, world);

        // Save to quick access file
        saveQuickAccessData();

        // Save to player categorized files
        savePlayerDateData(playerName, date, data);
        updatePlayerSummary(playerName, data);
    }

    /**
     * 检查并保存违规玩家数据
     */
    private static void checkAndSaveViolationData(String playerName, PlayerMiningData data, BlockPos pos, World world) {
        try {
            // 计算当前监控方块的总数
            int currentTotal = calculateCurrentMonitoredBlocks(data);
            int globalThreshold = ConfigManager.getThreshold();

            // 检查是否超过全局阈值
            boolean exceedsGlobalThreshold = currentTotal >= globalThreshold;

            // 检查是否有单个方块超过其特定阈值
            boolean exceedsBlockThreshold = false;
            Map<String, String> exceededBlocks = new HashMap<>();

            for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
                String blockId = entry.getKey();
                int count = entry.getValue();
                int blockThreshold = ConfigManager.getBlockThreshold(blockId);

                if (count >= blockThreshold) {
                    exceedsBlockThreshold = true;
                    exceededBlocks.put(blockId, ConfigManager.getBlockDisplayName(blockId));
                }
            }

            // 如果超过任一阈值，保存违规数据
            if (exceedsGlobalThreshold || exceedsBlockThreshold) {
                savePlayerViolationData(playerName, data, exceedsGlobalThreshold, exceededBlocks, globalThreshold, pos, world);

                // 同时更新玩家的违规时间戳记录
                updatePlayerViolationTimestamp(playerName, data);
            }

        } catch (Exception e) {
            LOGGER.error("检查违规数据时出错: {}", playerName, e);
        }
    }

    /**
     * 更新玩家的违规时间戳记录
     */
    private static void updatePlayerViolationTimestamp(String playerName, PlayerMiningData data) {
        File playerDir = new File(playersDirectory, cleanFileName(playerName));
        if (!playerDir.exists() && !playerDir.mkdirs()) {
            LOGGER.error("Failed to create player directory: {}", playerDir.getAbsolutePath());
            return;
        }

        File timestampFile = new File(playerDir, "violation_timestamps.json");

        // 读取现有的时间戳记录
        List<Map<String, Object>> timestampRecords = new ArrayList<>();
        if (timestampFile.exists()) {
            try (FileReader reader = new FileReader(timestampFile)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> loadedRecords = GSON.fromJson(reader, List.class);
                if (loadedRecords != null) {
                    timestampRecords = loadedRecords;
                }
            } catch (IOException e) {
                LOGGER.warn("无法读取玩家 {} 的违规时间戳记录", playerName, e);
            }
        }

        // 添加新的时间戳记录
        Map<String, Object> newRecord = new HashMap<>();
        newRecord.put("timestamp", data.lastMiningTimestamp);
        newRecord.put("readable_time", TIME_FORMAT.format(new Date(data.lastMiningTimestamp)));
        newRecord.put("unix_timestamp", data.lastMiningTimestamp);
        newRecord.put("total_blocks", data.getTotalMonitoredBlocks());
        newRecord.put("position", data.lastMiningPosition != null ?
                String.format("(%d, %d, %d)", data.lastMiningPosition.getX(), data.lastMiningPosition.getY(), data.lastMiningPosition.getZ()) : "未知");
        newRecord.put("dimension", data.lastMiningDimension != null ? data.lastMiningDimension : "未知");

        // 添加方块详情
        Map<String, Object> blockDetails = new HashMap<>();
        Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();
        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            if (monitoredBlocks.contains(entry.getKey())) {
                String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                int threshold = ConfigManager.getBlockThreshold(entry.getKey());
                blockDetails.put(blockName, String.format("%d/%d", entry.getValue(), threshold));
            }
        }
        newRecord.put("block_details", blockDetails);

        timestampRecords.add(newRecord);

        // 只保留最近100条记录，避免文件过大
        if (timestampRecords.size() > 100) {
            timestampRecords = timestampRecords.subList(timestampRecords.size() - 100, timestampRecords.size());
        }

        // 保存时间戳记录
        try (FileWriter writer = new FileWriter(timestampFile)) {
            GSON.toJson(timestampRecords, writer);
        } catch (IOException e) {
            LOGGER.error("无法保存玩家 {} 的违规时间戳记录", playerName, e);
        }
    }

    /**
     * 保存玩家违规数据到单独文件
     */
    private static void savePlayerViolationData(String playerName, PlayerMiningData data,
                                                boolean exceedsGlobalThreshold,
                                                Map<String, String> exceededBlocks,
                                                int globalThreshold, BlockPos pos, World world) {
        File playerViolationDir = new File(violationsDirectory, cleanFileName(playerName));
        if (!playerViolationDir.exists() && !playerViolationDir.mkdirs()) {
            LOGGER.error("Failed to create player violation directory: {}", playerViolationDir.getAbsolutePath());
            return;
        }

        // 使用时间戳作为文件名，避免覆盖
        String timestamp = TIMESTAMP_FORMAT.format(new Date());
        File violationFile = new File(playerViolationDir, "violation_" + timestamp + ".json");

        // 创建违规数据记录
        Map<String, Object> violationData = new HashMap<>();
        violationData.put("记录时间", TIME_FORMAT.format(new Date()));
        violationData.put("玩家名称", playerName);
        violationData.put("玩家UUID", data.playerId != null ? data.playerId.toString() : "未知");

        // 添加时间戳信息 - 方便服务器回放查找
        violationData.put("unix_timestamp", System.currentTimeMillis());
        violationData.put("timestamp_readable", TIME_FORMAT.format(new Date()));
        violationData.put("server_replay_timestamp", data.lastMiningTimestamp);

        // 添加位置信息
        if (pos != null) {
            violationData.put("position_x", pos.getX());
            violationData.put("position_y", pos.getY());
            violationData.put("position_z", pos.getZ());
            violationData.put("position_readable", String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()));
        }

        // 添加维度信息
        if (world != null) {
            violationData.put("dimension", world.getRegistryKey().getValue().toString());
        }

        // 添加阈值信息
        Map<String, Object> thresholdInfo = new HashMap<>();
        thresholdInfo.put("全局阈值", globalThreshold);
        thresholdInfo.put("超过全局阈值", exceedsGlobalThreshold);
        thresholdInfo.put("玩家当前总数", data.getTotalMonitoredBlocks());
        violationData.put("阈值信息", thresholdInfo);

        // 添加超过阈值的方块信息
        if (!exceededBlocks.isEmpty()) {
            Map<String, Object> exceededBlocksInfo = new HashMap<>();
            for (Map.Entry<String, String> entry : exceededBlocks.entrySet()) {
                String blockId = entry.getKey();
                String blockName = entry.getValue();
                int count = data.blockCounts.getOrDefault(blockId, 0);
                int blockThreshold = ConfigManager.getBlockThreshold(blockId);

                Map<String, Object> blockInfo = new HashMap<>();
                blockInfo.put("方块名称", blockName);
                blockInfo.put("当前数量", count);
                blockInfo.put("方块阈值", blockThreshold);
                blockInfo.put("超过数量", count - blockThreshold);

                exceededBlocksInfo.put(blockId, blockInfo);
            }
            violationData.put("超过阈值的方块", exceededBlocksInfo);
        }

        // 添加所有监控方块的数据
        Map<String, Object> allBlocksData = new HashMap<>();
        Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();
        int totalMonitored = 0;

        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            if (monitoredBlocks.contains(entry.getKey())) {
                String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                int count = entry.getValue();
                int blockThreshold = ConfigManager.getBlockThreshold(entry.getKey());

                Map<String, Object> blockData = new HashMap<>();
                blockData.put("显示名称", blockName);
                blockData.put("挖掘数量", count);
                blockData.put("方块阈值", blockThreshold);
                blockData.put("是否超过", count >= blockThreshold);

                allBlocksData.put(entry.getKey(), blockData);
                totalMonitored += count;
            }
        }

        violationData.put("所有监控方块数据", allBlocksData);
        violationData.put("监控方块总数", totalMonitored);
        violationData.put("最后活跃时间", data.lastActiveDate);
        violationData.put("最后挖掘时间", data.lastMiningTime);
        violationData.put("服务器回放时间戳", data.lastMiningTimestamp);

        // 保存到文件
        try (FileWriter writer = new FileWriter(violationFile)) {
            GSON.toJson(violationData, writer);
            LOGGER.info("已保存玩家 {} 的违规数据到: {}", playerName, violationFile.getName());
        } catch (IOException e) {
            LOGGER.error("保存玩家违规数据失败: {}", playerName, e);
        }
    }

    /**
     * 获取玩家的违规记录文件列表
     */
    public static List<File> getPlayerViolationFiles(String playerName) {
        File playerViolationDir = new File(violationsDirectory, cleanFileName(playerName));
        if (!playerViolationDir.exists()) {
            return new ArrayList<>();
        }

        File[] files = playerViolationDir.listFiles((dir, name) -> name.startsWith("violation_") && name.endsWith(".json"));
        if (files == null) {
            return new ArrayList<>();
        }

        List<File> violationFiles = new ArrayList<>(Arrays.asList(files));
        // 按时间倒序排列
        violationFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        return violationFiles;
    }

    /**
     * 读取违规记录文件内容
     */
    public static Map<String, Object> readViolationFile(File violationFile) {
        if (!violationFile.exists()) {
            return new HashMap<>();
        }

        try (FileReader reader = new FileReader(violationFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(reader, Map.class);
            return data != null ? data : new HashMap<>();
        } catch (IOException e) {
            LOGGER.error("读取违规记录文件失败: {}", violationFile.getName(), e);
            return new HashMap<>();
        }
    }

    /**
     * 获取玩家的违规时间戳记录
     */
    public static List<Map<String, Object>> getPlayerViolationTimestamps(String playerName) {
        File playerDir = new File(playersDirectory, cleanFileName(playerName));
        File timestampFile = new File(playerDir, "violation_timestamps.json");

        if (!timestampFile.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(timestampFile)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = GSON.fromJson(reader, List.class);
            return records != null ? records : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.warn("无法读取玩家 {} 的违规时间戳记录", playerName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取超过阈值的玩家列表（只计算当前监控的方块）
     */
    public static List<PlayerMiningData> getPlayersExceedingThreshold() {
        int threshold = ConfigManager.getThreshold();
        List<PlayerMiningData> exceeding = new ArrayList<>();

        for (PlayerMiningData data : playerData.values()) {
            // 只计算当前监控的方块
            int currentTotal = calculateCurrentMonitoredBlocks(data);
            if (currentTotal >= threshold) {
                exceeding.add(data);
            }
        }

        // Sort by count descending
        exceeding.sort((a, b) -> Integer.compare(
                calculateCurrentMonitoredBlocks(b),
                calculateCurrentMonitoredBlocks(a)
        ));
        return exceeding;
    }

    /**
     * 计算玩家在当前监控方块上的总挖掘数量
     */
    private static int calculateCurrentMonitoredBlocks(PlayerMiningData data) {
        if (data == null || data.blockCounts == null) {
            return 0;
        }

        Set<String> currentMonitoredBlocks = ConfigManager.getMonitoredBlocks();
        int total = 0;

        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            if (currentMonitoredBlocks.contains(entry.getKey())) {
                total += entry.getValue();
            }
        }

        return total;
    }

    public static PlayerMiningData getPlayerDataByName(String playerName) {
        for (PlayerMiningData data : playerData.values()) {
            if (data.playerName != null && data.playerName.equals(playerName)) {
                return data;
            }
        }
        return null;
    }

    /**
     * 获取玩家的当前监控方块数据（过滤掉已移除的方块）
     */
    public static Map<String, Integer> getPlayerCurrentBlocks(String playerName) {
        PlayerMiningData data = getPlayerDataByName(playerName);
        if (data == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> currentBlocks = new HashMap<>();
        Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();

        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            if (monitoredBlocks.contains(entry.getKey())) {
                currentBlocks.put(entry.getKey(), entry.getValue());
            }
        }

        return currentBlocks;
    }

    /**
     * 减少玩家特定方块的数量（用于奖励扣除等）
     */
    public static boolean reducePlayerBlockData(String playerName, String blockId, int amount, String reason) {
        PlayerMiningData data = getPlayerDataByName(playerName);
        if (data == null) {
            LOGGER.warn("无法找到玩家 {} 的数据", playerName);
            return false;
        }

        if (!data.blockCounts.containsKey(blockId)) {
            LOGGER.warn("玩家 {} 没有方块 {} 的挖掘记录", playerName, blockId);
            return false;
        }

        int currentCount = data.blockCounts.get(blockId);
        if (currentCount < amount) {
            LOGGER.warn("玩家 {} 的方块 {} 数量不足，当前: {}，需要减少: {}",
                    playerName, blockId, currentCount, amount);
            return false;
        }

        // 减少数量
        int newCount = currentCount - amount;
        data.blockCounts.put(blockId, newCount);

        // 记录操作日志
        String operationTime = TIME_FORMAT.format(new Date());
        LOGGER.info("减少玩家 {} 的方块 {} 数量: {} -> {} (原因: {})",
                playerName, blockId, currentCount, newCount, reason);

        // 保存操作记录
        saveReductionRecord(playerName, blockId, amount, currentCount, newCount, reason, operationTime);

        // 更新快速访问文件
        saveQuickAccessData();

        return true;
    }

    /**
     * 保存减少操作的记录
     */
    private static void saveReductionRecord(String playerName, String blockId, int reducedAmount,
                                            int oldCount, int newCount, String reason, String operationTime) {
        File reductionDir = new File(dataDirectory, "reduction_records");
        if (!reductionDir.exists() && !reductionDir.mkdirs()) {
            LOGGER.error("无法创建减少记录目录: {}", reductionDir.getAbsolutePath());
            return;
        }

        File playerRecordFile = new File(reductionDir, cleanFileName(playerName) + ".json");

        // 读取现有记录
        List<Map<String, Object>> records = new ArrayList<>();
        if (playerRecordFile.exists()) {
            try (FileReader reader = new FileReader(playerRecordFile)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> loadedRecords = GSON.fromJson(reader, List.class);
                if (loadedRecords != null) {
                    records = loadedRecords;
                }
            } catch (IOException e) {
                LOGGER.warn("无法读取玩家 {} 的减少记录文件", playerName, e);
            }
        }

        // 添加新记录
        Map<String, Object> newRecord = new HashMap<>();
        newRecord.put("操作时间", operationTime);
        newRecord.put("方块ID", blockId);
        newRecord.put("方块名称", ConfigManager.getBlockDisplayName(blockId));
        newRecord.put("减少数量", reducedAmount);
        newRecord.put("原数量", oldCount);
        newRecord.put("新数量", newCount);
        newRecord.put("操作原因", reason);

        records.add(newRecord);

        // 保存记录
        try (FileWriter writer = new FileWriter(playerRecordFile)) {
            GSON.toJson(records, writer);
        } catch (IOException e) {
            LOGGER.error("无法保存玩家 {} 的减少记录", playerName, e);
        }
    }

    /**
     * 获取玩家的减少操作记录
     */
    public static List<Map<String, Object>> getPlayerReductionRecords(String playerName) {
        File reductionDir = new File(dataDirectory, "reduction_records");
        File playerRecordFile = new File(reductionDir, cleanFileName(playerName) + ".json");

        if (!playerRecordFile.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(playerRecordFile)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = GSON.fromJson(reader, List.class);
            return records != null ? records : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.warn("无法读取玩家 {} 的减少记录", playerName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有玩家的名称列表
     */
    public static List<String> getAllPlayerNames() {
        List<String> playerNames = new ArrayList<>();
        for (PlayerMiningData data : playerData.values()) {
            if (data.playerName != null && !data.playerName.isEmpty()) {
                playerNames.add(data.playerName);
            }
        }
        return playerNames;
    }

    @SuppressWarnings("unchecked")
    private static void loadQuickAccessData() {
        File quickAccessFile = new File(dataDirectory, "quick_access.json");
        if (quickAccessFile.exists()) {
            try (FileReader reader = new FileReader(quickAccessFile)) {
                Map<String, Object> quickDataMap = GSON.fromJson(reader, Map.class);
                if (quickDataMap != null && quickDataMap.containsKey("玩家列表")) {
                    Object playerListObj = quickDataMap.get("玩家列表");
                    if (playerListObj instanceof Map) {
                        Map<String, Object> playerList = (Map<String, Object>) playerListObj;
                        for (Map.Entry<String, Object> entry : playerList.entrySet()) {
                            try {
                                UUID playerId = UUID.fromString(entry.getKey());
                                Object playerDataObj = entry.getValue();

                                if (playerDataObj instanceof Map) {
                                    Map<String, Object> playerDataMap = (Map<String, Object>) playerDataObj;

                                    PlayerMiningData data = new PlayerMiningData(playerId);
                                    data.playerName = safeGetString(playerDataMap, "玩家名称");
                                    data.lastMiningTime = safeGetString(playerDataMap, "最后挖掘时间");
                                    data.lastActiveDate = safeGetString(playerDataMap, "最后活跃日期");

                                    // Convert block counts
                                    Object blockCountsObj = playerDataMap.get("方块计数");
                                    if (blockCountsObj instanceof Map) {
                                        Map<String, Object> blockCountsMap = (Map<String, Object>) blockCountsObj;
                                        for (Map.Entry<String, Object> blockEntry : blockCountsMap.entrySet()) {
                                            if (blockEntry.getValue() instanceof Number) {
                                                data.blockCounts.put(blockEntry.getKey(), ((Number) blockEntry.getValue()).intValue());
                                            }
                                        }
                                    }

                                    playerData.put(playerId, data);
                                }
                            } catch (IllegalArgumentException e) {
                                LOGGER.warn("Invalid UUID in quick access data: {}", entry.getKey());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load quick access data", e);
            }
        }
    }

    private static String safeGetString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    private static void saveQuickAccessData() {
        File quickAccessFile = new File(dataDirectory, "quick_access.json");

        if (!ensureDataDirectoryExists()) {
            LOGGER.error("Cannot save quick access data - data directory does not exist");
            return;
        }

        // Create a map with Chinese keys for JSON output
        Map<String, Object> chineseData = new HashMap<>();
        chineseData.put("最后更新", TIME_FORMAT.format(new Date()));

        Map<String, Object> playerList = new HashMap<>();
        for (Map.Entry<UUID, PlayerMiningData> entry : playerData.entrySet()) {
            Map<String, Object> playerDataMap = new HashMap<>();
            playerDataMap.put("玩家名称", entry.getValue().playerName);
            playerDataMap.put("方块计数", entry.getValue().blockCounts);
            playerDataMap.put("最后挖掘时间", entry.getValue().lastMiningTime);
            playerDataMap.put("最后活跃日期", entry.getValue().lastActiveDate);

            playerList.put(entry.getKey().toString(), playerDataMap);
        }
        chineseData.put("玩家列表", playerList);

        try (FileWriter writer = new FileWriter(quickAccessFile)) {
            GSON.toJson(chineseData, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save quick access data", e);
        }
    }

    private static void savePlayerDateData(String playerName, String date, PlayerMiningData data) {
        File playerDir = new File(playersDirectory, cleanFileName(playerName));
        if (!playerDir.exists() && !playerDir.mkdirs()) {
            LOGGER.error("Failed to create player directory: {}", playerDir.getAbsolutePath());
            return;
        }

        File dateFile = new File(playerDir, date + ".json");

        // Create map with Chinese keys
        Map<String, Object> dateData = new HashMap<>();
        dateData.put("日期", date);

        // Convert block IDs to Chinese names,只显示当前监控的方块
        Map<String, Integer> miningRecords = new HashMap<>();
        Set<String> monitoredBlocks = ConfigManager.getMonitoredBlocks();

        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            if (monitoredBlocks.contains(entry.getKey())) {
                String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
                miningRecords.put(blockName, entry.getValue());
            }
        }

        dateData.put("挖掘记录", miningRecords);
        dateData.put("当日总计", calculateCurrentMonitoredBlocks(data));

        try (FileWriter writer = new FileWriter(dateFile)) {
            GSON.toJson(dateData, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save player date data: {} {}", playerName, date, e);
        }
    }

    private static void updatePlayerSummary(String playerName, PlayerMiningData data) {
        File playerDir = new File(playersDirectory, cleanFileName(playerName));
        if (!playerDir.exists() && !playerDir.mkdirs()) {
            LOGGER.error("Failed to create player directory: {}", playerDir.getAbsolutePath());
            return;
        }

        File summaryFile = new File(playerDir, "summary.json");

        // Create map with Chinese keys
        Map<String, Object> summary = new HashMap<>();
        summary.put("玩家名称", playerName);

        // 只计算当前监控的方块总数
        int currentTotal = calculateCurrentMonitoredBlocks(data);
        summary.put("总挖掘方块", currentTotal);
        summary.put("最后挖掘", data.lastMiningTime);

        // 获取最常挖掘的当前监控方块
        Map<String, Integer> currentBlocks = getPlayerCurrentBlocks(playerName);
        if (!currentBlocks.isEmpty()) {
            Optional<Map.Entry<String, Integer>> mostMinedOpt = currentBlocks.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue());

            if (mostMinedOpt.isPresent()) {
                Map.Entry<String, Integer> mostMined = mostMinedOpt.get();
                summary.put("最常挖掘", ConfigManager.getBlockDisplayName(mostMined.getKey()));
                summary.put("最常挖掘数量", mostMined.getValue());
            }
        }

        // Calculate days exceeding threshold based on current blocks
        summary.put("超过阈值天数", currentTotal >= ConfigManager.getThreshold() ? 1 : 0);

        try (FileWriter writer = new FileWriter(summaryFile)) {
            GSON.toJson(summary, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to update player summary: {}", playerName, e);
        }
    }

    private static String cleanFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean ensureDataDirectoryExists() {
        if (dataDirectory.exists()) {
            return true;
        }

        boolean created = dataDirectory.mkdirs();
        if (created) {
            LOGGER.info("Created data directory: {}", dataDirectory.getAbsolutePath());
        } else {
            LOGGER.error("Failed to create data directory: {}", dataDirectory.getAbsolutePath());
        }

        return created;
    }

    public static void saveAllData() {
        saveQuickAccessData();
        String currentDate = DATE_FORMAT.format(new Date());

        // Save all players' daily data
        for (Map.Entry<UUID, PlayerMiningData> entry : playerData.entrySet()) {
            if (entry.getValue().playerName != null) {
                savePlayerDateData(entry.getValue().playerName, currentDate, entry.getValue());
                updatePlayerSummary(entry.getValue().playerName, entry.getValue());
            }
        }
    }

    /**
     * 删除方块历史数据
     */
    public static int deleteBlockHistoryData(String blockId) {
        int deletedCount = 0;

        // 从内存数据中删除该方块的所有记录
        for (PlayerMiningData data : playerData.values()) {
            if (data.blockCounts.remove(blockId) != null) {
                deletedCount++;
            }
        }

        // 更新快速访问文件
        saveQuickAccessData();

        LOGGER.info("Deleted block history data for: {}, removed {} entries", blockId, deletedCount);
        return deletedCount;
    }

    /**
     * 删除玩家数据
     */
    public static int deletePlayerData(String playerName) {
        int deletedCount = 0;

        // 从内存数据中删除
        Iterator<Map.Entry<UUID, PlayerMiningData>> iterator = playerData.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerMiningData> entry = iterator.next();
            if (entry.getValue().playerName != null && entry.getValue().playerName.equals(playerName)) {
                iterator.remove();
                deletedCount++;
                break;
            }
        }

        // 删除玩家数据文件
        File playerDir = new File(playersDirectory, cleanFileName(playerName));
        if (playerDir.exists()) {
            File[] files = playerDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
            // 删除玩家目录
            if (playerDir.delete()) {
                LOGGER.info("Deleted player directory: {}", playerName);
            }
        }

        // 删除违规数据
        File violationDir = new File(violationsDirectory, cleanFileName(playerName));
        if (violationDir.exists()) {
            File[] files = violationDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
            // 删除违规目录
            if (violationDir.delete()) {
                LOGGER.info("Deleted player violation directory: {}", playerName);
            }
        }

        // 更新快速访问文件
        saveQuickAccessData();

        LOGGER.info("Deleted all data for player: {}, total {} entries", playerName, deletedCount);
        return deletedCount;
    }

    // Data class
    public static class PlayerMiningData {
        public UUID playerId;
        public String playerName;
        public Map<String, Integer> blockCounts = new HashMap<>();
        public String lastMiningTime;
        public String lastActiveDate;
        public BlockPos lastMiningPosition; // 新增：最后一次挖掘位置
        public String lastMiningDimension; // 新增：最后一次挖掘维度
        public long lastMiningTimestamp; // 新增：最后一次挖掘时间戳

        public PlayerMiningData(UUID playerId) {
            this.playerId = playerId;
        }

        public void incrementBlockCount(String blockId) {
            blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
        }

        /**
         * 获取当前监控方块的总数
         */
        public int getTotalMonitoredBlocks() {
            return calculateCurrentMonitoredBlocks(this);
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public void setLastMiningTime(String lastMiningTime) {
            this.lastMiningTime = lastMiningTime;
        }

        public void setLastActiveDate(String lastActiveDate) {
            this.lastActiveDate = lastActiveDate;
        }

        public void setLastMiningPosition(BlockPos lastMiningPosition) {
            this.lastMiningPosition = lastMiningPosition;
        }

        public void setLastMiningDimension(String lastMiningDimension) {
            this.lastMiningDimension = lastMiningDimension;
        }

        public void setLastMiningTimestamp(long lastMiningTimestamp) {
            this.lastMiningTimestamp = lastMiningTimestamp;
        }
    }
}