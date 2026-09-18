package com.example.legitaim;

import com.example.legitaim.config.ModConfig;
import com.example.legitaim.keybind.KeyBindings;
import com.example.legitaim.modules.AimAssist;
import com.example.legitaim.modules.HitboxESP;
import com.example.legitaim.modules.Triggerbot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegitAimAssistClient implements ClientModInitializer {

    public static final String MOD_ID = "legitaim";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[LegitAimAssist] Initializing...");

        // Khởi tạo phím tắt
        KeyBindings.register();

        // ── Lắng nghe sự kiện phím tắt (Toggle handlers) ──
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (KeyBindings.toggleAimAssist.wasPressed()) {
                ModConfig.aimAssistEnabled = !ModConfig.aimAssistEnabled;
                sendToggleMessage("Aim Assist", ModConfig.aimAssistEnabled);
            }
            while (KeyBindings.toggleESP.wasPressed()) {
                ModConfig.espEnabled = !ModConfig.espEnabled;
                sendToggleMessage("Hitbox ESP", ModConfig.espEnabled);
            }
            while (KeyBindings.toggleTriggerbot.wasPressed()) {
                ModConfig.triggerbotEnabled = !ModConfig.triggerbotEnabled;
                sendToggleMessage("Triggerbot", ModConfig.triggerbotEnabled);
            }
        });

        // ── Gameplay tick (Xử lý Triggerbot) ──
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Triggerbot.tick();
        });

        // ── Render tick (Xử lý AimAssist mượt + Vẽ ESP) ──
        WorldRenderEvents.START.register(AimAssist::onRender);
        WorldRenderEvents.LAST.register(HitboxESP::render);

        LOGGER.info("[LegitAimAssist] Initialized.");
    }

    private static void sendToggleMessage(String feature, boolean enabled) {
        if (mc.player == null) return;
        String status = enabled ? "§aBẬT" : "§cCẮT";
        mc.player.sendMessage(
            Text.literal("§7[§bLegitAim§7] §f" + feature + ": " + status),
            true
        );
    }
}
