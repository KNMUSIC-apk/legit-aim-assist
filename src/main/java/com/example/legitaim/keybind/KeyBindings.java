package com.example.legitaim;

import com.example.legitaim.config.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    private static KeyBinding toggleHitboxEsp;
    private static KeyBinding toggleAimAssist;
    private static KeyBinding toggleTriggerbot;

    private KeyBindings() {}

    public static void register() {
        // Đăng ký các phím mặc định (Có thể đổi trong Cài đặt -> Phím điều khiển)
        toggleHitboxEsp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_esp",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H, // Phím H: HitboxESP
            "category.legitaim.bindings"
        ));

        toggleAimAssist = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_aim",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // Phím V: AimAssist
            "category.legitaim.bindings"
        ));

        toggleTriggerbot = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_trigger",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R, // Phím R: Triggerbot
            "category.legitaim.bindings"
        ));

        // Lắng nghe sự kiện nhấn phím trong game
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (toggleHitboxEsp.wasPressed()) {
                boolean state = !ModConfig.snapshotEsp().enabled();
                ModConfig.setEspEnabled(state);
                client.player.sendMessage(Text.of("§b[LegitAim] §fHitboxESP: " + (state ? "§aBẬT" : "§cCẮT")), true);
            }

            while (toggleAimAssist.wasPressed()) {
                boolean state = !ModConfig.snapshotAim().enabled();
                ModConfig.setAimEnabled(state);
                client.player.sendMessage(Text.of("§b[LegitAim] §fAimAssist: " + (state ? "§aBẬT" : "§cCẮT")), true);
            }

            while (toggleTriggerbot.wasPressed()) {
                boolean state = !ModConfig.snapshotTrigger().enabled();
                ModConfig.setTriggerEnabled(state);
                client.player.sendMessage(Text.of("§b[LegitAim] §fTriggerbot: " + (state ? "§aBẬT" : "§cCẮT")), true);
            }
        });
    }
}
