package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.datafixers.util.Pair;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.content.DoubleButtonFrequencySlot;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Draw the Double Button Slot
public final class DoubleButtonSlotRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final List<DoubleButtonFrequencySlot> SLOTS = List.of(
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.TOP, true),
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.TOP, false),
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, true),
            new DoubleButtonFrequencySlot(DoubleButtonBlockEntity.ButtonHalf.BOTTOM, false));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button slot
    private DoubleButtonSlotRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the double button slot
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        HitResult target = minecraft.hitResult;
        if (!(target instanceof BlockHitResult hitResult)) {
            return;
        }

        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof DoubleButtonBlockEntity doubleButton)) {
            return;
        }
        if (!doubleButton.isLinkHardwareVisible()) {
            return;
        }

        BlockState state = doubleButton.getBlockState();
        Vec3 localHit = hitResult.getLocation().subtract(Vec3.atLowerCornerOf((Vec3i) pos));
        for (DoubleButtonFrequencySlot slot : SLOTS) {
            boolean hit = slot.testHit(level, pos, state, localHit);
            ItemStack stack = doubleButton.getFrequency(slot.button(), slot.firstFrequency());
            MutableComponent label = slotLabel(slot);
            ValueBox box = new ValueBox(label, new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.25D), pos)
                    .passive(!hit);
            if (!stack.isEmpty()) {
                box.wideOutline();
            }

            Outliner.getInstance()
                    .showOutline(Pair.of(new SlotKey(slot.button(), slot.firstFrequency()), pos), box.transform(slot))
                    .highlightFace(hitResult.getDirection());

            if (hit) {
                ArrayList<MutableComponent> tip = new ArrayList<>();
                tip.add(label.copy());
                tip.add(CreateLang.translateDirect(stack.isEmpty()
                        ? "logistics.filter.click_to_set"
                        : "logistics.filter.click_to_replace"));
                CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the slot label
    private static MutableComponent slotLabel(DoubleButtonFrequencySlot slot) {
        MutableComponent frequency = CreateLang.translateDirect(slot.firstFrequency()
                ? "logistics.firstFrequency"
                : "logistics.secondFrequency");
        return Component.translatable(slot.button().translationKey()).append(" ").append(frequency);
    }

    // Store the slot key
    private record SlotKey(DoubleButtonBlockEntity.ButtonHalf button, boolean firstFrequency) {
    }
}
