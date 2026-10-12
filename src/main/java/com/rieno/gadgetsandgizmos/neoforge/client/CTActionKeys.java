package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.lib.client.input.ClientActionKey;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

// Own gameplay bindings while the library handles sampling and live key labels
public final class CTActionKeys {
    private static final String CATEGORY = "key.categories.createthrusters";
    private static final List<ClientActionKey> KEYS = new ArrayList<>();

    public static final ClientActionKey LAUNCHER_FIRE = mouse("entity_launcher.fire", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final ClientActionKey LAUNCHER_REEL = mouse("entity_launcher.reel", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final ClientActionKey LAUNCHER_LENGTHEN = key("entity_launcher.lengthen", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final ClientActionKey LAUNCHER_SHORTEN = key("entity_launcher.shorten", GLFW.GLFW_KEY_SPACE);
    public static final ClientActionKey LAUNCHER_DETACH = key("entity_launcher.detach", GLFW.GLFW_KEY_R);
    public static final ClientActionKey LAUNCHER_MODE = key("entity_launcher.cycle_power_mode", GLFW.GLFW_KEY_V);
    public static final ClientActionKey LAUNCHER_DISMOUNT = key("entity_launcher.dismount", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final ClientActionKey STAFF_DRAG = mouse("physics_staff.drag", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final ClientActionKey STAFF_LOCK = mouse("physics_staff.lock", GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final ClientActionKey STAFF_ROTATE = key("physics_staff.rotate", GLFW.GLFW_KEY_TAB);
    public static final ClientActionKey STAFF_PUSH = key("physics_staff.push", GLFW.GLFW_KEY_UNKNOWN);
    public static final ClientActionKey STAFF_PULL = key("physics_staff.pull", GLFW.GLFW_KEY_UNKNOWN);
    public static final ClientActionKey STAFF_FAST = key("physics_staff.fast_distance", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final ClientActionKey TABLET_MODE = key("diagnostic_tablet.cycle_mode", GLFW.GLFW_KEY_LEFT_ALT);
    public static final ClientActionKey TABLET_READER_EXIT = key("diagnostic_tablet.exit_reader", GLFW.GLFW_KEY_ESCAPE);

    public static final ClientActionKey CLAW_ESCAPE = key("claw.escape", GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final ClientActionKey CONTROLLER_EXIT = key("controller.exit_input", GLFW.GLFW_KEY_ESCAPE);
    private static int lastPlayerActions;
    private static int playerActionTicks;

    private CTActionKeys() {}

    public static void tick() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            lastPlayerActions = playerActionTicks = 0;
            return;
        }
        int held = CLAW_ESCAPE.isHeld()
                ? com.rieno.gadgetsandgizmos.neoforge.network.ServerboundPlayerActionInputPacket.CLAW_ESCAPE : 0;
        if (held != lastPlayerActions || held != 0 && ++playerActionTicks >= 10) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.rieno.gadgetsandgizmos.neoforge.network.ServerboundPlayerActionInputPacket(held));
            playerActionTicks = 0;
        }
        lastPlayerActions = held;
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KEYS.forEach(key -> key.register(event::register));
    }

    private static ClientActionKey mouse(String name, int button) {
        return add(name, InputConstants.Type.MOUSE, button);
    }

    private static ClientActionKey key(String name, int code) {
        return add(name, InputConstants.Type.KEYSYM, code);
    }

    private static ClientActionKey add(String name, InputConstants.Type type, int code) {
        ClientActionKey key = new ClientActionKey("key.createthrusters." + name, CATEGORY, type, code);
        KEYS.add(key);
        return key;
    }

    // Build controls from current assignments instead of cached descriptions
    public static void onItemTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        List<ClientActionKey> keys;
        if (item instanceof EntityLauncherItem) {
            keys = List.of(LAUNCHER_FIRE, LAUNCHER_REEL, LAUNCHER_LENGTHEN, LAUNCHER_SHORTEN, LAUNCHER_DETACH, LAUNCHER_MODE);
        } else if (CTBlocks.ENTITY_LAUNCHER_ANCHOR != null && item == CTBlocks.ENTITY_LAUNCHER_ANCHOR.get().asItem()) {
            keys = List.of(LAUNCHER_FIRE, LAUNCHER_DETACH, LAUNCHER_DISMOUNT);
        } else if (item instanceof PhysicsStaffItem) {
            keys = List.of(STAFF_DRAG, STAFF_LOCK, STAFF_ROTATE, STAFF_PUSH, STAFF_PULL, STAFF_FAST);
        } else if (item instanceof DiagnosticTabletItem) {
            keys = List.of(TABLET_MODE, TABLET_READER_EXIT);
        } else if (item instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof com.rieno.gadgetsandgizmos.content.ClawBlock) {
            keys = List.of(CLAW_ESCAPE);
        } else if (item instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlock) {
            keys = List.of(CONTROLLER_EXIT);
        } else if (item instanceof com.rieno.gadgetsandgizmos.content.PortableContraptionControllerItem) {
            keys = List.of(CONTROLLER_EXIT);
        } else {
            return;
        }
        for (ClientActionKey key : keys) {
            event.getToolTip().add(Component.translatable("createthrusters.controls.binding",
                    Component.translatable(key.mapping().getName()), key.label()).withStyle(ChatFormatting.GRAY));
        }
    }
}
