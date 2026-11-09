
package com.example.legitaim;

import com.example.legitaim.keybind.KeyBindings;
import com.example.legitaim.modules.AimAssist;
import com.example.legitaim.modules.HitboxESP;
import com.example.legitaim.modules.Triggerbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class LegitAimAssistClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBindings.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (KeyBindings.TOGGLE_AIM.wasPressed()) {
                AimAssist.enabled = !AimAssist.enabled;
            }

            while (KeyBindings.TOGGLE_TRIGGERBOT.wasPressed()) {
                Triggerbot.enabled = !Triggerbot.enabled;
            }

            while (KeyBindings.TOGGLE_ESP.wasPressed()) {
                HitboxESP.enabled = !HitboxESP.enabled;
            }

            AimAssist.onClientTick(client);
            Triggerbot.onClientTick(client);
        });
    }
}
