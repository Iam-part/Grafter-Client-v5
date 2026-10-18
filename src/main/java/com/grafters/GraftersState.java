package com.grafters;

import com.grafters.gui.GraftersMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.lwjgl.glfw.GLFW;

/** Inventory Move state. The actual input override happens in KeyboardInputMixin. */
public final class GraftersState {
    private GraftersState() {}

    private static boolean wasInScreen;

    public static void reset() { wasInScreen = false; }

    public static Screen screen(Minecraft mc) { return mc.gui.screen(); }

    /** True when InvMove should drive movement from the real keyboard. */
    public static boolean invMoveActive(Minecraft mc) {
        Screen s = screen(mc);
        return s != null && GraftersConfig.get().invMove && moveAllowed(s);
    }

    public static void startTick(Minecraft mc) {
        if (mc.player == null) { reset(); return; }

        if (screen(mc) != null) {
            wasInScreen = true;
        } else if (wasInScreen) {
            wasInScreen = false;
            KeyMapping.setAll(); // resync with physically held keys
        }

        if (Freecam.isActive()) Freecam.tick(mc);
    }

    private static boolean moveAllowed(Screen s) {
        if (s instanceof GraftersMenuScreen) return true;
        // text-input screens are excluded so typing doesn't move you
        return s instanceof AbstractContainerScreen<?>
                && !(s instanceof AnvilScreen)
                && !(s instanceof CreativeModeInventoryScreen);
    }

    /** Reads the real keyboard/mouse state, ignoring what vanilla did to the KeyMapping. */
    public static boolean physical(Minecraft mc, KeyMapping km) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(km);
        long h = mc.getWindow().handle();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                    && GLFW.glfwGetKey(h, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(h, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
