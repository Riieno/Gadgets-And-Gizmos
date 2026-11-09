package com.rieno.gadgetsandgizmos.compat.letitwalk;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphHostServices;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodePresentationRegistry;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import dev.letitwalk.content.WalkerCoreBlockEntity;
import dev.letitwalk.walker.WalkerBrain;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose native walker controls through the shared graph node and execution APIs
public final class LetItWalkNodes{
    // Retain stable saved node ids independently of optional mod loading
    public static final String PREFIX = "createthrusters:let_it_walk_";

    // Prevent node registration instances
    private LetItWalkNodes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register native controls separately from ordinary SCM navigation nodes
    public static void register(){
        register("movement", Map.of("forward", GraphValue.number(0), "strafe", GraphValue.number(0),
                "turn", GraphValue.number(0), "gear", GraphValue.number(1), "gallop", GraphValue.bool(false)));
        register("parking", Map.of("parked", GraphValue.bool(true)));
        register("jump", Map.of("held", GraphValue.bool(true)));
        register("height", Map.of("amount", GraphValue.number(0)));
        register("lean", Map.of("roll", GraphValue.number(0), "pitch", GraphValue.number(0)));
        register("flight", Map.of("enabled", GraphValue.bool(true)));
        register("dash", Map.of("held", GraphValue.bool(true)));
        register("grab", Map.of("held", GraphValue.bool(true)));
        register("stop", Map.of());
        register("status", Map.of());
    }

    // Apply native mutations only for live execution pulses on the owning server
    private static void register(String action, Map<String, GraphValue> defaults){
        Map<String, String> inputs = new LinkedHashMap<>();
        if(!"status".equals(action)) inputs.put("exec", "exec");
        for(String key : List.of("forward", "strafe", "turn", "gear", "gallop", "parked", "held", "amount", "roll", "pitch", "enabled")){
            if(defaults.containsKey(key)) inputs.put(key, defaults.get(key).type());
        }
        String type = PREFIX + action;
        GraphApi.nodes().register(new GraphNodeDefinition(type, "let_it_walk", inputs,
                Map.of("exec", "exec", "available", "boolean", "assembled", "boolean", "powered", "boolean",
                        "parked", "boolean", "flying", "boolean", "rpm", "number", "gear", "number", "legs", "number"), true));
        GraphNodePresentationRegistry.register(type,
                new GraphNodePresentationRegistry.Presentation(defaults, List.of(), Map.of()));
        GraphApi.runtimes().register(type, (ctx, vals) -> {
            var host = ctx.service(GraphHostServices.BLOCK_ENTITY).orElse(null);
            WalkerCoreBlockEntity core = LetItWalkCompatibility.findCore(host);
            boolean available = core != null && host != null && host.getLevel() != null && !host.getLevel().isClientSide;
            if(available && ctx.executionTriggered() && !"status".equals(action)) apply(core, action, vals);
            return Map.of("available", GraphValue.bool(available), "assembled", GraphValue.bool(available && core.isAssembled()),
                    "powered", GraphValue.bool(available && core.isPowered()), "parked", GraphValue.bool(available && core.parked),
                    "flying", GraphValue.bool(available && core.flying), "rpm", GraphValue.number(available ? Math.abs(core.getTheoreticalSpeed()) : 0),
                    "gear", GraphValue.number(available ? core.gear : 0), "legs", GraphValue.number(available ? core.legs.size() : 0));
        });
    }

    // Preserve other native actions when several walker nodes execute in the same tick
    private static void apply(WalkerCoreBlockEntity core, String action, Map<String, GraphValue> vals){
        long tick = core.getLevel().getGameTime();
        int keys = tick - core.lastInputTime <= 10L ? core.input : 0;
        switch(action){
            case "movement" -> {
                keys &= ~(15 | WalkerBrain.KEY_CRAB | WalkerBrain.KEY_GALLOP | (WalkerBrain.GEAR_MASK << WalkerBrain.GEAR_SHIFT));
                double forward = number(vals, "forward");
                double strafe = number(vals, "strafe");
                double turn = number(vals, "turn");
                keys |= forward > 0.05D ? WalkerBrain.KEY_FORWARD : forward < -0.05D ? WalkerBrain.KEY_BACK : 0;
                if(Math.abs(strafe) > 0.05D) keys |= WalkerBrain.KEY_CRAB | (strafe > 0.0D ? 8 : 4);
                core.receiveTurn(-turn * 0.04D);
                int gear = Mth.clamp((int)Math.round(vals.getOrDefault("gear", GraphValue.number(1)).asNumber()), 0, 4);
                keys |= gear << WalkerBrain.GEAR_SHIFT;
                if(bool(vals, "gallop")) keys |= WalkerBrain.KEY_GALLOP;
            }
            case "parking" -> {
                boolean parked = bool(vals, "parked");
                if(core.parked != parked){
                    core.parked = parked;
                    core.setChanged();
                    core.sendData();
                }
                if(core.parked) keys = 0;
            }
            case "jump" -> keys = key(keys, WalkerBrain.KEY_JUMP, bool(vals, "held"));
            case "height" -> {
                double amount = number(vals, "amount");
                keys = key(keys, WalkerBrain.KEY_UP, amount > 0.05D);
                keys = key(keys, WalkerBrain.KEY_DOWN, amount < -0.05D);
            }
            case "lean" -> core.receiveLean(Math.toRadians(Mth.clamp(rawNumber(vals, "roll"), -25, 25)),
                    Math.toRadians(Mth.clamp(rawNumber(vals, "pitch"), -25, 25)));
            case "flight" -> keys = key(keys, WalkerBrain.KEY_FLIGHT, bool(vals, "enabled") != core.flying);
            case "dash" -> keys = key(keys, WalkerBrain.KEY_DASH, bool(vals, "held"));
            case "grab" -> keys = key(keys, WalkerBrain.KEY_GRAB, bool(vals, "held"));
            case "stop" -> {
                keys = 0;
                core.receiveLean(0, 0);
                core.mouseTurn = 0;
            }
            default -> { return; }
        }
        core.receiveInput(keys & ~WalkerBrain.KEY_LATCH, tick);
    }

    // Replace one native held action without clearing movement
    private static int key(int keys, int mask, boolean held){ return held ? keys | mask : keys & ~mask; }
    // Read a finite signed movement input
    private static double number(Map<String, GraphValue> vals, String key){ return Mth.clamp(rawNumber(vals, key), -1, 1); }
    // Read a finite native numeric input
    private static double rawNumber(Map<String, GraphValue> vals, String key){
        double val = vals.getOrDefault(key, GraphValue.number(0)).asNumber();
        return Double.isFinite(val) ? val : 0;
    }
    // Read one native held-state input
    private static boolean bool(Map<String, GraphValue> vals, String key){ return vals.getOrDefault(key, GraphValue.bool(false)).asBoolean(); }
}
