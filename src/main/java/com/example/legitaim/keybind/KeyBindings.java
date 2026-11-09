
package com.example.legitaim.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class KeyBindings {

    public static KeyBinding TOGGLE_AIM;
    public static KeyBinding TOGGLE_TRIGGERBOT;
    public static KeyBinding TOGGLE_ESP;

    private static boolean registered = false;

    public static void register() {
        if (registered) return;

        TOGGLE_AIM = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.legitaim.toggle_aim",
                InputUtil.Type.KEYSYM,
                79,
                KeyBinding.Category.MISC
            )
        );

        TOGGLE_TRIGGERBOT = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.legitaim.toggle_triggerbot",
                InputUtil.Type.KEYSYM,
                75,
                KeyBinding.Category.MISC
            )
        );

        TOGGLE_ESP = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.legitaim.toggle_esp",
                InputUtil.Type.KEYSYM,
                76,
                KeyBinding.Category.MISC
            )
        );

        registered = true;
    }
}
