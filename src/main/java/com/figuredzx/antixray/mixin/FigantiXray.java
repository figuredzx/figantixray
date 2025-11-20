package com.figuredzx.antixray.mixin;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FigantiXray implements DedicatedServerModInitializer {

    public static final String MOD_ID = "figantixray";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitializeServer() {
        LOGGER.info("正在初始化 Figanti 反透视模组");
        try {
            // Initialize config and data managers
            ConfigManager.initialize();
            PlayerDataManager.initialize();

            // Register commands
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> XrayCommand.register(dispatcher));

            // Register event listeners
            EventListeners.register();
            PlayerJoinHandler.register();

            // Save data when server stops
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                PlayerDataManager.saveAllData();
                LOGGER.info("Figanti 反透视数据已保存");
            });

            LOGGER.info("Figanti 反透视模组初始化完成");
        } catch (Exception e) {
            LOGGER.error("Figanti 反透视模组初始化失败", e);
            throw new RuntimeException("模组初始化失败", e);
        }
    }
}