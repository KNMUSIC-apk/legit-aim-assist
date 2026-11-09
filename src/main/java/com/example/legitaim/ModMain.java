package com.example.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModMain implements ModInitializer {
    public static final String MOD_ID = "ten_mod_cua_ban";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Mod đã được khởi tạo thành công trên Minecraft 1.21.11!");
        
        // Đăng ký các Item, Block, hoặc Event tại đây
    }
}
