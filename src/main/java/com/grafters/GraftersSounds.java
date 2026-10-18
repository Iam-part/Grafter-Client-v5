package com.grafters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class GraftersSounds {
    private GraftersSounds() {}

    public static void click(float pitch) {
        if (!GraftersConfig.get().uiSounds) return;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    /** Higher pitch when switching ON, lower when switching OFF. */
    public static void toggle(boolean on) { click(on ? 1.25f : 0.8f); }
}
