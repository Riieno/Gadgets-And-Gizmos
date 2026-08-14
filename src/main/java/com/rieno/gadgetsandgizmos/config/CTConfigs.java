package com.rieno.gadgetsandgizmos.config;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.neoforge.CTCommonEvents;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

// Define every common, server and client setting exposed by the addon
public final class CTConfigs {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Client CLIENT;
    public static final Common COMMON;
    public static final Server SERVER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec SERVER_SPEC;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared feature toggle snapshot
    private static volatile FeatureToggleSnapshot featureToggleSnapshot;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();

        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        featureToggleSnapshot = defaultFeatureToggleSnapshot();
    }

    // Initialize the CT configs
    private CTConfigs() {
    }

    // Register the CT configs
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CreateThrusters.MOD_ID + "-client.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, CreateThrusters.MOD_ID + "-common.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, CreateThrusters.MOD_ID + "-server.toml");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the load event
    public static void onLoad(ModConfigEvent.Loading evt) {
        syncServerFeatureConfig(evt.getConfig());
    }

    // Handle the reload event
    public static void onReload(ModConfigEvent.Reloading evt) {
        syncServerFeatureConfig(evt.getConfig());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the advanced controller V2 UI
    public static void setAdvancedControllerV2Ui(boolean enabled) {
        CLIENT.advancedControllerV2Ui.set(enabled);
        CLIENT_SPEC.save();
    }

    // Define the feature group
    private static Map<String, ModConfigSpec.ConfigValue<Boolean>> defineFeatureGroup(ModConfigSpec.Builder builder,
                                                                                      String group,
                                                                                      Map<String, Boolean> defaults) {
        Map<String, ModConfigSpec.ConfigValue<Boolean>> values = new LinkedHashMap<>();
        builder.push(group);
        for (Map.Entry<String, Boolean> entry : defaults.entrySet()) {
            if ("diagnostic_tablet".equals(entry.getKey())) {
                continue;
            }
            String featureLabel = CTFeatureToggles.featureLabel(entry.getKey());
            values.put(entry.getKey(), builder
                    .comment("Allow " + featureLabel + " on this server. Existing content is not removed automatically.")
                    .define(entry.getKey(), entry.getValue()));
        }
        builder.pop();
        return Map.copyOf(values);
    }

    // Sync the server feature config
    private static synchronized void syncServerFeatureConfig(ModConfig config) {
        if (config.getSpec() == SERVER_SPEC) {
            FeatureToggleSnapshot current = featureToggleSnapshot;
            Map<String, Boolean> blocks = new LinkedHashMap<>(
                    snapshotFeatureValues(SERVER.blockFeatures, CTFeatureToggles.blockDefaults()));
            Map<String, Boolean> items = new LinkedHashMap<>(
                    snapshotFeatureValues(SERVER.itemFeatures, CTFeatureToggles.itemDefaults()));
            boolean tabletEnabled = current.blocks().getOrDefault("diagnostic_tablet", true);
            blocks.put("diagnostic_tablet", tabletEnabled);
            items.put("diagnostic_tablet", tabletEnabled);
            featureToggleSnapshot = new FeatureToggleSnapshot(
                    Map.copyOf(blocks), Map.copyOf(items),
                    snapshotFeatureValues(SERVER.entityFeatures, CTFeatureToggles.entityDefaults()));
        } else if (config.getSpec() == COMMON_SPEC) {
            FeatureToggleSnapshot current = featureToggleSnapshot;
            Map<String, Boolean> blocks = new LinkedHashMap<>(current.blocks());
            Map<String, Boolean> items = new LinkedHashMap<>(current.items());
            boolean tabletEnabled = Boolean.TRUE.equals(COMMON.enableDiagnosticTablet.get());
            blocks.put("diagnostic_tablet", tabletEnabled);
            items.put("diagnostic_tablet", tabletEnabled);
            featureToggleSnapshot = new FeatureToggleSnapshot(
                    Map.copyOf(blocks), Map.copyOf(items), current.entities());
        } else {
            return;
        }
        FeatureToggleSnapshot snapshot = featureToggleSnapshot;
        CTFeatureToggles.applyServerOverrides(snapshot.blocks(), snapshot.items(), snapshot.entities());
        CTCommonEvents.syncFeatureTogglesToAllPlayers();
    }

    // Create the feature toggle snapshot
    public static FeatureToggleSnapshot createFeatureToggleSnapshot() {
        return featureToggleSnapshot;
    }

    // Create the default feature toggle snapshot
    private static FeatureToggleSnapshot defaultFeatureToggleSnapshot() {
        return new FeatureToggleSnapshot(
                CTFeatureToggles.blockDefaults(),
                CTFeatureToggles.itemDefaults(),
                CTFeatureToggles.entityDefaults());
    }

    // Get the snapshot feature values
    private static Map<String, Boolean> snapshotFeatureValues(
            Map<String, ModConfigSpec.ConfigValue<Boolean>> configuredValues,
            Map<String, Boolean> defaults) {
        Map<String, Boolean> snapshot = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, ModConfigSpec.ConfigValue<Boolean>> entry : configuredValues.entrySet()) {
            snapshot.put(entry.getKey(), Boolean.TRUE.equals(entry.getValue().get()));
        }
        return Map.copyOf(snapshot);
    }

    // Keep visual and input settings on the client
    public static final class Client {
        // Show detailed goggle tooltips
        public final ModConfigSpec.BooleanValue showDetailedGoggleTooltips;
        // Show shipping manifest goggle tooltip
        public final ModConfigSpec.BooleanValue showShippingManifestGoggleTooltip;
        // Render shipping manifest contents
        public final ModConfigSpec.BooleanValue renderShippingManifestContents;
        // Show link status chat
        public final ModConfigSpec.BooleanValue showLinkStatusChat;
        // Enable mannequin poser GUI
        public final ModConfigSpec.BooleanValue enableMannequinPoserGui;
        // Prefer straw statues poser GUI
        public final ModConfigSpec.BooleanValue preferStrawStatuesPoserGui;
        // Advanced controller V2 UI
        public final ModConfigSpec.BooleanValue advancedControllerV2Ui;
        // Thruster max volume
        public final ModConfigSpec.DoubleValue thrusterMaxVolume;
        // Thruster particle scale
        public final ModConfigSpec.DoubleValue thrusterParticleScale;
        // Claw marker render mode
        public final ModConfigSpec.EnumValue<ClawMarkerRenderMode> clawMarkerRenderMode;

        // Initialize the client
        private Client(ModConfigSpec.Builder builder) {
            builder.comment("Client-side visuals and UX").push("client");
            showDetailedGoggleTooltips = builder
                    .comment("Legacy option; addon goggle tooltip details now follow Create's Sneak detail behavior")
                    .define("showDetailedGoggleTooltips", false);
            showShippingManifestGoggleTooltip = builder
                    .comment("Show the attached inventory preview when looking at a Shipping Manifest while wearing Create goggles")
                    .define("showShippingManifestGoggleTooltip", true);
            renderShippingManifestContents = builder
                    .comment("Render the attached inventory contents on placed Shipping Manifests and enable mouse-wheel scrolling while looking at them")
                    .define("renderShippingManifestContents", true);
            showLinkStatusChat = builder
                    .comment("Show gyro/link status messages in chat when interacting with blocks")
                    .define("showLinkStatusChat", true);
            enableMannequinPoserGui = builder
                    .comment("Enable Mannequin Poser GUI")
                    .define("enableMannequinPoserGui", true);
            preferStrawStatuesPoserGui = builder
                    .comment("Use the StrawStatues/Statue Menus armor stand GUI for armor stands and player mannequins when StrawStatues is installed. StrawStatues entities always keep their own GUI.")
                    .define("preferStrawStatuesPoserGui", true);
            advancedControllerV2Ui = builder
                    .comment("Use the optional V2 node graph interface for Advanced Contraption Controllers")
                    .define("advancedControllerV2Ui", false);
            thrusterMaxVolume = builder
                    .comment("Maximum local volume multiplier for thruster sounds (0 disables local thruster audio)")
                    .defineInRange("thrusterMaxVolume", 1.0D, 0.0D, 2.0D);
            thrusterParticleScale = builder
                    .comment("Local thruster particle multiplier (0 disables local thruster particles)")
                    .defineInRange("thrusterParticleScale", 1.0D, 0.0D, 1.0D);
            clawMarkerRenderMode = builder
                    .comment("Claw marker render mode is disabled; OFF is enforced")
                    .defineEnum("clawMarkerRenderMode", ClawMarkerRenderMode.OFF);
            builder.pop();
        }
    }

        // Store the feature toggle snapshot
        public record FeatureToggleSnapshot(Map<String, Boolean> blocks,
                                                                                Map<String, Boolean> items,
                                                                                Map<String, Boolean> entities) {
        }

    // Keep shared mechanics in the common config
    public static final class Common {
        // Thruster base thrust
        public final ModConfigSpec.DoubleValue thrusterBaseThrust;
        // Thruster base airflow
        public final ModConfigSpec.DoubleValue thrusterBaseAirflow;
        // Thruster base fuel use per tick
        public final ModConfigSpec.DoubleValue thrusterBaseFuelUsePerTick;
        // Thruster fuel throttle exponent
        public final ModConfigSpec.DoubleValue thrusterFuelThrottleExponent;
        // Enable thruster entity damage
        public final ModConfigSpec.BooleanValue enableThrusterEntityDamage;
        // Enable thruster mob haunting
        public final ModConfigSpec.BooleanValue enableThrusterMobHaunting;
        // Beam thrust multiplier
        public final ModConfigSpec.DoubleValue beamThrustMultiplier;
        // Oxidized fuel thrust multiplier
        public final ModConfigSpec.DoubleValue oxidizedFuelThrustMultiplier;
        // Dense fuel efficiency multiplier
        public final ModConfigSpec.DoubleValue denseFuelEfficiencyMultiplier;
        // Light fuel efficiency multiplier
        public final ModConfigSpec.DoubleValue lightFuelEfficiencyMultiplier;
        // Bearing max pivot angle in degrees
        public final ModConfigSpec.DoubleValue bearingMaxPivotAngleDeg;
        // Bearing smoothing
        public final ModConfigSpec.DoubleValue bearingSmoothing;
        // Bearing debounce tick count
        public final ModConfigSpec.IntValue bearingDebounceTicks;
        // Gearbox max static angle in degrees
        public final ModConfigSpec.DoubleValue gearboxMaxStaticAngleDeg;
        // Gearbox reverse with redstone
        public final ModConfigSpec.BooleanValue gearboxReverseWithRedstone;
        // Scissor piston max range
        public final ModConfigSpec.IntValue scissorPistonMaxRange;
        // Enable special thanks mannequin loot
        public final ModConfigSpec.BooleanValue enableSpecialThanksMannequinLoot;
        // Enable mob head drops
        public final ModConfigSpec.BooleanValue enableMobHeadDrops;
        // Focused mode FE per tick
        public final ModConfigSpec.DoubleValue focusedModeFePerTick;
        // Focused mode FE capacity
        public final ModConfigSpec.IntValue focusedModeFeCapacity;
        // Propulsion upgrade max multiplier
        public final ModConfigSpec.DoubleValue propulsionUpgradeMaxMultiplier;
        // FE at max RPM
        public final ModConfigSpec.IntValue feAtMaxRpm;
        // Maximum stress
        public final ModConfigSpec.IntValue maxStress;
        // Electric motor RPM range
        public final ModConfigSpec.IntValue electricMotorRpmRange;
        // Electric motor minimum consumption
        public final ModConfigSpec.IntValue electricMotorMinimumConsumption;
        // Electric motor max input
        public final ModConfigSpec.IntValue electricMotorMaxInput;
        // Electric motor capacity
        public final ModConfigSpec.IntValue electricMotorCapacity;
        // Alternator max output
        public final ModConfigSpec.IntValue alternatorMaxOutput;
        // Alternator capacity
        public final ModConfigSpec.IntValue alternatorCapacity;
        // Alternator efficiency
        public final ModConfigSpec.DoubleValue alternatorEfficiency;
        // Powered zipline rope attachment inertia damping
        public final ModConfigSpec.DoubleValue poweredZiplineRopeAttachmentInertiaDamping;
        // Claw can grab entities
        public final ModConfigSpec.BooleanValue clawCanGrabEntities;
        // Entity launcher max knockback
        public final ModConfigSpec.DoubleValue entityLauncherMaxKnockback;
        // Entity launcher grapple reel velocity
        public final ModConfigSpec.DoubleValue entityLauncherGrappleReelVelocity;
        // Entity launcher grapple slack pull velocity
        public final ModConfigSpec.DoubleValue entityLauncherGrappleSlackPullVelocity;
        // Entity launcher grapple max velocity
        public final ModConfigSpec.DoubleValue entityLauncherGrappleMaxVelocity;
        // Entity launcher grapple swing acceleration
        public final ModConfigSpec.DoubleValue entityLauncherGrappleSwingAcceleration;
        // Advanced controller max nodes
        public final ModConfigSpec.IntValue advancedControllerMaxNodes;
        // Enable diagnostic tablet
        public final ModConfigSpec.BooleanValue enableDiagnosticTablet;
        // Initialize the common
        private Common(ModConfigSpec.Builder builder) {
            builder.comment("Gameplay values shared between client and server").push("common");
            builder.comment("Thruster propulsion, fuel, beam mode, and upgrade scaling").push("thruster");
            thrusterBaseThrust = builder
                    .comment("Base thrust at 100% throttle for normal thrusters. Default 900.")
                    .defineInRange("thrusterBaseThrust", 900.0D, 1.0D, 100000.0D);
            thrusterBaseAirflow = builder
                    .comment("Base airflow at 100% throttle")
                    .defineInRange("thrusterBaseAirflow", 80.0D, 0.0D, 100000.0D);
            thrusterBaseFuelUsePerTick = builder
                    .comment("Base fuel usage in mB/t at 100% throttle for a neutral fuel")
                    .defineInRange("thrusterBaseFuelUsePerTick", 5.6D, 0.01D, 1000.0D);
            thrusterFuelThrottleExponent = builder
                    .comment("Fuel curve exponent: >1 makes low throttle significantly more efficient")
                    .defineInRange("thrusterFuelThrottleExponent", 1.35D, 0.1D, 4.0D);
            enableThrusterEntityDamage = builder
                    .comment("Allow active thruster exhaust to damage living entities")
                    .define("enableThrusterEntityDamage", true);
            enableThrusterMobHaunting = builder
                    .comment("Allow haunting-upgraded thrusters to convert supported mobs instead of dropping loot on death")
                    .define("enableThrusterMobHaunting", true);
            beamThrustMultiplier = builder
                    .comment("Thrust multiplier for beam (focused/lense) thruster mode. Default 1.5")
                    .defineInRange("beamThrustMultiplier", 1.5D, 0.0D, 100.0D);
            oxidizedFuelThrustMultiplier = builder
                    .comment("Thrust multiplier applied to oxidized thruster fuels")
                    .defineInRange("oxidizedFuelThrustMultiplier", 1.5D, 0.0D, 100.0D);
            denseFuelEfficiencyMultiplier = builder
                    .comment("Fuel efficiency multiplier for fluids tagged as dense thruster fuels")
                    .defineInRange("denseFuelEfficiencyMultiplier", 1.45D, 0.01D, 100.0D);
            lightFuelEfficiencyMultiplier = builder
                    .comment("Fuel efficiency multiplier for fluids tagged as light thruster fuels")
                    .defineInRange("lightFuelEfficiencyMultiplier", 1.0D, 0.01D, 100.0D);
            focusedModeFePerTick = builder
                    .comment("FE consumed per tick at 100% throttle when a thruster is in focused/beam mode. Scales linearly with throttle.")
                    .defineInRange("focusedModeFePerTick", 50.0D, 1.0D, 100000.0D);
            focusedModeFeCapacity = builder
                    .comment("Maximum FE a beam thruster can store internally")
                    .defineInRange("focusedModeFeCapacity", 100000, 1000, 10000000);
            propulsionUpgradeMaxMultiplier = builder
                    .comment("Maximum multiplier allowed from propulsion upgrades. Tiers apply 2x, 4x, 8x, and 16x before this cap.")
                    .defineInRange("propulsionUpgradeMaxMultiplier", 32.0D, 1.0D, 64.0D);
            builder.pop();

            // ------------------------------------BEARINGS / GEARBOXES------------------------------------
            builder.comment("Servo Bearing pivot controls").push("thruster_bearing");
            bearingMaxPivotAngleDeg = builder
                    .comment("Maximum pivot angle in degrees when bearing receives full redstone differential")
                    .defineInRange("bearingMaxPivotAngleDeg", 90.0D, 1.0D, 180.0D);
            bearingSmoothing = builder
                    .comment("Bearing interpolation factor per tick (0 = no movement, 1 = snap)")
                    .defineInRange("bearingSmoothing", 0.25D, 0.0D, 1.0D);
            bearingDebounceTicks = builder
                    .comment("Bearing target debounce ticks before motion updates")
                    .defineInRange("bearingDebounceTicks", 2, 0, 40);
            builder.pop();

            builder.comment("Gyroscope and gearbox controls").push("gearbox");
            gearboxMaxStaticAngleDeg = builder
                    .comment("Maximum static per-face angle reported by the bi-directional gearbox from gyro input")
                    .defineInRange("gearboxMaxStaticAngleDeg", 90.0D, 1.0D, 180.0D);
            gearboxReverseWithRedstone = builder
                    .comment("Invert gearbox gyro-derived face angles and redstone outputs when the gearbox is powered")
                    .define("gearboxReverseWithRedstone", true);
            builder.pop();

            // ------------------------------------PISTONS / LOOT------------------------------------
            builder.comment("Scissor Piston extension limits").push("scissor_piston");
            scissorPistonMaxRange = builder
                    .comment("Maximum extension range, in blocks, after adding Scissor Arms")
                    .defineInRange("maxRange", 32, 1, 32);
            builder.pop();

            builder.comment("Optional loot and entity drops").push("loot");
            enableSpecialThanksMannequinLoot = builder
                    .comment("Allow Special Thanks Player Mannequins to appear in vanilla chest loot")
                    .define("enableSpecialThanksMannequinLoot", true);
            enableMobHeadDrops = builder
                    .comment("Allow mobs and players to drop heads through the addon's head-drop system")
                    .define("enableMobHeadDrops", true);
            builder.pop();

            // -----------------------------------------------------ENERGY-----------------------------------------------------
            builder.comment("Shared kinetic and Forge Energy conversion values").push("energy");
            feAtMaxRpm = builder
                    .comment("Forge Energy conversion rate in FE/t at 256 RPM. The alternator multiplies this by its efficiency; default 5883 keeps alternator output at 5000 FE/t with 0.85 efficiency.")
                    .defineInRange("feAtMaxRpm", 5883, 0, Integer.MAX_VALUE);
            maxStress = builder
                    .comment("Maximum stress for the Alternator and Industrial Motor in SU at 256 RPM.")
                    .defineInRange("maxStress", 96800, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.comment("Industrial Motor energy input and speed limits").push("industrial_motor");
            electricMotorRpmRange = builder
                    .comment("Industrial Motor min/max RPM range.")
                    .defineInRange("motorRpmRange", 256, 1, Integer.MAX_VALUE);
            electricMotorMinimumConsumption = builder
                    .comment("Industrial Motor minimum required energy consumption in FE/t.")
                    .defineInRange("motorMinConsumption", 8, 0, Integer.MAX_VALUE);
            electricMotorMaxInput = builder
                    .comment("Industrial Motor max FE input transfer rate.")
                    .defineInRange("motorMaxInput", 5000, 0, Integer.MAX_VALUE);
            electricMotorCapacity = builder
                    .comment("Industrial Motor internal FE buffer.")
                    .defineInRange("motorCapacity", 5000, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.comment("Alternator energy output and efficiency").push("alternator");
            alternatorMaxOutput = builder
                    .comment("Alternator max FE output transfer rate.")
                    .defineInRange("generatorMaxOutput", 5000, 0, Integer.MAX_VALUE);
            alternatorCapacity = builder
                    .comment("Alternator internal FE buffer.")
                    .defineInRange("generatorCapacity", 5000, 0, Integer.MAX_VALUE);
            alternatorEfficiency = builder
                    .comment("Alternator efficiency relative to the shared FE-at-256-RPM conversion rate.")
                    .defineInRange("generatorEfficiency", 0.85D, 0.01D, 1.0D);
            builder.pop();

            // ------------------------------------MOVEMENT / INTERACTIONS------------------------------------
            builder.comment("Powered Zipline movement and rope attachment behavior").push("powered_zipline");
            poweredZiplineRopeAttachmentInertiaDamping = builder
                    .comment("Powered Zipline hanging-rope anchor motion damping while ropes are attached. 0 = instant start/stop, 1 = strongest smoothing/coasting. Higher values reduce sway from hard starts and stops.")
                    .defineInRange("poweredZiplineRopeAttachmentInertiaDamping", 0.45D, 0.0D, 1.0D);
            builder.pop();

            builder.comment("Claw block targeting and entity pickup behavior").push("claw");
            clawCanGrabEntities = builder
                    .comment("Allow the Claw block to scan for and pick up living entities")
                    .define("clawCanGrabEntities", true);
            builder.pop();

            builder.comment("Entity Launcher throw and grapple behavior").push("entity_launcher");
            entityLauncherMaxKnockback = builder
                    .comment("Maximum knockback applied when a fully charged Entity Launcher throws a held entity")
                    .defineInRange("entityLauncherMaxKnockback", 4.0D, 0.1D, 64.0D);
            entityLauncherGrappleReelVelocity = builder
                    .comment("Maximum player pull velocity per tick while actively reeling toward a grapple anchor")
                    .defineInRange("entityLauncherGrappleReelVelocity", 0.62D, 0.01D, 8.0D);
            entityLauncherGrappleSlackPullVelocity = builder
                    .comment("Maximum player pull velocity per tick when only enforcing rope slack/length without active reel")
                    .defineInRange("entityLauncherGrappleSlackPullVelocity", 0.18D, 0.0D, 8.0D);
            entityLauncherGrappleMaxVelocity = builder
                    .comment("Maximum grapple movement velocity cap applied to the player")
                    .defineInRange("entityLauncherGrappleMaxVelocity", 0.78D, 0.1D, 16.0D);
            entityLauncherGrappleSwingAcceleration = builder
                    .comment("Tangential swing acceleration applied from movement input while grappling")
                    .defineInRange("entityLauncherGrappleSwingAcceleration", 0.045D, 0.0D, 2.0D);
            builder.pop();

            // ------------------------------------CONTROLLERS / TABLETS------------------------------------
            builder.comment("Advanced Contraption Controller graph limits").push("advanced_controller");
            advancedControllerMaxNodes = builder
                    .comment("Maximum number of nodes allowed across an Advanced Contraption Controller graph and its functions")
                    .defineInRange("maxNodes", 512, 32, 4096);
            builder.pop();

            builder.comment("Smart Tablet availability").push("diagnostic_tablet");
            enableDiagnosticTablet = builder
                    .comment("Enable the Smart Tablet block, item, user interface, apps and networking")
                    .define("enabled", true);
            builder.pop();
            builder.pop();
        }
    }

    // Keep world rules and feature gates on the server
    public static final class Server {
        // Enable thruster particles
        public final ModConfigSpec.BooleanValue enableThrusterParticles;
        // Thruster particle intensity
        public final ModConfigSpec.DoubleValue thrusterParticleIntensity;
        // Enable thruster bulk processing
        public final ModConfigSpec.BooleanValue enableThrusterBulkProcessing;
        // Thruster bulk processing full throttle multiplier
        public final ModConfigSpec.DoubleValue thrusterBulkProcessingFullThrottleMultiplier;
        // Enable gyroscope linking
        public final ModConfigSpec.BooleanValue enableGyroscopeLinking;
        // Gyroscope link range
        public final ModConfigSpec.IntValue gyroscopeLinkRange;
        // Virtual orientation source timeout tick count
        public final ModConfigSpec.IntValue virtualOrientationSourceTimeoutTicks;
        // Controller orientation source max tilt in degrees
        public final ModConfigSpec.DoubleValue controllerOrientationSourceMaxTiltDegrees;
        // Allow thruster manual toggle
        public final ModConfigSpec.BooleanValue allowThrusterManualToggle;
        // Physics gantry belt wheel max distance
        public final ModConfigSpec.IntValue physicsGantryBeltWheelMaxDistance;
        // Shipping schedule work spread tick count
        public final ModConfigSpec.IntValue shippingScheduleWorkSpreadTicks;
        // Enable SCM initialization V2
        public final ModConfigSpec.BooleanValue enableScmInitializationV2;
        // Tracked block features
        public final Map<String, ModConfigSpec.ConfigValue<Boolean>> blockFeatures;
        // Tracked item features
        public final Map<String, ModConfigSpec.ConfigValue<Boolean>> itemFeatures;
        // Tracked entity features
        public final Map<String, ModConfigSpec.ConfigValue<Boolean>> entityFeatures;

        // Initialize the server
        private Server(ModConfigSpec.Builder builder) {
            builder.comment("Authoritative server-side behavior").push("server");
            builder.comment("Server-owned content availability. Clients use these values while connected.").push("features");
            blockFeatures = defineFeatureGroup(builder, "blocks", CTFeatureToggles.blockDefaults());
            itemFeatures = defineFeatureGroup(builder, "items", CTFeatureToggles.itemDefaults());
            entityFeatures = defineFeatureGroup(builder, "entities", CTFeatureToggles.entityDefaults());
            builder.pop();
            enableThrusterParticles = builder
                    .comment("Spawn thruster exhaust particles while active")
                    .define("enableThrusterParticles", true);
            thrusterParticleIntensity = builder
                    .comment("Multiplier for active thruster particle count")
                    .defineInRange("thrusterParticleIntensity", 1.0D, 0.0D, 4.0D);
            enableThrusterBulkProcessing = builder
                    .comment("Force thruster air streams to bulk-process items using Create fan blasting or haunting")
                    .define("enableThrusterBulkProcessing", true);
            thrusterBulkProcessingFullThrottleMultiplier = builder
                    .comment("Bulk processing rate multiplier at 100% throttle, relative to Create's default fan processing speed. Processing scales linearly with throttle, so 50% throttle matches Create when this is 2.0")
                    .defineInRange("thrusterBulkProcessingFullThrottleMultiplier", 2.0D, 0.0D, 64.0D);
            enableGyroscopeLinking = builder
                    .comment("Allow linking Advanced Data Link blocks to Simulated gyro sensors")
                    .define("enableGyroscopeLinking", true);
            gyroscopeLinkRange = builder
                    .comment("Maximum block distance between selected gyro sensor and placed Advanced Data Link, matching Create's Display Link flow")
                    .defineInRange("gyroscopeLinkRange", 128, 1, 1024);
            virtualOrientationSourceTimeoutTicks = builder
                    .comment("How many ticks a virtual orientation source remains active without an update")
                    .defineInRange("virtualOrientationSourceTimeoutTicks", 40, 1, 1200);
            controllerOrientationSourceMaxTiltDegrees = builder
                    .comment("Maximum tilt magnitude (degrees) when deriving orientation from Analogue Contraption Controller pitch/roll axes")
                    .defineInRange("controllerOrientationSourceMaxTiltDegrees", 45.0D, 1.0D, 89.0D);
            allowThrusterManualToggle = builder
                    .comment("Allow Use-key toggling of the enabled state on thrusters")
                    .define("allowThrusterManualToggle", true);
            physicsGantryBeltWheelMaxDistance = builder
                    .comment("Maximum allowed link distance for Physics Gantry Belt Wheel (hard capped to 64 blocks)")
                    .defineInRange("physicsGantryBeltWheelMaxDistance", 64, 1, 64);
            builder.comment("Advanced Contraption Controller and Ship Control Module tuning")
                    .push("ship_control");
            shippingScheduleWorkSpreadTicks = builder
                    .comment("Spread shipping schedule maintenance and state updates over this many ticks. Ship physics control still runs every tick.")
                    .defineInRange("shippingScheduleWorkSpreadTicks", 2, 1, 4);
            enableScmInitializationV2 = builder
                    .comment("Use the experimental SCM initialization V2 matrix probe, representative propulsion sampling, and interpolated 9x9 bearing maps")
                    .define("enableScmInitializationV2", false);
            builder.pop();
            builder.pop();
        }
    }
}
