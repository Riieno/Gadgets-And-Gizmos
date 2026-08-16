package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import dev.propulsionteam.propulsionsimulated.PropulsionConfig;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

// Set the Oxidized fuel bonus for multiblock Thrusters

@Pseudo
@Mixin(
    targets="dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity",
    remap = false
)

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/



public abstract class PropulsionSimulatedOxidizedFuelThrusterSupportMixin{

    //Get the current fuel stack
    @Shadow
    public abstract FluidStack fluidStack();

    // Is this a multiblock Thruster?
    @Shadow
    public abstract boolean isMultiblock();

    // Is this the multiblock controller?
    @Shadow
    public abstract boolean isController();

    // Get the controller
    @Shadow
    public abstract ThrusterBlockEntity getControllerBE();

    // Define and set Oxidized Fuel Bonus
    @ModifyArg(
        method = {"updateSingleThrust", "updateMultiThrust"},
        at = @At(
            value = "INVOKE",
            target = "Ldev/propulsionteam/propulsionsimulated/content/thruster/thruster/ThrusterBlockEntity;setThrustAndSync(F)V"),
            index = 0
    )

    // Set Thrusters Thrust
    private float createthrusters$applyOxidizedFuelBonus(float thrust){
        // Return base thrust for single thrusters or non oxized fuel
        if(!isMultiblock() || !OxidizedFuel.isOxidized(fluidStack())){
            return thrust;
        }

        // Define thrust bonus for oxidized fuel on multiblock thrusters based on thruster width
        int thrusterSize = ((PropulsionSimulatedThrusterWidthAccessor) (Object) this).createthrusters$getthrusterSize();

        // Calculate bonus
        float multiplier = createthrusters$getnOxidizerThrustMultiplier(thrusterSize);
        double share = CTConfigs.COMMON.propulsionSimulatedOxidizedFuelBonusShare.get();
        return (float) (thrust *(1.0D + (multiplier - 1.0D)*share));
    }

    // Update the Propulsion Simulated Thruster Goggle Tooltip
    @ModifyExpressionValue(
        method = "addThrusterDetails",
        at = @At(
                value = "INVOKE",
                target = "Ldev/propulsionteam/propulsionsimulated/content/thruster/thruster/ThrusterBlockEntity;validOxidizer()Z"
        )
    )
    private boolean createthrusters$showOxidizedFuelBonus(boolean nOxidizerPresent) {
        return nOxidizerPresent
                || (isMultiblock() && OxidizedFuel.isOxidized(fluidStack()));
    }

    @Redirect(
        method = "addThrusterDetails",
        at = @At(
                value = "INVOKE",
                target = "Ldev/propulsionteam/propulsionsimulated/content/thruster/thruster/ThrusterBlockEntity;getMultiblockOxidizerEfficiency(I)F"
        )
    )
    private float createthrusters$showScaledOxidizerEfficiency(int thrusterSize) {
        float nEfficiency = createthrusters$getnOxidizerEfficiency(thrusterSize);
        if (!OxidizedFuel.isOxidized(fluidStack())) {
            return nEfficiency;
        }

        if (createthrusters$hasnOxidizer()){
            return nEfficiency;
        }

        double share = CTConfigs.COMMON.propulsionSimulatedOxidizedFuelBonusShare.get();
        return (float) (1.0D - (1.0D - nEfficiency) * share);
    }

    @Redirect(
        method = "addThrusterDetails",
        at = @At(
            value = "INVOKE",
            target = "Ldev/propulsionteam/propulsionsimulated/content/thruster/thruster/ThrusterBlockEntity;getMultiblockOxidizerThrustMultiplier(I)F"
        )
    )

    private float createthrusters$showScaledOxidizerThrustMultiplier(int thrusterSize){
        float nMultiplier = createthrusters$getnOxidizerThrustMultiplier(thrusterSize);
        if(!OxidizedFuel.isOxidized(fluidStack())){
            return nMultiplier;
        }

        double share = CTConfigs.COMMON.propulsionSimulatedOxidizedFuelBonusShare.get();
        float multiplier = (float) (1.0D + (nMultiplier - 1.0D) * share);

        return createthrusters$hasnOxidizer() ? nMultiplier * multiplier : multiplier;

        
    }

    private boolean createthrusters$hasnOxidizer(){
        ThrusterBlockEntity controller = isController() ? (ThrusterBlockEntity) (Object) this : getControllerBE();
        return controller != null && controller.oxidizerTank != null && !controller.oxidizerTank.isEmpty() && controller.oxidizerTank.getPrimaryHandler().getFluidAmount() > 0;
    }

    private static float createthrusters$getnOxidizerEfficiency(int thrusterSize){
        return switch(thrusterSize){
            case 2 -> PropulsionConfig.MULTIBLOCK_2X_OXIDIZER_EFFICIENCY.get().floatValue();
            case 3 -> PropulsionConfig.MULTIBLOCK_3X_OXIDIZER_EFFICIENCY.get().floatValue();
            default -> 1.0F;
        };
    }

    private static float createthrusters$getnOxidizerThrustMultiplier(int thrusterSize){
        float oxidizerEfficiency = createthrusters$getnOxidizerEfficiency(thrusterSize);
        return oxidizerEfficiency <= 0.0F ? 1.0F : 1.0F / oxidizerEfficiency;
    }

}







// public abstract class PropulsionSimulatedOxidizedFuelThrusterSupportMixin{

//     // Get current fuel stack
//     @Shadow
//     public abstract FluidStack fluidStack();

//     // Is Thruster a Multiblock?
//     @Shadow
//     public abstract boolean isMultiblock();

//     // Set the Oxidized fuel bonus
//     @ModifyArg(
//         method= {"updateSingleThrust", "updateMultiThrust"},
//         at = @At(
//             value = "INVOKE",
//                     target = "Ldev/propulsionteam/propulsionsimulated/content/thruster/thruster/ThrusterBlockEntity;setThrustAndSync(F)V"),
//             index = 0
//     )
//     private float createthrusters$applyOxidizedFuelBonus(float thrust) {
//         if(!isMultiblock() || !OxidizedFuel.isOxidized(fluidStack())){
//             return thrust;
//         }

//         int thrusterSize = ((PropulsionSimulatedThrusterWidthAccessor) (Object) this).createthrusters$getthrusterSize();

//         double oxidizerEfficiency = switch(thrusterSize){
//             case 2 -> PropulsionConfig.MULTIBLOCK_2X_OXIDIZER_EFFICIENCY.get();
//             case 3 -> PropulsionConfig.MULTIBLOCK_3X_OXIDIZER_EFFICIENCY.get();
//             default -> 1.0D;
//         };
//         if (oxidizerEfficiency <= 0.0D){
//             return thrust;
//         }

//         double share = CTConfigs.COMMON.propulsionSimulatedOxidizedFuelBonusShare.get();
//         double nOxidizerBonus = (1.0D / oxidizerEfficiency)-1.0D;
//         double multiplier = 1.0D + (nOxidizerBonus * share);
//         return (float) (thrust * multiplier);
//     }
// }