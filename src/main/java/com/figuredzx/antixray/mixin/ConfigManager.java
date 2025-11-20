package com.figuredzx.antixray.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger("FigantiXray/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static ModConfig config;

    // Block ID to Chinese name mapping
    private static final Map<String, String> BLOCK_NAMES = new HashMap<>();

    // 存储自定义方块名称
    private static final Map<String, String> CUSTOM_BLOCK_NAMES = new HashMap<>();

    // 存储方块特定阈值
    private static final Map<String, Integer> BLOCK_THRESHOLDS = new HashMap<>();

    static {
        // Initialize block name mapping
        BLOCK_NAMES.put("minecraft:gold_ore", "金矿");
        BLOCK_NAMES.put("minecraft:deepslate_gold_ore", "深层金矿");
        BLOCK_NAMES.put("minecraft:diamond_ore", "钻石矿");
        BLOCK_NAMES.put("minecraft:deepslate_diamond_ore", "深层钻石矿");
        BLOCK_NAMES.put("minecraft:emerald_ore", "绿宝石矿");
        BLOCK_NAMES.put("minecraft:deepslate_emerald_ore", "深层绿宝石矿");
        BLOCK_NAMES.put("minecraft:ancient_debris", "下界残骸");
        BLOCK_NAMES.put("minecraft:nether_gold_ore", "下界金矿");
        BLOCK_NAMES.put("minecraft:copper_ore", "铜矿");
        BLOCK_NAMES.put("minecraft:deepslate_copper_ore", "深层铜矿");
        BLOCK_NAMES.put("minecraft:iron_ore", "铁矿");
        BLOCK_NAMES.put("minecraft:deepslate_iron_ore", "深层铁矿");
        BLOCK_NAMES.put("minecraft:coal_ore", "煤矿");
        BLOCK_NAMES.put("minecraft:deepslate_coal_ore", "深层煤矿");
        BLOCK_NAMES.put("minecraft:lapis_ore", "青金石矿");
        BLOCK_NAMES.put("minecraft:deepslate_lapis_ore", "深层青金石矿");
        BLOCK_NAMES.put("minecraft:redstone_ore", "红石矿");
        BLOCK_NAMES.put("minecraft:deepslate_redstone_ore", "深层红石矿");
    }

    public static void initialize() {
        configFile = new File("config/figantixray/config.json");
        loadConfig();
    }

    private static void loadConfig() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = GSON.fromJson(reader, ModConfig.class);
                LOGGER.info("Loaded config from {}", configFile.getAbsolutePath());

                // 加载自定义名称和阈值
                if (config.customBlockNames != null) {
                    CUSTOM_BLOCK_NAMES.putAll(config.customBlockNames);
                }
                if (config.blockThresholds != null) {
                    BLOCK_THRESHOLDS.putAll(config.blockThresholds);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
                createDefaultConfig();
            }
        } else {
            createDefaultConfig();
        }
    }

    private static void createDefaultConfig() {
        config = new ModConfig();
        saveConfig();
    }

    public static void saveConfig() {
        File dir = configFile.getParentFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                LOGGER.info("Created config directory: {}", dir.getAbsolutePath());
            } else {
                LOGGER.error("Failed to create config directory: {}", dir.getAbsolutePath());
                return;
            }
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
            LOGGER.info("Saved config to {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // Getters and setters
    public static int getThreshold() {
        return config.threshold;
    }

    public static void setThreshold(int threshold) {
        config.threshold = threshold;
        saveConfig();
    }

    public static Set<String> getMonitoredBlocks() {
        return new HashSet<>(config.monitoredBlocks);
    }

    public static void addMonitoredBlock(String blockId) {
        if (!config.monitoredBlocks.contains(blockId)) {
            config.monitoredBlocks.add(blockId);
            saveConfig();
            LOGGER.info("Added monitored block: {}", blockId);
        }
    }

    public static void removeMonitoredBlock(String blockId) {
        config.monitoredBlocks.remove(blockId);
        saveConfig();
        LOGGER.info("Removed monitored block: {}", blockId);
    }

    public static boolean isBlockMonitored(Identifier blockId) {
        return config.monitoredBlocks.contains(blockId.toString());
    }

    /**
     * Get Chinese display name for block
     * @param blockId Block ID
     * @return Chinese name, or block ID if unknown
     */
    public static String getBlockDisplayName(String blockId) {
        // 优先返回自定义名称
        if (CUSTOM_BLOCK_NAMES.containsKey(blockId) && CUSTOM_BLOCK_NAMES.get(blockId) != null && !CUSTOM_BLOCK_NAMES.get(blockId).isEmpty()) {
            return CUSTOM_BLOCK_NAMES.get(blockId);
        }
        // 其次返回预设的中文名称
        return BLOCK_NAMES.getOrDefault(blockId, blockId);
    }

    /**
     * 设置方块自定义名称
     */
    public static void setBlockCustomName(String blockId, String customName) {
        if (customName != null && !customName.trim().isEmpty()) {
            CUSTOM_BLOCK_NAMES.put(blockId, customName.trim());
        } else {
            CUSTOM_BLOCK_NAMES.remove(blockId);
        }
        config.customBlockNames = new HashMap<>(CUSTOM_BLOCK_NAMES);
        saveConfig();
        LOGGER.info("Set custom name for {}: {}", blockId, customName);
    }

    /**
     * 获取方块自定义名称
     */
    public static String getBlockCustomName(String blockId) {
        return CUSTOM_BLOCK_NAMES.get(blockId);
    }

    /**
     * 设置方块特定阈值
     */
    public static void setBlockThreshold(String blockId, int threshold) {
        if (threshold > 0) {
            BLOCK_THRESHOLDS.put(blockId, threshold);
        } else {
            BLOCK_THRESHOLDS.remove(blockId);
        }
        config.blockThresholds = new HashMap<>(BLOCK_THRESHOLDS);
        saveConfig();
        LOGGER.info("Set threshold for {}: {}", blockId, threshold);
    }

    /**
     * 获取方块特定阈值
     */
    public static int getBlockThreshold(String blockId) {
        return BLOCK_THRESHOLDS.getOrDefault(blockId, getThreshold());
    }

    /**
     * 获取所有方块特定阈值
     */
    public static Map<String, Integer> getBlockThresholds() {
        return new HashMap<>(BLOCK_THRESHOLDS);
    }

    // 新增：获取删除密码
    public static String getDeletePassword() {
        return config.deletePassword;
    }

    // 新增：设置删除密码
    public static void setDeletePassword(String password) {
        config.deletePassword = password;
        saveConfig();
    }
    // 新增：验证删除密码
    public static boolean verifyDeletePassword(String inputPassword) {
        return config.deletePassword.equals(inputPassword);
    }
    // 新增：修改删除密码（需要旧密码验证）
    public static boolean changeDeletePassword(String oldPassword, String newPassword) {
        if (verifyDeletePassword(oldPassword)) {
            setDeletePassword(newPassword);
            return true;
        }
        return false;
    }
    // 新增：OP玩家记录开关
    public static boolean isOpRecordEnabled() {
        return config.recordOpPlayers;
    }
    // 新增：设置OP玩家记录开关
    public static void setOpRecordEnabled(boolean enabled) {
        config.recordOpPlayers = enabled;
        saveConfig();
        LOGGER.info("OP player recording {}", enabled ? "enabled" : "disabled");
    }

    // Config class
    private static class ModConfig {
        public int threshold = 64;
        public String deletePassword = "default_password_123"; // 默认删除密码
        public boolean recordOpPlayers = true; // 是否记录OP玩家的挖掘数据
        public List<String> monitoredBlocks = new ArrayList<>(Arrays.asList(
                "minecraft:gold_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:emerald_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:ancient_debris"
        ));
        public Map<String, String> customBlockNames = new HashMap<>();
        public Map<String, Integer> blockThresholds = new HashMap<>();
    }
}