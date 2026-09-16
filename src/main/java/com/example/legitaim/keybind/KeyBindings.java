package com.example.legitaim.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    public static KeyBinding toggleAimAssist;
    public static KeyBinding toggleESP;
    public static KeyBinding toggleTriggerbot;

    private KeyBindings() {}

    public static void register() {
        toggleAimAssist = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_aim",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "category.legitaim.bindings"
        ));

        toggleESP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_esp",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.legitaim.bindings"
        ));

        toggleTriggerbot = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.legitaim.toggle_trigger",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.legitaim.bindings"
        ));
    }
}
