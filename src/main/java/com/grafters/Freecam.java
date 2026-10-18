package com.grafters;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Freecam by swapping the client's camera entity for a detached dummy player.
 * The real player stays where it is. The dummy is never added to the world.
 *
 * Two things keep the world from vanishing while you fly:
 *  - cave/occlusion culling is switched off ("See through terrain"), otherwise the renderer hides
 *    everything when the camera is inside solid rock, and
 *  - the camera is kept inside the area the server has actually sent you.
 */
public final class Freecam {
    private Freecam() {}

    private static RemotePlayer cam;
    private static ClientLevel camLevel;
    private static double x, y, z;      // eye position of the free camera
    private static float yaw, pitch;
    private static boolean sneakLatched;
    private static int tickCount;

    // cave-culling switch, found by reflection so a rename cannot break the build
    private static Field cullField;
    private static Object cullOwner;
    private static boolean cullSearched;
    private static Boolean originalCull;

    public static boolean isActive() {
        return GraftersConfig.get().freecam && cam != null;
    }

    public static boolean sneakLatched() { return sneakLatched; }

    public static void reset() {
        cam = null;
        camLevel = null;
        GraftersConfig.get().freecam = false;
        restoreCulling(Minecraft.getInstance());
    }

    public static void onToggle(Minecraft mc, boolean on) {
        if (on) {
            if (mc.player == null || mc.level == null) { GraftersConfig.get().freecam = false; return; }
            cam = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "Freecam"));
            camLevel = mc.level;
            Vec3 eye = mc.player.getEyePosition();
            x = eye.x; y = eye.y; z = eye.z;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            // keep the real player crouched if it was (safe on ledges) while the camera flies
            sneakLatched = mc.player.isCrouching() || mc.options.keyShift.isDown();
            sync();
            snapOld();
            mc.setCameraEntity(cam);
            if (GraftersConfig.get().freecamNoCull) setCulling(mc, false);
        } else {
            restoreCulling(mc);
            if (mc.player != null) mc.setCameraEntity(mc.player);
            cam = null;
            camLevel = null;
            KeyMapping.setAll();
        }
    }

    /** Called by EntityMixin instead of rotating the player. */
    public static void look(double dyaw, double dpitch) {
        yaw += (float) dyaw * 0.15f;
        pitch = Mth.clamp(pitch + (float) dpitch * 0.15f, -90f, 90f);
        if (cam != null) { sync(); snapOldRot(); }   // no interpolation lag on mouse look
    }

    private static void sync() {
        cam.setPos(x, y - cam.getEyeHeight(), z);
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.setYHeadRot(yaw);        // the camera reads the head yaw for living entities
        cam.yBodyRot = yaw;
    }

    private static void snapOldRot() {
        cam.yRotO = yaw;
        cam.xRotO = pitch;
        cam.yHeadRotO = yaw;
        cam.yBodyRotO = yaw;
    }

    private static void snapOld() {
        cam.setOldPosAndRot();
        snapOldRot();
    }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        Options o = mc.options;

        // world changed (dimension, respawn...): the dummy belongs to the old world, so stop cleanly
        if (mc.player == null || mc.level != camLevel) {
            c.freecam = false;
            onToggle(mc, false);
            com.grafters.mods.Util.chat("Freecam turned off (world changed)");
            return;
        }
        // something (death screen, server) may have put the camera back on the player: take it again
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
        if (++tickCount % 20 == 0) setCulling(mc, !c.freecamNoCull);

        // Flight input comes from the physical keys; the real player gets no input
        // (KeyboardInputMixin zeroes it).
        boolean fwdK = GraftersState.physical(mc, o.keyUp), backK = GraftersState.physical(mc, o.keyDown);
        boolean leftK = GraftersState.physical(mc, o.keyLeft), rightK = GraftersState.physical(mc, o.keyRight);
        boolean upK = GraftersState.physical(mc, o.keyJump), downK = GraftersState.physical(mc, o.keyShift);
        boolean sprintK = GraftersState.physical(mc, o.keySprint);

        // Never let clicks reach the (real) player while flying
        while (o.keyAttack.consumeClick()) { }
        while (o.keyUse.consumeClick()) { }
        while (o.keyPickItem.consumeClick()) { }
        o.keyAttack.setDown(false);
        o.keyUse.setDown(false);

        if (GraftersState.screen(mc) != null) { snapOld(); sync(); return; }

        double speed = c.freecamSpeed * (sprintK ? 2.0 : 1.0);
        double fwd = (fwdK ? 1 : 0) - (backK ? 1 : 0);
        double left = (leftK ? 1 : 0) - (rightK ? 1 : 0);
        double up = (upK ? 1 : 0) - (downK ? 1 : 0);

        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
        double sx = Math.cos(yr), sz = Math.sin(yr);

        double dx = lx * fwd + sx * left;
        double dy = ly * fwd + up;
        double dz = lz * fwd + sz * left;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1) { dx /= len; dy /= len; dz /= len; }

        cam.setOldPosAndRot();
        cam.yRotO = yaw; cam.xRotO = pitch; cam.yHeadRotO = yaw;
        x += dx * speed; y += dy * speed; z += dz * speed;

        // stay inside the chunks the server has sent (beyond that there is only void)
        double maxD = Math.min(c.freecamMaxDist, Math.max(16, mc.options.renderDistance().get() * 16 - 8));
        double ox = x - mc.player.getX(), oz = z - mc.player.getZ();
        double d = Math.sqrt(ox * ox + oz * oz);
        if (d > maxD) {
            double k = maxD / d;
            x = mc.player.getX() + ox * k;
            z = mc.player.getZ() + oz * k;
        }
        sync();
    }

    // ------------------------------------------------------------------
    //  Cave culling switch (reflection)
    // ------------------------------------------------------------------
    private static Object fieldValue(Object owner, String name) {
        try {
            Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void findCullField(Minecraft mc) {
        if (cullSearched) return;
        cullSearched = true;
        Object[] owners = {mc, mc.options, fieldValue(mc, "levelRenderer")};
        String[] names = {"smartCull", "chunkCullingEnabled"};
        for (Object owner : owners) {
            if (owner == null) continue;
            for (String n : names) {
                try {
                    Field f = owner.getClass().getDeclaredField(n);
                    if (f.getType() != boolean.class) continue;
                    f.setAccessible(true);
                    cullField = f;
                    cullOwner = owner;
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
    }

    private static void setCulling(Minecraft mc, boolean enabled) {
        try {
            findCullField(mc);
            if (cullField == null) return;
            boolean cur = cullField.getBoolean(cullOwner);
            if (originalCull == null) originalCull = cur;
            if (cur == enabled) return;
            cullField.setBoolean(cullOwner, enabled);
            Object lr = fieldValue(mc, "levelRenderer");
            if (lr != null) lr.getClass().getMethod("needsUpdate").invoke(lr);   // rebuild what is visible
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static void restoreCulling(Minecraft mc) {
        Boolean original = originalCull;
        originalCull = null;
        if (original != null) {
            setCulling(mc, original);
            originalCull = null;
        }
    }
}
