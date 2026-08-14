package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mrh0.createaddition.blocks.portable_energy_interface.PortableEnergyManager;
import com.rieno.gadgetsandgizmos.content.EnergyCargoUnits;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.List;

// Keep Create Addition energy recipes behind their feature toggle
@Pseudo
@Mixin(targets = "com.mrh0.createaddition.trains.schedule.condition.EnergyThresholdCondition", remap = false)
public abstract class CreateAdditionEnergyThresholdConditionMixin extends CargoThresholdCondition {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the unit
    @Overwrite
    protected Component getUnit() {
        return Component.literal(" " + EnergyCargoUnits.label(getMeasure()))
                .withStyle(ChatFormatting.AQUA);
    }

    // Test the create addition energy threshold condition
    @Overwrite
    protected boolean test(Level level, Train train, CompoundTag context) {
        long foundEnergy = 0L;
        for (Carriage carriage : train.carriages) {
            if (carriage.anyAvailableEntity() == null) {
                continue;
            }
            IEnergyStorage storage = PortableEnergyManager.get(
                    carriage.anyAvailableEntity().getContraption());
            if (storage != null) {
                foundEnergy += Math.max(0, storage.getEnergyStored());
            }
        }
        requestStatusToUpdate(EnergyCargoUnits.display(foundEnergy, getMeasure()), context);
        return EnergyCargoUnits.test(getOperator(), foundEnergy,
                EnergyCargoUnits.toFe(getThreshold(), getMeasure()));
    }

    // Get the condition title
    @Overwrite
    public List<Component> getTitleAs(String type) {
        return List.of(Component.literal(getOperator().formatted + " " + getThreshold()
                + " " + EnergyCargoUnits.label(getMeasure()))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the configuration widgets
    @Overwrite
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        super.initConfigurationWidgets(builder);
        builder.addSelectionScrollInput(71, 50, (input, label) -> input
                .forOptions(EnergyCargoUnits.options())
                .titled(Component.literal("Energy unit")), "Measure");
    }

    // Get the waiting status
    @Overwrite
    public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag context) {
        int displayed = getLastDisplaySnapshot(context);
        if (displayed < 0) {
            return Component.empty();
        }
        int offset = getOperator() == Ops.LESS ? -1
                : getOperator() == Ops.GREATER ? 1 : 0;
        return Component.literal(displayed + " / "
                + Math.max(0, getThreshold() + offset) + " "
                + EnergyCargoUnits.label(getMeasure()));
    }
}
