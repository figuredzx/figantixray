package com.figuredzx.antixray.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
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

    private static final Map<UUID, PlayerMiningData> playerData = new ConcurrentHashMap<>();
    private static File dataDirectory;
    private static File playersDirectory;

    public static void initialize() {
        dataDirectory = new File("config/figantixray/data");
        playersDirectory = new File(dataDirectory, "players");
        ensureDataDirectoryExists();
        loadQuickAccessData();
    }
// 在 PlayerDataManager 类中添加以下方法

    /**
     * 删除指定玩家的所有挖掘数据
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

        // 更新快速访问文件
        saveQuickAccessData();

        LOGGER.info("Deleted all data for player: {}, total {} entries", playerName, deletedCount);
        return deletedCount;
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
    public static void recordBlockBreak(ServerPlayerEntity player, String blockId) {
        UUID playerId = player.getUuid();
        String playerName = player.getGameProfile().getName();
        String date = DATE_FORMAT.format(new Date());
        String currentTime = TIME_FORMAT.format(new Date());

        PlayerMiningData data = playerData.computeIfAbsent(playerId,
                k -> new PlayerMiningData(playerId));

        data.incrementBlockCount(blockId);
        data.setPlayerName(playerName);
        data.setLastMiningTime(currentTime);
        data.setLastActiveDate(date);

        // Save to quick access file
        saveQuickAccessData();

        // Save to player categorized files
        savePlayerDateData(playerName, date, data);
        updatePlayerSummary(playerName, data);
    }

    /**
     * 获取超过阈值的玩家列表（考虑特定方块阈值）
     */
    public static List<PlayerMiningData> getPlayersExceedingThreshold(String date) {
        List<PlayerMiningData> exceeding = new ArrayList<>();

        for (PlayerMiningData data : playerData.values()) {
            // 检查每个方块是否超过其特定阈值
            boolean exceeds = false;
            for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
                String blockId = entry.getKey();
                int count = entry.getValue();
                int threshold = ConfigManager.getBlockThreshold(blockId);

                if (count >= threshold) {
                    exceeds = true;
                    break; // 只要有一个方块超过阈值就标记为超过
                }
            }

            if (exceeds) {
                exceeding.add(data);
            }
        }

        // Sort by count descending
        exceeding.sort((a, b) -> Integer.compare(b.getTotalMonitoredBlocks(), a.getTotalMonitoredBlocks()));
        return exceeding;
    }

    public static PlayerMiningData getPlayerData(UUID playerId) {
        return playerData.get(playerId);
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
     * 删除指定方块的所有历史数据（需要密码验证）
     * @param blockId 要删除数据的方块ID
     * @return 被删除的数据条目数量
     */
    public static int deleteBlockHistoryData(String blockId) {
        int deletedCount = 0;

        // 从内存数据中删除
        for (PlayerMiningData data : playerData.values()) {
            if (data.blockCounts.containsKey(blockId)) {
                data.blockCounts.remove(blockId);
                deletedCount++;
            }
        }

        // 保存更改到快速访问文件
        if (deletedCount > 0) {
            saveQuickAccessData();
            LOGGER.info("Deleted {} entries of block {} from player data", deletedCount, blockId);
        }

        return deletedCount;
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

        // Convert block IDs to Chinese names
        Map<String, Integer> miningRecords = new HashMap<>();
        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            String blockName = ConfigManager.getBlockDisplayName(entry.getKey());
            miningRecords.put(blockName, entry.getValue());
        }

        dateData.put("挖掘记录", miningRecords);
        dateData.put("当日总计", data.getTotalMonitoredBlocks());

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
        summary.put("总挖掘方块", data.getTotalMonitoredBlocks());
        summary.put("最后挖掘", data.lastMiningTime);

        // 安全地获取最常挖掘的方块
        if (!data.blockCounts.isEmpty()) {
            Optional<Map.Entry<String, Integer>> mostMinedOpt = data.blockCounts.entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue());

            if (mostMinedOpt.isPresent()) {
                Map.Entry<String, Integer> mostMined = mostMinedOpt.get();
                summary.put("最常挖掘", ConfigManager.getBlockDisplayName(mostMined.getKey()));
                summary.put("最常挖掘数量", mostMined.getValue());
            }
        }

        // Calculate days exceeding threshold (simplified implementation)
        // 使用新的阈值检查逻辑
        boolean exceedsThreshold = false;
        for (Map.Entry<String, Integer> entry : data.blockCounts.entrySet()) {
            String blockId = entry.getKey();
            int count = entry.getValue();
            int threshold = ConfigManager.getBlockThreshold(blockId);

            if (count >= threshold) {
                exceedsThreshold = true;
                break;
            }
        }
        summary.put("超过阈值天数", exceedsThreshold ? 1 : 0);

        try (FileWriter writer = new FileWriter(summaryFile)) {
            GSON.toJson(summary, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to update player summary: {}", playerName, e);
        }
    }

    private static String cleanFileName(String fileName) {
        // Filter illegal filename characters
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

    // Data class
    public static class PlayerMiningData {
        public UUID playerId;
        public String playerName;
        public Map<String, Integer> blockCounts = new HashMap<>();
        public String lastMiningTime;
        public String lastActiveDate;

        public PlayerMiningData(UUID playerId) {
            this.playerId = playerId;
        }

        public void incrementBlockCount(String blockId) {
            blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
        }

        public int getTotalMonitoredBlocks() {
            return blockCounts.values().stream().mapToInt(Integer::intValue).sum();
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
    }
}