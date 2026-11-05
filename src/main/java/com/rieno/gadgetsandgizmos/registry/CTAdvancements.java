package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.advancement.SimpleAdvancementTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Register addon criteria through the reusable server trigger API
public final class CTAdvancements{
    public static final DeferredRegister<CriterionTrigger<?>> REGISTRAR =
            DeferredRegister.create(Registries.TRIGGER_TYPE, CreateThrusters.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleAdvancementTrigger> PERPETUAL_ENGINE =
            REGISTRAR.register("perpetual_engine", SimpleAdvancementTrigger::new);

    private CTAdvancements(){}
}
