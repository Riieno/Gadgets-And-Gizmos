package com.rieno.gadgetsandgizmos.ponder;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.VariableTransmissionBlock;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Build the in-game Ponder scenes used to explain the addon's less obvious mechanics
public final class CTPonderScenes {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int CT_TOOLTIP_DURATION = 120;
    private static final int CT_TOOLTIP_PAUSE = 180;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT ponder scenes
    private CTPonderScenes() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the CT tooltip
    private static TextElementBuilder ctTooltip(CreateSceneBuilder scene) {
        return scene.overlay().showText(CT_TOOLTIP_DURATION);
    }

    // Handle the CT pause
    private static void ctPause(CreateSceneBuilder scene) {
        scene.idle(CT_TOOLTIP_PAUSE);
    }

    // Animate the nixie signal
    private static void animateNixieSignal(CreateSceneBuilder scene, Selection nixieSel, int from, int to, int step, int ticksPerStep) {
        for (int strength = from; strength <= to; strength += step) {
            final int displayStrength = strength;
            scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", displayStrength));
            scene.idle(ticksPerStep);
        }
    }

    // Get the create filter stack
    private static ItemStack ct$getCreateFilterStack() {
        ItemStack filter = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse("create:filter"))
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
        if (!filter.isEmpty()) {
            return filter;
        }
        filter = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse("create:attribute_filter"))
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
        if (!filter.isEmpty()) {
            return filter;
        }
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse("create:package_filter"))
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
    }

    // Handle the thruster propulsion
    public static void thrusterPropulsion(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster.propulsion", "Thruster \u2013 Fuel & Propulsion");
        scene.configureBasePlate(0, 0, 5);

        BlockPos thrusterPos = util.grid().at(3, 1, 2);
        BlockPos leverPos    = util.grid().at(3, 1, 1);
        Selection thrusterSel  = util.select().position(thrusterPos);
        Selection leverSel     = util.select().position(leverPos);
        Selection leverToggle  = util.select().fromTo(leverPos, leverPos.below());
        Selection pipesY1      = util.select().fromTo(4, 1, 2, 5, 1, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(thrusterSel, Direction.EAST);
        scene.idle(8);
        scene.world().showSection(pipesY1, Direction.DOWN);
        scene.idle(5);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("The Thruster produces exhaust thrust to propel physics-assembled contraptions")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.overlay().showControls(
                util.vector().blockSurface(util.grid().at(5, 1, 2), Direction.EAST), Pointing.LEFT, 35)
            .withItem(new ItemStack(Items.LAVA_BUCKET));
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("Lava, dense fluids, and XP are accepted as liquid fuel via fluid pipes")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(util.grid().at(4, 1, 2), Direction.NORTH));
        ctPause(scene);

        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.6, 0), Pointing.DOWN, 35)
            .withItem(new ItemStack(Items.COAL));
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("Press Use with Coal, Blaze Rods, or Blaze Cakes to load solid fuel directly")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        // -----------------------------------------------------FUEL SETUP-----------------------------------------------------
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putBoolean("Enabled", true);
            nbt.putString("ControlMode", "REDSTONE");
            nbt.putBoolean("InfiniteSolidFuel", true);
            nbt.putString("SolidFuelTypeId", "create:creative_blaze_cake");
            nbt.putDouble("SolidFuelPowerMultiplier", 1.5D);
            nbt.putString("SolidFuelParticleStyle", "DEFAULT");
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
            nbt.putInt("Signal", 0);
        });

        // ------------------------------------REDSTONE CONTROL------------------------------------
        scene.world().showSection(leverSel, Direction.SOUTH);
        scene.idle(5);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("Redstone signal 0-15 controls throttle from 0% to 100%")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(leverPos, Direction.NORTH));
        ctPause(scene);

        scene.world().toggleRedstonePower(leverToggle);
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, true), false);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 1.0f);
            nbt.putFloat("RedstoneThrottle", 1.0f);
            nbt.putInt("Signal", 15);
        });
        scene.idle(10);
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text("Signal strength 15 fires the thruster at maximum thrust")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 0.33f);
            nbt.putFloat("RedstoneThrottle", 0.33f);
            nbt.putInt("Signal", 5);
        });
        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text("Lower signal strengths proportionally reduce thrust output")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.addKeyframe();
        // ------------------------------------PROCESSING FILTER------------------------------------
        ItemStack createFilter = ct$getCreateFilterStack();
        ctTooltip(scene)
            .colored(PonderPalette.INPUT)
            .text("Insert a Create Filter in the Filter slot to run in peaceful mode")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(thrusterPos, Direction.SOUTH));
        if (!createFilter.isEmpty()) {
            scene.overlay().showControls(
                    util.vector().blockSurface(thrusterPos, Direction.SOUTH), Pointing.RIGHT, 35)
                .withItem(createFilter.copy());
            scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class,
                be -> be.insertInventorySlot(ThrusterBlockEntity.SLOT_LIST, createFilter.copy()));
        }
        ctPause(scene);
        ctTooltip(scene)
            .colored(PonderPalette.RED)
            .text("With a Filter installed, the thruster is muted and plume particles are suppressed")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class,
            be -> be.extractInventorySlot(ThrusterBlockEntity.SLOT_LIST, null));
        scene.idle(10);

        // -----------------------------------------------------PLUME COLOR-----------------------------------------------------
        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.6, 0), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Items.BLUE_DYE));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putInt("BeamColor", 0x3F76E4);
            nbt.putFloat("PlumeColorRatio", 1.0f);
        });
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text("Use Dye on the thruster to recolor its plume")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.world().toggleRedstonePower(leverToggle);
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, false), false);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
            nbt.putInt("Signal", 0);
        });
        scene.idle(10);
        ctTooltip(scene)
            .colored(PonderPalette.RED)
            .text("Signal 0 cuts the throttle \u2014 the thruster goes silent")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.markAsFinished();
        }

        // Handle the thruster FE mode
        public static void thrusterFeMode(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster.fe_mode", "Thruster \u2013 Config, Upgrades & Focused Mode");
        scene.configureBasePlate(0, 0, 5);

        BlockPos thrusterPos = util.grid().at(3, 1, 2);
        BlockPos leverPos    = util.grid().at(3, 1, 1);
        Selection thrusterSel  = util.select().position(thrusterPos);
        Selection cableSection = util.select().fromTo(4, 1, 2, 5, 1, 2);
        Selection leverSel     = util.select().position(leverPos);
        Selection leverToggle  = util.select().fromTo(leverPos, leverPos.below());

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(thrusterSel, Direction.EAST);
        scene.idle(8);
        scene.world().showSection(cableSection, Direction.DOWN);
        scene.idle(5);

        scene.overlay().showControls(
                util.vector().blockSurface(thrusterPos, Direction.NORTH), Pointing.RIGHT, 40)
            .rightClick()
            .withItem(ItemStack.EMPTY);
        ctTooltip(scene)
            .attachKeyFrame()
            .text("Sneak + Use the Thruster to open its Config screen")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(thrusterPos, Direction.NORTH));
        ctPause(scene);
        ctTooltip(scene)
            .text("The Config screen exposes throttle min/max, control mode, and equipment slots")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.7, 0), Pointing.DOWN, 40)
            .withItem(new ItemStack(CTItems.PROPULSION_UPGRADE_T1.get()));
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text("A Propulsion Upgrade multiplies thrust: T1=2x, T2=4x, T3=8x, T4=16x")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putBoolean("Enabled", true);
            nbt.putString("ControlMode", "REDSTONE");
            nbt.putBoolean("InfiniteSolidFuel", true);
            nbt.putString("SolidFuelTypeId", "create:creative_blaze_cake");
            nbt.putDouble("SolidFuelPowerMultiplier", 1.5D);
            nbt.putString("SolidFuelParticleStyle", "DEFAULT");
            nbt.putInt("PropulsionUpgradeTier", 1);
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
        });
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE,
                new ItemStack(CTItems.PROPULSION_UPGRADE_T1.get())));
        scene.idle(85);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("The Filter slot accepts a Create filter to restrict which items are processed")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(thrusterPos, Direction.SOUTH));
        ctPause(scene);

        scene.addKeyframe();
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.extractInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE, null));
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.7, 0), Pointing.DOWN, 40)
            .withItem(new ItemStack(CTItems.THRUSTER_LENSE.get()));
        ctTooltip(scene)
            .colored(PonderPalette.INPUT)
            .text("Insert a Thruster Lens into the Lens slot to enable Focused (FE) Mode")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putBoolean("FocusedMode", true);
            nbt.putInt("BeamFE", Integer.MAX_VALUE);
            nbt.putFloat("BeamMaxOpacity", 1.0f);
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
        });
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_LENS,
                new ItemStack(CTItems.THRUSTER_LENSE.get())));

        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.getEnergyStorage().receiveEnergy(Integer.MAX_VALUE, false));
        ctPause(scene);
        ctTooltip(scene)
            .colored(PonderPalette.INPUT)
            .text("In Focused Mode the thruster draws Forge Energy instead of fluid or solid fuel")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(util.grid().at(4, 1, 2), Direction.NORTH));
        ctPause(scene);
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text("Keep FE cables connected for effectively infinite beam runtime")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(util.grid().at(5, 1, 2), Direction.EAST));
        ctPause(scene);

        // ------------------------------------REDSTONE CONTROL------------------------------------
        scene.world().showSection(leverSel, Direction.SOUTH);
        scene.idle(5);
        scene.addKeyframe();
        scene.world().toggleRedstonePower(leverToggle);
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, true), false);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 1.0f);
            nbt.putFloat("RedstoneThrottle", 1.0f);
            nbt.putInt("Signal", 15);
        });
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.getEnergyStorage().receiveEnergy(Integer.MAX_VALUE, false));
        scene.idle(10);
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text("With a Lens inserted and FE available, redstone-on fires a focused beam")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.6, 0), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Items.BLUE_DYE));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putInt("BeamColor", 0x3F76E4);
        });
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text("Dye also tints Focused Mode: use blue dye to recolor the beam")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.world().toggleRedstonePower(leverToggle);
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, false), false);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
        });
        scene.idle(10);
        ctTooltip(scene)
            .colored(PonderPalette.RED)
            .text("Removing the Thruster Lens restores normal fluid / solid-fuel operation")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.markAsFinished();
        }

        // Handle the thruster processing
        public static void thrusterProcessing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster.processing", "Thruster \u2013 Item Processing");
        scene.configureBasePlate(0, 0, 5);

        // Setup the thruster exhaust line
        BlockPos thrusterPos = util.grid().at(4, 1, 2);
        Selection thrusterSel = util.select().position(thrusterPos);
        BlockPos depotWest  = util.grid().at(1, 1, 2);
        BlockPos depotSouth = util.grid().at(4, 1, 3);
        BlockPos depotMid1  = util.grid().at(3, 1, 3);
        BlockPos depotMid2  = util.grid().at(2, 1, 3);
        BlockPos depotFar   = util.grid().at(1, 1, 3);
        Selection allDepots = util.select()
            .position(depotWest).add(util.select().position(depotSouth))
            .add(util.select().position(depotMid1)).add(util.select().position(depotMid2))
            .add(util.select().position(depotFar));
        Selection pipeSection = util.select().fromTo(5, 0, 2, 5, 1, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(thrusterSel, Direction.EAST);
        scene.idle(8);
        scene.world().showSection(pipeSection, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(allDepots, Direction.UP);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("A Processing Upgrade converts the thruster exhaust into an item-processing stream")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putBoolean("Enabled", true);
            nbt.putString("ControlMode", "REDSTONE");
            nbt.putBoolean("InfiniteSolidFuel", true);
            nbt.putString("SolidFuelTypeId", "create:creative_blaze_cake");
            nbt.putDouble("SolidFuelPowerMultiplier", 1.5D);
            nbt.putString("SolidFuelParticleStyle", "DEFAULT");
            nbt.putFloat("Throttle", 1.0f);
            nbt.putFloat("RedstoneThrottle", 1.0f);
            nbt.putInt("Signal", 15);
        });
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, true), false);

        // Show Smoking upgrade food processing
        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.7, 0), Pointing.DOWN, 35)
            .withItem(new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T1.get()));
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("Insert a Smoking Upgrade — food items on Depots in the exhaust stream are cooked")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE,
                new ItemStack(CTItems.PROCESSING_UPGRADE_SMOKING_T1.get())));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putString("ProcessingUpgradeType", "SMOKING");
            nbt.putInt("ProcessingUpgradeTier", 1);
        });
        scene.idle(75);

        scene.world().modifyBlockEntity(depotWest, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.BEEF)));
        scene.overlay().showControls(util.vector().topOf(depotWest), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.BEEF));
        ctTooltip(scene)
            .colored(PonderPalette.WHITE)
            .text("Raw food placed on Depots in the exhaust stream is smoked into cooked food")
            .placeNearTarget()
            .pointAt(util.vector().topOf(depotWest));
        ctPause(scene);
        scene.world().modifyBlockEntity(depotWest, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.COOKED_BEEF)));
        scene.overlay().showControls(util.vector().topOf(depotWest), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.COOKED_BEEF));
        scene.idle(30);
        scene.world().modifyBlockEntity(depotWest, DepotBlockEntity.class,
            depot -> depot.setHeldItem(ItemStack.EMPTY));
        scene.idle(10);

        // Swap to the Smelting upgrade
        scene.addKeyframe();
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.extractInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE, null));
        scene.idle(5);
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.7, 0), Pointing.DOWN, 35)
            .withItem(new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T1.get()));
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("A Smelting Upgrade smelts raw ores and other smeltable items in the exhaust")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE,
                new ItemStack(CTItems.PROCESSING_UPGRADE_SMELTING_T1.get())));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putString("ProcessingUpgradeType", "SMELTING");
            nbt.putInt("ProcessingUpgradeTier", 1);
        });
        scene.idle(70);

        scene.world().modifyBlockEntity(depotMid1, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.RAW_IRON)));
        scene.overlay().showControls(util.vector().topOf(depotMid1), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.RAW_IRON));
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("Raw Iron smelted in the exhaust yields Iron Ingots")
            .placeNearTarget()
            .pointAt(util.vector().topOf(depotMid1));
        ctPause(scene);
        scene.world().modifyBlockEntity(depotMid1, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.IRON_INGOT)));
        scene.overlay().showControls(util.vector().topOf(depotMid1), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.IRON_INGOT));
        scene.idle(30);
        scene.world().modifyBlockEntity(depotMid1, DepotBlockEntity.class,
            depot -> depot.setHeldItem(ItemStack.EMPTY));
        scene.idle(10);

        // Swap to the Haunting upgrade
        scene.addKeyframe();
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.extractInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE, null));
        scene.idle(5);
        scene.overlay().showControls(
                util.vector().topOf(thrusterPos).add(0, 0.7, 0), Pointing.DOWN, 35)
            .withItem(new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T1.get()));
        ctTooltip(scene)
            .colored(PonderPalette.FAST)
            .text("A Haunting Upgrade bathes the exhaust in soul fire \u2014 Gravel becomes Soul Sand, and more")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be ->
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_UPGRADE,
                new ItemStack(CTItems.PROCESSING_UPGRADE_HAUNTING_T1.get())));
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putString("ProcessingUpgradeType", "HAUNTING");
            nbt.putInt("ProcessingUpgradeTier", 1);
        });
        ctPause(scene);

        scene.world().modifyBlockEntity(depotMid2, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.GRAVEL)));
        scene.overlay().showControls(util.vector().topOf(depotMid2), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.GRAVEL));
        ctTooltip(scene)
            .colored(PonderPalette.FAST)
            .text("Gravel haunted by the exhaust stream becomes Soul Sand")
            .placeNearTarget()
            .pointAt(util.vector().topOf(depotMid2));
        ctPause(scene);
        scene.world().modifyBlockEntity(depotMid2, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.SOUL_SAND)));
        scene.overlay().showControls(util.vector().topOf(depotMid2), Pointing.DOWN, 25)
            .withItem(new ItemStack(Items.SOUL_SAND));
        scene.idle(30);
        scene.world().modifyBlockEntity(depotMid2, DepotBlockEntity.class,
            depot -> depot.setHeldItem(ItemStack.EMPTY));
        scene.idle(10);

        // Finish with upgrade range and speed
        ctTooltip(scene)
            .colored(PonderPalette.WHITE)
            .text("Higher-tier upgrades increase processing speed and range \u2014 pair with the Filter slot")
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);
        scene.markAsFinished();
        }

        // Handle the industrial alternator
        public static void industrialAlternator(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("industrial_alternator.usage", "Industrial Alternator");
        scene.configureBasePlate(0, 0, 5);

        BlockPos leverPos = util.grid().at(0, 1, 1);
        BlockPos thrusterPos = util.grid().at(0, 1, 2);
        BlockPos cableWest = util.grid().at(1, 1, 2);
        BlockPos cableEast = util.grid().at(2, 1, 2);
        BlockPos alternatorPos = util.grid().at(3, 1, 2);
        BlockPos shaftPos = util.grid().at(4, 1, 2);
        BlockPos cogPos = util.grid().at(5, 1, 2);
        Selection alternatorCore = util.select().position(alternatorPos);
        Selection drivetrain = util.select().fromTo(3, 1, 2, 5, 1, 2);
        Selection cableLine = util.select().fromTo(1, 1, 2, 2, 1, 2);
        Selection thrusterSel = util.select().position(thrusterPos);
        Selection leverSel = util.select().position(leverPos);
        Selection leverToggle = util.select().fromTo(leverPos, leverPos.below());

        // -----------------------------------------------------KINETIC INPUT-----------------------------------------------------
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(alternatorCore, Direction.DOWN);
        scene.world().showSection(drivetrain, Direction.DOWN);
        scene.world().setKineticSpeed(drivetrain, 64.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        scene.effects().rotationSpeedIndicator(cogPos);
        scene.idle(10);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_alternator.usage.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(alternatorPos));
        ctPause(scene);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_alternator.usage.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(alternatorPos));
        ctPause(scene);
        // ------------------------------------GENERATOR LOAD------------------------------------
        scene.world().setKineticSpeed(drivetrain, 0.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        scene.effects().rotationSpeedIndicator(cogPos);
        scene.effects().indicateRedstone(alternatorPos);
        scene.idle(20);

        scene.idle(60);
        scene.world().setKineticSpeed(drivetrain, 64.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        scene.effects().rotationSpeedIndicator(cogPos);
        scene.idle(10);
        // -----------------------------------------------------ENERGY OUTPUT-----------------------------------------------------
        scene.world().showSection(cableLine, Direction.EAST);
        scene.idle(5);
        scene.world().showSection(thrusterSel, Direction.EAST);
        scene.idle(5);
        scene.world().showSection(leverSel, Direction.SOUTH);
        scene.idle(5);

        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putBoolean("FocusedMode", true);
            nbt.putInt("BeamFE", Integer.MAX_VALUE);
            nbt.putFloat("BeamMaxOpacity", 1.0f);
            nbt.putFloat("Throttle", 0.0f);
            nbt.putFloat("RedstoneThrottle", 0.0f);
        });
        scene.world().modifyBlockEntity(thrusterPos, ThrusterBlockEntity.class, be -> {
            be.insertInventorySlot(ThrusterBlockEntity.SLOT_LENS, new ItemStack(CTItems.THRUSTER_LENSE.get()));
            be.getEnergyStorage().receiveEnergy(Integer.MAX_VALUE, false);
        });

        // ------------------------------------POWERED THRUSTER------------------------------------
        scene.addKeyframe();
        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_alternator.usage.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(cableWest, Direction.NORTH));
        ctPause(scene);

        scene.world().toggleRedstonePower(leverToggle);
        scene.effects().indicateRedstone(leverPos);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, true), false);
        scene.world().modifyBlockEntityNBT(thrusterSel, ThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("Throttle", 1.0f);
            nbt.putFloat("RedstoneThrottle", 1.0f);
            nbt.putInt("Signal", 15);
        });
        scene.idle(15);
        scene.markAsFinished();
        }

    // Handle the industrial motor
    public static void industrialMotor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("industrial_motor.usage", "Industrial Motor");
        scene.configureBasePlate(0, 0, 5);

        BlockPos depotPos = util.grid().at(2, 1, 2);
        BlockPos pressPos = util.grid().at(2, 3, 2);
        BlockPos shaftPos = util.grid().at(3, 3, 2);
        BlockPos motorPos = util.grid().at(4, 3, 2);
        BlockPos cablePos = util.grid().at(5, 3, 2);

        Selection depotSel = util.select().position(depotPos);
        Selection pressSel = util.select().position(pressPos);
        Selection drivetrain = util.select().fromTo(2, 3, 2, 5, 3, 2);
        Selection motorSel = util.select().position(motorPos);
        Selection cableSel = util.select().fromTo(5, 0, 2, 5, 3, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(depotSel, Direction.UP);
        scene.world().showSection(pressSel, Direction.DOWN);
        scene.world().showSection(drivetrain, Direction.DOWN);
        scene.world().showSection(motorSel, Direction.DOWN);
        scene.world().showSection(cableSel, Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlockEntityNBT(motorSel, IndustrialMotorBlockEntity.class, nbt -> {
            nbt.putInt("Energy", 5000);
            nbt.putBoolean("Active", true);
            nbt.putFloat("GeneratedSpeed", 128.0f);
            nbt.putInt("Consumption", IndustrialMotorBlockEntity.getEnergyConsumptionRate(128.0f));
        });
        scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.RAW_IRON, 64)));
        scene.world().modifyBlockEntity(pressPos, MechanicalPressBlockEntity.class,
            press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.world().setKineticSpeed(drivetrain, 128.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);

        scene.addKeyframe();
        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_motor.usage.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(motorPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class,
            depot -> depot.setHeldItem(new ItemStack(Items.RAW_IRON, 64)));
        scene.world().modifyBlockEntity(pressPos, MechanicalPressBlockEntity.class,
            press -> press.getPressingBehaviour().start(PressingBehaviour.Mode.BELT));
        scene.world().setKineticSpeed(drivetrain, 256.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        scene.effects().rotationSpeedIndicator(motorPos);
        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_motor.usage.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(pressPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().blockSurface(motorPos, Direction.WEST), Pointing.RIGHT, 35)
            .rightClick()
            .withItem(new ItemStack(Items.REDSTONE));
        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.industrial_motor.usage.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(motorPos, Direction.WEST));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the bearing controller
    public static void bearingController(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster_bearing.controller", "Servo Bearing - Controller Input");
        scene.rotateCameraY(-90.0f);

        BlockPos controllerPos = util.grid().at(0, 1, 4);
        BlockPos cogPos = util.grid().at(2, 1, 2);
        BlockPos bearingPos = util.grid().at(2, 1, 3);

        Selection base = util.select().layer(0);
        Selection drivetrain = util.select().fromTo(2, 0, 0, 2, 1, 2);
        Selection controllerSel = util.select().position(controllerPos);
        Selection bearingSel = util.select().position(bearingPos);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(drivetrain, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(bearingSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(controllerSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.controller.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        scene.world().setKineticSpeed(drivetrain, 64.0f);
        scene.effects().rotationSpeedIndicator(cogPos);
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.controller.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 35.0));
        scene.idle(15);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", -20.0));
        scene.idle(15);
        scene.world().setKineticSpeed(drivetrain, 0.0f);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.controller.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.controller.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the bearing data link
    public static void bearingDataLink(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster_bearing.datalink", "Servo Bearing - Joystick and Data Link");
        scene.rotateCameraY(-90.0f);

        BlockPos joystickPos = util.grid().at(0, 1, 3);
        BlockPos cogPos = util.grid().at(2, 1, 2);
        BlockPos bearingPos = util.grid().at(2, 1, 3);
        BlockPos gyroLinkPos = util.grid().at(2, 1, 4);

        Selection base = util.select().layer(0);
        Selection drivetrain = util.select().fromTo(2, 0, 0, 2, 1, 2);
        Selection joystickSel = util.select().position(joystickPos);
        Selection bearingSel = util.select().position(bearingPos);
        Selection gyroSel = util.select().position(gyroLinkPos);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(drivetrain, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(bearingSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(joystickSel, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(gyroSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.datalink.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(gyroLinkPos));
        ctPause(scene);

        scene.world().setKineticSpeed(drivetrain, 64.0f);
        scene.effects().rotationSpeedIndicator(cogPos);
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.datalink.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(util.vector().topOf(joystickPos).add(0, 0.5, 0), Pointing.UP, 40)
            .withItem(ItemStack.EMPTY);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 30.0));
        scene.effects().indicateRedstone(joystickPos);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(joystickPos).add(0, 0.5, 0), Pointing.LEFT, 40)
            .withItem(ItemStack.EMPTY);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", -30.0));
        scene.effects().indicateRedstone(joystickPos);
        scene.idle(15);
        scene.world().setKineticSpeed(drivetrain, 0.0f);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.datalink.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.datalink.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(gyroLinkPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the bearing servo
    public static void bearingServo(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster_bearing.servo", "Servo Bearing - Servo Input");

        BlockPos cogPos = util.grid().at(2, 1, 1);
        BlockPos bearingPos = util.grid().at(2, 1, 2);
        BlockPos gearboxPos = util.grid().at(3, 1, 1);
        BlockPos gyroLinkPos = util.grid().at(3, 2, 1);
        BlockPos gimbalPos = util.grid().at(4, 3, 3);

        Selection base = util.select().layer(0);
        Selection cogSel = util.select().position(cogPos);
        Selection bearingSel = util.select().position(bearingPos);
        Selection gearboxSel = util.select().position(gearboxPos);
        Selection gyroSel = util.select().position(gyroLinkPos);
        Selection gimbalSel = util.select().position(gimbalPos);
        Selection drivetrain = cogSel.add(gearboxSel).add(bearingSel);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(cogSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(bearingSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gearboxSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gyroSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gimbalSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.servo.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.servo.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(gimbalPos));
        ctPause(scene);

        scene.world().setKineticSpeed(drivetrain, 64.0f);
        scene.effects().rotationSpeedIndicator(cogPos);

        scene.addKeyframe();
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 22.5));
        scene.idle(12);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", -35.0));
        scene.idle(12);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 0.0));
        scene.idle(10);
        scene.world().setKineticSpeed(drivetrain, 0.0f);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.servo.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.servo.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(gyroLinkPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the bearing fuel
    public static void bearingFuel(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster_bearing.fuel", "Servo Bearing - Fuel and FE Passthrough");
        scene.rotateCameraY(-90.0f);

        BlockPos tankPos = util.grid().at(2, 1, 2);
        BlockPos thrusterPos = util.grid().at(2, 1, 3);
        BlockPos bearingPos = util.grid().at(3, 1, 2);
        BlockPos pipeNearPos = util.grid().at(4, 1, 2);
        BlockPos pipeMidPos = util.grid().at(5, 1, 2);
        BlockPos pipeLowerPos = util.grid().at(5, 0, 2);

        Selection base = util.select().layer(0);
        Selection tankSel = util.select().position(tankPos);
        Selection bearingSel = util.select().position(bearingPos);
        Selection thrusterSel = util.select().position(thrusterPos);
        Selection pipesSel = util.select().position(pipeNearPos)
            .add(util.select().position(pipeMidPos))
            .add(util.select().position(pipeLowerPos));
        Selection assemblySel = tankSel.add(bearingSel).add(thrusterSel).add(pipesSel);

        // ------------------------------------ASSEMBLY INTRO------------------------------------
        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(tankSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(pipesSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(bearingSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(thrusterSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_0").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(pipeLowerPos));
        ctPause(scene);

        ctTooltip(scene)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(tankPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, true), false);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 25.0));
        scene.idle(12);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", -25.0));
        scene.idle(12);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        scene.addKeyframe();
        // ------------------------------------PIPE CONNECTION------------------------------------
        scene.world().hideSection(pipesSel, Direction.EAST);
        scene.idle(6);
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.thruster_bearing.fuel.text_5").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        scene.world().showSection(pipesSel, Direction.WEST);
        scene.idle(6);
        scene.world().modifyBlock(thrusterPos, state -> state.setValue(ThrusterBlock.POWERED, false), false);
        scene.world().modifyBlockEntityNBT(bearingSel, ThrusterBearingBlockEntity.class,
            nbt -> nbt.putDouble("ComputerTargetAngleDeg", 0.0));
        scene.idle(8);

        scene.markAsFinished();
    }

    // Handle the claw basics
    public static void clawBasics(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("claw.basics", "The Claw");
        scene.configureBasePlate(0, 0, 5);
        scene.rotateCameraY(-90.0f);

        BlockPos clawPos         = util.grid().at(2, 4, 2);
        BlockPos winchPos        = util.grid().at(2, 5, 2);
        BlockPos carriagePos     = util.grid().at(2, 6, 2);
        BlockPos connectorPos    = util.grid().at(2, 2, 2);
        BlockPos vault1Pos       = util.grid().at(1, 1, 2);
        BlockPos vault2Pos       = util.grid().at(2, 1, 2);

        Selection base        = util.select().layer(0);
        Selection gantryShaft = util.select().fromTo(2, 7, 0, 2, 7, 4);
        Selection carriage    = util.select().position(carriagePos);
        Selection winch       = util.select().position(winchPos);
        Selection clawSel     = util.select().position(clawPos);
        Selection connectorSel= util.select().position(connectorPos);
        Selection vaultsSel   = util.select().fromTo(1, 1, 2, 2, 1, 2);

        Selection loadSel     = util.select().fromTo(1, 1, 2, 2, 2, 2);

        // ------------------------------------ASSEMBLY REVEAL------------------------------------
        scene.world().showSection(base, Direction.UP);
        scene.idle(4);
        scene.world().showSection(gantryShaft, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(carriage, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(winch, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(clawSel, Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(connectorSel, Direction.UP);
        scene.idle(4);
        scene.world().showSection(vaultsSel, Direction.UP);
        scene.idle(10);

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.WHITE)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(clawPos, Direction.NORTH));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.WHITE)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(winchPos, Direction.NORTH));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.SLOW)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(clawPos, Direction.NORTH));
        ctPause(scene);

        scene.addKeyframe();

        // -----------------------------------------------------CLAW MOVEMENT-----------------------------------------------------
        ElementLink<WorldSectionElement> clawLink = scene.world().makeSectionIndependent(clawSel);

        scene.world().moveSection(clawLink, util.vector().of(0, -1, 0), 30);
        scene.idle(35);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(clawPos, Direction.NORTH));
        ctPause(scene);

        scene.addKeyframe();

        scene.world().moveSection(clawLink, util.vector().of(0, -1, 0), 15);
        scene.idle(18);

        // ------------------------------------CARGO TRANSFER------------------------------------
        ElementLink<WorldSectionElement> loadLink = scene.world().makeSectionIndependent(loadSel);
        scene.world().moveSection(clawLink, util.vector().of(0, 2, 0), 40);
        scene.world().moveSection(loadLink, util.vector().of(0, 2, 0), 40);
        scene.idle(44);

        ctTooltip(scene)
            .colored(PonderPalette.RED)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_5").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(clawPos, Direction.NORTH));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.RED)
            .text(Component.translatable("createthrusters.ponder.claw.basics.text_6").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(clawPos, Direction.NORTH));
        ctPause(scene);

        scene.addKeyframe();

        // -----------------------------------------------------RELEASE LOAD-----------------------------------------------------
        scene.world().moveSection(loadLink, util.vector().of(0, -2, 0), 35);
        scene.idle(40);

        scene.markAsFinished();
    }

    // Handle the gyro linking
    public static void gyroLinking(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("gyroscope_link.linking", "Linking Orientation Sources");
        scene.configureBasePlate(0, 0, 5);
        scene.markAsFinished();
    }

    // Handle the gearbox passthrough
    public static void gearboxPassthrough(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bidirectional_gearbox.passthrough", "Smart Gearbox \u2013 Passthrough Mode");
        scene.configureBasePlate(0, 0, 5);

        BlockPos gearboxPos      = util.grid().at(3, 1, 2);
        BlockPos largeCogEast    = util.grid().at(5, 1, 2);
        BlockPos largeCogSouth   = util.grid().at(3, 1, 4);
        BlockPos speedometerWest = util.grid().at(1, 1, 2);
        BlockPos speedometerNorth = util.grid().at(3, 1, 0);

        Selection base       = util.select().layer(0);
        Selection gearboxSel = util.select().position(gearboxPos);

        Selection ewInput    = util.select().fromTo(4, 1, 2, 5, 1, 2);
        Selection ewOutput   = util.select().fromTo(1, 1, 2, 2, 1, 2);
        Selection ewLane     = util.select().fromTo(1, 1, 2, 5, 1, 2);
        Selection ewEastHalf = util.select().fromTo(4, 1, 2, 5, 1, 2);

        Selection nsInput    = util.select().fromTo(3, 1, 3, 3, 1, 4);
        Selection nsOutput   = util.select().fromTo(3, 1, 0, 3, 1, 1);
        Selection nsLane     = util.select().fromTo(3, 1, 0, 3, 1, 4);

        // -----------------------------------------------------GEARBOX INTRO-----------------------------------------------------
        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(gearboxSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("The Smart Gearbox carries two completely independent kinetic lanes — East/West and North/South — in a single block")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        // ------------------------------------EAST WEST DRIVE------------------------------------
        scene.world().showSection(ewInput, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(ewOutput, Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(ewLane, 64.0f);
        scene.effects().rotationSpeedIndicator(largeCogEast);
        scene.effects().rotationSpeedIndicator(speedometerWest);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("Rotation enters on the East face and exits unchanged on the West — the E/W lane passes straight through along the X axis")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(gearboxPos, Direction.EAST));
        ctPause(scene);

        // ------------------------------------NORTH SOUTH DRIVE------------------------------------
        scene.world().showSection(nsInput, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(nsOutput, Direction.DOWN);
        scene.idle(5);
        scene.world().setKineticSpeed(nsLane, -48.0f);
        scene.effects().rotationSpeedIndicator(largeCogSouth);
        scene.effects().rotationSpeedIndicator(speedometerNorth);

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("The North/South lane runs simultaneously at a different speed and opposite direction without any interference")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(gearboxPos, Direction.SOUTH));
        ctPause(scene);

        // ------------------------------------PASSTHROUGH CONTROL------------------------------------
        scene.addKeyframe();
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text("Two independent lanes, two separate stress budgets — ideal for compact multi-axis machines and flight controllers")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        scene.overlay().showControls(
                util.vector().blockSurface(gearboxPos, Direction.EAST), Pointing.LEFT, 40)
            .withItem(ItemStack.EMPTY)
            .rightClick();
        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putBoolean("InvertE", true);
        });
        scene.world().setKineticSpeed(ewEastHalf, -64.0f);
        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.MEDIUM)
            .text("Use any horizontal face to invert its output direction — useful for paired bearings that must counter-rotate")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(gearboxPos, Direction.EAST));
        ctPause(scene);

        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putBoolean("InvertE", false);
            nbt.putString("OperationMode", "passthrough");
        });
        scene.world().setKineticSpeed(ewEastHalf, 64.0f);
        ctTooltip(scene)
            .colored(PonderPalette.WHITE)
            .text("Force Passthrough mode to keep both lanes active at all times, bypassing any attached sensor or computer input")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the gearbox servo
    public static void gearboxServo(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bidirectional_gearbox.servo", "Smart Gearbox \u2013 Servo Mode");
        scene.configureBasePlate(0, 0, 5);

        // Setup the gearbox, sensor and bearing outputs
        BlockPos gearboxPos     = util.grid().at(2, 2, 2);
        BlockPos cogWestPos     = util.grid().at(1, 2, 2);
        BlockPos cogEastPos     = util.grid().at(3, 2, 2);
        BlockPos cogSouthPos    = util.grid().at(2, 2, 1);
        BlockPos cogNorthPos    = util.grid().at(2, 2, 3);
        BlockPos gyroLinkPos    = util.grid().at(2, 3, 2);
        BlockPos gimbalPos      = util.grid().at(4, 3, 4);
        BlockPos bearingWestPos  = util.grid().at(1, 1, 2);
        BlockPos bearingEastPos  = util.grid().at(3, 1, 2);
        BlockPos bearingNorthPos = util.grid().at(2, 1, 1);
        BlockPos bearingSouthPos = util.grid().at(2, 1, 3);

        Selection base                   = util.select().layer(0);
        Selection gearboxSel             = util.select().position(gearboxPos);

        Selection gearboxAndCogs         = util.select().fromTo(1, 2, 1, 3, 2, 3);
        Selection gyroLinkSel            = util.select().position(gyroLinkPos);
        Selection gimbalSel              = util.select().position(gimbalPos);

        Selection allBearingsAndThrusters = util.select().fromTo(0, 1, 0, 4, 1, 4);
        Selection bearingWestSel         = util.select().position(bearingWestPos);
        Selection bearingEastSel         = util.select().position(bearingEastPos);
        Selection bearingNorthSel        = util.select().position(bearingNorthPos);
        Selection bearingSouthSel        = util.select().position(bearingSouthPos);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(gearboxAndCogs, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .text("In Servo Mode the Smart Gearbox drives each of its four faces to an independent target angle based on live orientation input")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        // Show the live orientation source
        scene.world().showSection(gyroLinkSel, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(gimbalSel, Direction.DOWN);
        scene.idle(5);

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .text("An Advanced Data Link placed directly above connects the gearbox to a Gimbal Sensor or another orientation source")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gyroLinkPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.WHITE)
            .text("The Gimbal Sensor reports live tilt angles — the gearbox continuously updates all four face outputs to match")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gimbalPos));
        ctPause(scene);

        // Link every gearbox face to its bearing
        scene.world().showSection(allBearingsAndThrusters, Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putString("OperationMode", "servo");
            nbt.putBoolean("ServoModeActive", true);
            nbt.putBoolean("AngleControlActive", true);
            nbt.putBoolean("GyroSourcePresent", true);
        });

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("A Servo Bearing on each face receives that face's servo angle — the gearbox cog output spins while the bearing arm slews to its target")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(gearboxPos, Direction.WEST));
        ctPause(scene);

        // Drive pitch through the north and south faces
        scene.addKeyframe();

        scene.world().modifyBlock(gimbalPos,
            state -> state.hasProperty(BlockStateProperties.AXIS)
                ? state.setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                : state, false);
        scene.effects().rotationSpeedIndicator(gimbalPos);

        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putDouble("AngleN", -20.0);
            nbt.putDouble("AngleS",  20.0);
            nbt.putDouble("AngleE",   0.0);
            nbt.putDouble("AngleW",   0.0);
        });

        scene.world().setKineticSpeed(gearboxAndCogs, 32.0f);
        scene.world().modifyBlockEntityNBT(bearingNorthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putString("ControlMode", "COMPUTER");
            nbt.putBoolean("ComputerOverrideActive", true);
            nbt.putDouble("ComputerTargetAngleDeg", -20.0);
        });
        scene.world().modifyBlockEntityNBT(bearingSouthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putString("ControlMode", "COMPUTER");
            nbt.putBoolean("ComputerOverrideActive", true);
            nbt.putDouble("ComputerTargetAngleDeg",  20.0);
        });
        scene.world().modifyBlockEntityNBT(bearingEastSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putString("ControlMode", "COMPUTER");
            nbt.putBoolean("ComputerOverrideActive", true);
            nbt.putDouble("ComputerTargetAngleDeg",   0.0);
        });
        scene.world().modifyBlockEntityNBT(bearingWestSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putString("ControlMode", "COMPUTER");
            nbt.putBoolean("ComputerOverrideActive", true);
            nbt.putDouble("ComputerTargetAngleDeg",   0.0);
        });
        scene.idle(15);

        scene.world().setKineticSpeed(gearboxAndCogs, 0.0f);
        scene.idle(10);

        // Drive roll through the east and west faces
        scene.world().modifyBlock(gimbalPos,
            state -> state.hasProperty(BlockStateProperties.AXIS)
                ? state.setValue(BlockStateProperties.AXIS, Direction.Axis.Z)
                : state, false);
        scene.effects().rotationSpeedIndicator(gimbalPos);

        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putDouble("AngleN",   0.0);
            nbt.putDouble("AngleS",   0.0);
            nbt.putDouble("AngleE",  30.0);
            nbt.putDouble("AngleW", -30.0);
        });
        scene.world().setKineticSpeed(gearboxAndCogs, -32.0f);
        scene.world().modifyBlockEntityNBT(bearingNorthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg",   0.0);
        });
        scene.world().modifyBlockEntityNBT(bearingSouthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg",   0.0);
        });
        scene.world().modifyBlockEntityNBT(bearingEastSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg",  30.0);
        });
        scene.world().modifyBlockEntityNBT(bearingWestSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg", -30.0);
        });
        scene.idle(15);
        scene.world().setKineticSpeed(gearboxAndCogs, 0.0f);
        scene.idle(10);

        // Drive both axes together
        scene.world().modifyBlock(gimbalPos,
            state -> state.hasProperty(BlockStateProperties.AXIS)
                ? state.setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                : state, false);
        scene.effects().rotationSpeedIndicator(gimbalPos);

        scene.world().modifyBlockEntityNBT(gearboxSel, BiDirectionalGearboxBlockEntity.class, nbt -> {
            nbt.putDouble("AngleN", -25.0);
            nbt.putDouble("AngleS",  25.0);
            nbt.putDouble("AngleE", -35.0);
            nbt.putDouble("AngleW",  35.0);
        });
        scene.world().setKineticSpeed(gearboxAndCogs, 32.0f);
        scene.world().modifyBlockEntityNBT(bearingNorthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg", -25.0);
        });
        scene.world().modifyBlockEntityNBT(bearingSouthSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg",  25.0);
        });
        scene.world().modifyBlockEntityNBT(bearingEastSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg", -35.0);
        });
        scene.world().modifyBlockEntityNBT(bearingWestSel, ThrusterBearingBlockEntity.class, nbt -> {
            nbt.putDouble("ComputerTargetAngleDeg",  35.0);
        });
        scene.idle(15);
        scene.world().setKineticSpeed(gearboxAndCogs, 0.0f);
        scene.idle(10);

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .text("All four bearings and their Thrusters track the sensor tilt simultaneously — each face pair maps to one axis of rotation")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        // Finish with ComputerCraft control
        scene.addKeyframe();
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text("A ComputerCraft peripheral on the gearbox lets scripts call setFaceAngle() for fully programmatic per-face control")
            .placeNearTarget()
            .pointAt(util.vector().topOf(gearboxPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the variable transmission scaling
    public static void variableTransmissionScaling(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("variable_transmission.scaling", "Variable Transmission Scaling");
        scene.configureBasePlate(0, 0, 5);

        BlockPos largeCogPos    = util.grid().at(5, 1, 2);
        BlockPos transPos       = util.grid().at(4, 1, 2);
        BlockPos shaftPos       = util.grid().at(3, 1, 2);
        BlockPos leverPos       = util.grid().at(4, 1, 1);

        Selection base          = util.select().layer(0);
        Selection cogSection    = util.select().fromTo(5, 0, 1, 5, 1, 2);
        Selection transAndLever = util.select().fromTo(4, 1, 1, 4, 1, 2);
        Selection outputSection = util.select().fromTo(2, 1, 2, 3, 1, 2);
        Selection kineticChain  = util.select().fromTo(2, 1, 2, 5, 1, 2);
        Selection outputOnly    = util.select().fromTo(2, 1, 2, 3, 1, 2);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(cogSection, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(transAndLever, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(outputSection, Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlock(transPos,
            state -> state.setValue(VariableTransmissionBlock.POWER, 15), false);
        scene.world().setKineticSpeed(kineticChain, 128.0f);
        scene.effects().rotationSpeedIndicator(largeCogPos);
        scene.effects().rotationSpeedIndicator(shaftPos);
        ctTooltip(scene)
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(transPos, Direction.UP))
            .text(Component.translatable("createthrusters.ponder.variable_transmission.scaling.text_1").getString());
        ctPause(scene);

        scene.world().modifyBlock(transPos,
            state -> state.setValue(VariableTransmissionBlock.POWER, 0), false);
        scene.effects().indicateRedstone(transPos);
        scene.world().setKineticSpeed(outputOnly, 0.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(leverPos, Direction.UP))
            .text(Component.translatable("createthrusters.ponder.variable_transmission.scaling.text_2").getString());
        ctPause(scene);

        scene.world().modifyBlock(transPos,
            state -> state.setValue(VariableTransmissionBlock.POWER, 8), false);
        scene.effects().indicateRedstone(transPos);
        scene.world().setKineticSpeed(outputOnly, 68.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.MEDIUM)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(leverPos, Direction.UP))
            .text(Component.translatable("createthrusters.ponder.variable_transmission.scaling.text_3").getString());
        ctPause(scene);

        scene.world().modifyBlock(transPos,
            state -> state.setValue(VariableTransmissionBlock.POWER, 15), false);
        scene.effects().indicateRedstone(transPos);
        scene.world().setKineticSpeed(outputOnly, 128.0f);
        scene.effects().rotationSpeedIndicator(shaftPos);
        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(leverPos, Direction.UP))
            .text(Component.translatable("createthrusters.ponder.variable_transmission.scaling.text_4").getString());
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the joystick controls
    public static void joystickControls(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("analogue_joystick.controls", "Analogue Joystick Controls");

        BlockPos joystickPos = util.grid().at(3, 1, 3);
        BlockPos nixieTubeNorth = util.grid().at(3, 1, 0);
        BlockPos nixieTubeSouth = util.grid().at(3, 1, 6);
        BlockPos nixieTubeEast = util.grid().at(6, 1, 3);
        BlockPos nixieTubeWest = util.grid().at(0, 1, 3);
        BlockPos redstoneLinkNorth = util.grid().at(3, 1, 1);
        BlockPos redstoneLinkSouth = util.grid().at(3, 1, 5);
        BlockPos redstoneLinkEast = util.grid().at(5, 1, 3);
        BlockPos redstoneLinkWest = util.grid().at(1, 1, 3);

        Selection base = util.select().layer(0);
        Selection joystickSel = util.select().position(joystickPos);
        Selection redstoneLinks = util.select()
            .position(redstoneLinkNorth)
            .add(util.select().position(redstoneLinkSouth))
            .add(util.select().position(redstoneLinkEast))
            .add(util.select().position(redstoneLinkWest));
        Selection nixieTubes = util.select()
            .position(nixieTubeNorth)
            .add(util.select().position(nixieTubeSouth))
            .add(util.select().position(nixieTubeEast))
            .add(util.select().position(nixieTubeWest));

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(joystickSel, Direction.DOWN);
        scene.world().showSection(redstoneLinks, Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(nixieTubes, Direction.DOWN);
        scene.idle(8);

        // -----------------------------------------------------DISPLAY LINKS-----------------------------------------------------
        scene.world().modifyBlock(nixieTubeNorth,
            state -> state.hasProperty(NixieTubeBlock.FACING)
                ? state.setValue(NixieTubeBlock.FACING, state.getValue(NixieTubeBlock.FACING).getClockWise())
                : state,
            false);
        scene.world().modifyBlock(nixieTubeSouth,
            state -> state.hasProperty(NixieTubeBlock.FACING)
                ? state.setValue(NixieTubeBlock.FACING, state.getValue(NixieTubeBlock.FACING).getClockWise())
                : state,
            false);
        scene.world().modifyBlock(nixieTubeEast,
            state -> state.hasProperty(NixieTubeBlock.FACING)
                ? state.setValue(NixieTubeBlock.FACING, state.getValue(NixieTubeBlock.FACING).getClockWise())
                : state,
            false);
        scene.world().modifyBlock(nixieTubeWest,
            state -> state.hasProperty(NixieTubeBlock.FACING)
                ? state.setValue(NixieTubeBlock.FACING, state.getValue(NixieTubeBlock.FACING).getClockWise())
                : state,
            false);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(joystickPos).add(0, 0.5, 0), Pointing.UP, 40)
            .withItem(ItemStack.EMPTY);
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        scene.idle(20);

        // ------------------------------------HORIZONTAL INPUT------------------------------------
        scene.effects().indicateRedstone(nixieTubeEast);

        scene.world().modifyBlockEntityNBT(util.select().position(nixieTubeEast), NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 0));
        animateNixieSignal(scene, util.select().position(nixieTubeEast), 1, 15, 2, 2);
        scene.idle(20);
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubeEast));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(joystickPos).add(0, 0.5, 0), Pointing.LEFT, 40)
            .withItem(ItemStack.EMPTY);
        ctTooltip(scene)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_5").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        scene.idle(20);

        // ------------------------------------VERTICAL INPUT------------------------------------
        scene.effects().indicateRedstone(nixieTubeNorth);

        scene.world().modifyBlockEntityNBT(util.select().position(nixieTubeNorth), NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 0));
        animateNixieSignal(scene, util.select().position(nixieTubeNorth), 1, 15, 2, 2);
        scene.idle(20);
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_6").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubeNorth));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().topOf(joystickPos), Pointing.UP, 40)
            .rightClick();
        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_7").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        ctTooltip(scene)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_8").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        scene.addKeyframe();
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_9").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        scene.idle(20);
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_joystick.controls.text_10").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the controller channels
    public static void controllerChannels(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("analogue_contraption_controller.channels", "Controller Channels");

        // Setup the controller and wireless output
        BlockPos controllerPos = util.grid().at(2, 1, 3);
        BlockPos redstoneLinkPos = util.grid().at(1, 1, 5);
        BlockPos nixieTubePos = util.grid().at(2, 1, 5);
        BlockPos joystickPos = util.grid().at(1, 1, 2);
        BlockPos cogPos = util.grid().at(3, 1, 0);
        BlockPos bearingPos = util.grid().at(3, 1, 1);
        BlockPos thrusterPos1 = util.grid().at(4, 1, 3);
        BlockPos thrusterPos2 = util.grid().at(3, 2, 1);
        BlockPos redstoneLampPos = util.grid().at(4, 1, 5);

        Selection base = util.select().layer(0);
        Selection controllerSel = util.select().position(controllerPos);
        Selection redstoneLinkSel = util.select().position(redstoneLinkPos);
        Selection nixieSel = util.select().position(nixieTubePos);
        Selection joystickSel = util.select().position(joystickPos);
        Selection kinetics = util.select()
            .position(cogPos).add(util.select().position(bearingPos))
            .add(util.select().position(thrusterPos1)).add(util.select().position(thrusterPos2));
        Selection bearingSel = util.select().position(bearingPos);
        Selection lampSel = util.select().position(redstoneLampPos);

        Selection stageOne = controllerSel.add(redstoneLinkSel).add(nixieSel);

        scene.world().showSection(base, Direction.UP);
        scene.idle(5);
        scene.world().showSection(stageOne, Direction.DOWN);
        scene.idle(8);

        scene.world().modifyBlock(nixieTubePos,
            state -> state.hasProperty(NixieTubeBlock.FACING)
                ? state.setValue(NixieTubeBlock.FACING, state.getValue(NixieTubeBlock.FACING).getClockWise())
                : state,
            false);

        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(redstoneLinkPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubePos));
        ctPause(scene);

        scene.addKeyframe();
        scene.overlay().showControls(
                util.vector().blockSurface(controllerPos, Direction.NORTH), Pointing.RIGHT, 40)
            .rightClick()
            .withItem(ItemStack.EMPTY);
        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(controllerPos, Direction.NORTH));
        ctPause(scene);

        // Show analogue redstone scaling
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_5").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubePos));
        scene.effects().indicateRedstone(nixieTubePos);

        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 0));
        animateNixieSignal(scene, nixieSel, 1, 15, 1, 2);
        ctPause(scene);

        // Show stepped and held channel modes
        scene.addKeyframe();
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_6").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubePos));
        scene.effects().indicateRedstone(nixieTubePos);

        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 0));
        scene.idle(6);
        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 5));
        scene.idle(6);
        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 10));
        scene.idle(6);
        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 15));
        scene.idle(6);
        ctPause(scene);

        scene.addKeyframe();
        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_7").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(nixieTubePos));

        scene.world().modifyBlockEntityNBT(nixieSel, NixieTubeBlockEntity.class,
            nbt -> nbt.putInt("RedstoneStrength", 15));
        scene.idle(20);
        ctPause(scene);

        // Bind joystick input to the controller
        scene.addKeyframe();
        scene.world().showSection(joystickSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_8").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(joystickPos));
        ctPause(scene);

        ctTooltip(scene)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_9").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        ctPause(scene);

        // Drive kinetic and thruster outputs
        scene.addKeyframe();
        scene.world().showSection(kinetics, Direction.DOWN);
        scene.idle(10);

        scene.rotateCameraY(-90.0f);

        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_10").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(bearingPos));
        ctPause(scene);

        scene.world().setKineticSpeed(kinetics, 64.0f);
        scene.effects().rotationSpeedIndicator(cogPos);
        scene.idle(20);

        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_11").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(thrusterPos1));
        scene.idle(15);
        scene.world().setKineticSpeed(kinetics, 0.0f);
        ctPause(scene);

        // Swap to a direct lamp output
        scene.addKeyframe();

        scene.rotateCameraY(90.0f);

        scene.world().setKineticSpeed(kinetics, 0.0f);
        scene.world().hideSection(kinetics, Direction.UP);
        scene.world().hideSection(joystickSel, Direction.UP);
        scene.world().hideSection(redstoneLinkSel, Direction.UP);
        scene.world().hideSection(nixieSel, Direction.UP);
        scene.idle(10);

        scene.world().showSection(lampSel, Direction.DOWN);
        scene.idle(8);

        ctTooltip(scene)
            .attachKeyFrame()
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_12").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(controllerPos));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_13").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(redstoneLampPos));
        ctPause(scene);

        // Apply the direct block signal
        scene.effects().indicateRedstone(redstoneLampPos);
        scene.idle(10);

        scene.world().modifyBlock(redstoneLampPos,
            state -> state.hasProperty(BlockStateProperties.POWERED)
                ? state.setValue(BlockStateProperties.POWERED, true)
                : state.hasProperty(BlockStateProperties.LIT)
                    ? state.setValue(BlockStateProperties.LIT, true)
                    : state,
            false);
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.analogue_contraption_controller.channels.text_14").getString())
            .placeNearTarget()
            .pointAt(util.vector().topOf(redstoneLampPos));
        ctPause(scene);

        scene.markAsFinished();
    }

    // Handle the physics gantry
    public static void physicsGantry(SceneBuilder builder, SceneBuildingUtil util) {
        // -----------------------------------------------------SCENE SETUP-----------------------------------------------------
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("physics_gantry.basics", "Physics Gantry");
        scene.configureBasePlate(0, 1, 5);
        scene.setSceneOffsetY(-1.5f);
        scene.rotateCameraY(180.0f);
        ItemStack beltConnector = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse("create:belt_connector"))
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);

        BlockPos shaftStart = util.grid().at(3, 7, 3);
        BlockPos shaftMiddle = util.grid().at(3, 7, 4);
        BlockPos shaftEnd = util.grid().at(3, 7, 5);
        BlockPos carriagePos = util.grid().at(3, 6, 4);
        BlockPos driverWheelPos = util.grid().at(2, 6, 1);
        BlockPos receiverWheelPos = util.grid().at(2, 6, 4);

        Selection base = util.select().fromTo(0, 0, 1, 4, 0, 5);
        Selection gantryShaft = util.select().fromTo(shaftStart, shaftEnd);
        Selection carriageOnly = util.select().position(carriagePos);
        Selection driverWheel = util.select().position(driverWheelPos);
        Selection receiverWheel = util.select().position(receiverWheelPos);
        Selection driveTower = util.select().fromTo(3, 0, 0, 3, 7, 2)
            .add(util.select().position(2, 7, 1));
        Selection lowerRelay = util.select().position(3, 5, 4)
            .add(util.select().position(3, 5, 5));
        Selection carriageAndReceiverWheel = carriageOnly.add(receiverWheel);
        Selection movingCarriageAssembly = carriageAndReceiverWheel.add(lowerRelay);
        Selection allKinetics = gantryShaft.add(carriageOnly).add(driverWheel).add(receiverWheel)
            .add(driveTower).add(lowerRelay);

        // -----------------------------------------------------GANTRY SHAFT-----------------------------------------------------
        scene.world().showSection(base, Direction.UP);
        scene.idle(8);
        scene.world().showSection(driveTower, Direction.DOWN);
        scene.world().setKineticSpeed(driveTower, 32.0f);
        scene.idle(8);

        scene.world().showSection(gantryShaft, Direction.DOWN);
        scene.world().setKineticSpeed(gantryShaft, 32.0f);
        scene.idle(12);
        scene.overlay().showOutline(PonderPalette.GREEN, "physics_gantry_shaft", gantryShaft, 70);
        ctTooltip(scene)
            .attachKeyFrame()
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_1").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(shaftMiddle));
        ctPause(scene);

        ctTooltip(scene)
            .colored(PonderPalette.MEDIUM)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_2").getString())
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(shaftEnd, Direction.SOUTH));
        ctPause(scene);

        scene.addKeyframe();
        // ------------------------------------CARRIAGE MOVEMENT------------------------------------
        ElementLink<WorldSectionElement> carriage = scene.world().showIndependentSection(carriageOnly, Direction.UP);
        scene.world().setKineticSpeed(carriageOnly, 32.0f);
        scene.idle(12);
        ctTooltip(scene)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_3").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(carriagePos));
        ctPause(scene);

        scene.world().moveSection(carriage, util.vector().of(0.0, 0.0, -1.0), 35);
        scene.effects().rotationSpeedIndicator(carriagePos);
        scene.idle(35);
        scene.world().moveSection(carriage, util.vector().of(0.0, 0.0, 1.0), 35);
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_4").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(carriagePos));
        scene.idle(35);
        ctPause(scene);
        scene.world().hideIndependentSection(carriage, Direction.DOWN);
        scene.idle(8);

        scene.addKeyframe();
        // -----------------------------------------------------BELT WHEELS-----------------------------------------------------
        scene.rotateCameraY(90.0f);
        scene.world().showSection(driverWheel, Direction.EAST);
        scene.world().setKineticSpeed(driveTower.add(driverWheel), 48.0f);
        scene.idle(12);
        scene.overlay().showControls(util.vector().topOf(driverWheelPos), Pointing.DOWN, 45)
            .rightClick()
            .withItem(beltConnector);
        ctTooltip(scene)
            .colored(PonderPalette.OUTPUT)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_5").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(driverWheelPos));
        ctPause(scene);

        ElementLink<WorldSectionElement> movingCarriage = scene.world()
            .showIndependentSection(movingCarriageAssembly, Direction.UP);
        scene.world().setKineticSpeed(movingCarriageAssembly, 48.0f);
        scene.idle(12);
        scene.world().moveSection(movingCarriage, util.vector().of(0.0, 0.0, -1.0), 35);
        scene.effects().rotationSpeedIndicator(receiverWheelPos);
        scene.idle(35);
        scene.world().moveSection(movingCarriage, util.vector().of(0.0, 0.0, 1.0), 35);
        ctTooltip(scene)
            .colored(PonderPalette.GREEN)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_6").getString())
            .placeNearTarget()
            .pointAt(util.vector().centerOf(carriagePos));
        scene.idle(35);
        ctPause(scene);
        scene.world().hideIndependentSection(movingCarriage, Direction.DOWN);
        scene.idle(8);

        // -----------------------------------------------------BELT DRIVE-----------------------------------------------------
        scene.world().showSection(movingCarriageAssembly, Direction.UP);
        scene.world().setKineticSpeed(allKinetics, 48.0f);
        scene.world().modifyBlockEntityNBT(driverWheel, PhysicsGantryBeltWheelBlockEntity.class, nbt -> {
            nbt.putLong("LinkedPos", receiverWheelPos.asLong());
            nbt.putBoolean("ReceivesFromLinkedWheel", false);
            nbt.putFloat("GeneratedLinkSpeed", 0.0f);
        }, true);
        scene.world().modifyBlockEntityNBT(receiverWheel, PhysicsGantryBeltWheelBlockEntity.class, nbt -> {
            nbt.putLong("LinkedPos", driverWheelPos.asLong());
            nbt.putBoolean("ReceivesFromLinkedWheel", true);
            nbt.putFloat("GeneratedLinkSpeed", 48.0f);
        }, true);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(receiverWheelPos), Pointing.DOWN, 45)
            .rightClick()
            .withItem(beltConnector);
        ctTooltip(scene)
            .colored(PonderPalette.BLUE)
            .text(Component.translatable("createthrusters.ponder.physics_gantry.basics.text_7").getString())
            .placeNearTarget()
            .pointAt(util.vector().of(2.0, 6.5, 2.5));
        ctPause(scene);

        scene.effects().rotationSpeedIndicator(driverWheelPos);
        scene.effects().rotationSpeedIndicator(receiverWheelPos);
        scene.idle(30);
        scene.world().setKineticSpeed(allKinetics, 0.0f);
        scene.markAsFinished();
    }

    // Handle the physics staff usage
    public static void physicsStaffUsage(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("physics_staff.usage", "Physics Staff Usage");
        scene.configureBasePlate(0, 0, 5);
        scene.markAsFinished();
    }
}
