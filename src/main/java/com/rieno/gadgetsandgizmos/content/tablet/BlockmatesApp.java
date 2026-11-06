package com.rieno.gadgetsandgizmos.content.tablet;

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import net.minecraft.network.chat.Component;
import com.rieno.gadgetsandgizmos.lib.text.UserMessageException;
import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerItemRequest;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkSnapshot;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.List;

// Present only the workers and item requests owned by the paired ACC
final class BlockmatesApp{
    private BlockmatesApp(){}

    static String execute(TabletActionContext ctx, TabletAction action){
        if("pair".equals(action.actionId())) TabletAppTargets.pair(ctx, PaidTabletApps.BLOCKMATES.id());
        var binding = com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage.selectedBinding(ctx.player().server, ctx.sourceTabletId(), action.appId());
        var target = binding == null ? TabletAppTargets.support(ctx)
                : com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
        if(target == null && "refresh".equals(action.actionId())){
            CompoundTag data = new CompoundTag();
            LocalizedText.put(data, "Help", Component.translatable("createthrusters.text.pair_an_acc_in_reader_mode_to_manage_its_workers"));
            PaidTabletApps.snapshot(ctx, action.appId(), data);
            return "";
        }
        TabletAppTargets.requireAccess(ctx, target);
        if(!(target instanceof WorkerOrchestrator controller)) throw new UserMessageException(Component.translatable("createthrusters.text.blockmates_must_be_linked_to_an_acc_managing_workers"));
        String val = action.arguments().getOrDefault("value", "").strip();
        String message = "";
        List<String> missing = List.of();
        boolean requestFailed = false;
        switch(action.actionId()){
            case "refresh", "pair", "fluids" -> {}
            case "worker_enable" -> {
                String[] args = val.split("\\|", 2);
                if(args.length != 2 || !controller.setWorkerEnabled(UUID.fromString(args[0]), Boolean.parseBoolean(args[1]))) throw new UserMessageException(Component.translatable("createthrusters.text.worker_is_unavailable_or_is_not_owned_by_this_acc"));
            }
            case "cancel" -> {
                if(!controller.cancelWorkerTask(UUID.fromString(val))) throw new UserMessageException(Component.translatable("createthrusters.text.this_task_is_no_longer_queued"));
                message = "Task cancellation requested";
            }
            case "request", "request_selected", "request_fluid", "request_fluid_selected" -> {
                UUID selectedWorkerId = null;
                if("request_selected".equals(action.actionId())
                        || "request_fluid_selected".equals(action.actionId())){
                    int idx = val.indexOf('|');
                    if(idx < 1) throw new UserMessageException(Component.translatable("createthrusters.text.choose_a_worker"));
                    try{
                        selectedWorkerId = UUID.fromString(val.substring(0, idx));
                    }catch(IllegalArgumentException ex){
                        throw new UserMessageException(Component.translatable("createthrusters.text.invalid_worker_selection"));
                    }
                    val = val.substring(idx + 1);
                }
                if("request_fluid".equals(action.actionId())
                        || "request_fluid_selected".equals(action.actionId())){
                    String[] fill = val.split("\\|", 4);
                    if(fill.length != 4) throw new UserMessageException(Component.translatable("createthrusters.text.select_fluid_container_count_and_mb_per_container"));
                    ResourceLocation fluid = ResourceLocation.tryParse(fill[0]);
                    ResourceLocation container = ResourceLocation.tryParse(fill[1]);
                    int count = Integer.parseInt(fill[2]);
                    int millibuckets = Integer.parseInt(fill[3]);
                    if(count < 1 || count > TabletAppsServerConfig.REQUEST.get())
                        throw new UserMessageException(Component.translatable("createthrusters.text.container_count_exceeds_the_server_request_limit"));
                    if(millibuckets < 1 || millibuckets > 1_000_000)
                        throw new UserMessageException(Component.translatable("createthrusters.text.mb_per_container_must_be_between_1_and_1000000"));
                    if(!(controller instanceof com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity acc))
                        throw new UserMessageException(Component.translatable("createthrusters.text.fluid_container_requests_require_an_acc"));
                    var res = acc.requestFluidContainers(fluid, container, count, millibuckets,
                            ctx.player().getUUID(), selectedWorkerId);
                    requestFailed = res.failed();
                    message = requestFailed ? res.message() : "Fluid container request queued";
                    if(requestFailed){
                        missing = acc.workerPlanningDetails();
                        if(missing.isEmpty()) missing = List.of(message);
                    }
                    break;
                }
                String[] args = val.split("\\|", 4);
                if(args.length < 3) throw new UserMessageException(Component.translatable("createthrusters.text.enter_an_item_amount_and_transfer_or_craft_mode"));
                ResourceLocation item = ResourceLocation.tryParse(args[0]);
                if(item == null || BuiltInRegistries.ITEM.getOptional(item).orElse(Items.AIR) == Items.AIR) throw new UserMessageException(Component.translatable("createthrusters.text.unknown_requested_item"));
                int amount = Integer.parseInt(args[1]);
                if(amount < 1 || amount > TabletAppsServerConfig.REQUEST.get()) throw new UserMessageException(Component.translatable("createthrusters.text.amount_exceeds_the_server_request_limit"));
                if(!"transfer".equals(args[2]) && !"craft".equals(args[2])) throw new UserMessageException(Component.translatable("createthrusters.text.choose_transfer_or_craft"));
                var res = controller.requestItems(new WorkerItemRequest(null, item, amount, "craft".equals(args[2]),
                        args.length == 4 ? args[3] : "", ctx.player().getUUID(), selectedWorkerId));
                requestFailed = res.failed();
                if(requestFailed){
                    message = res.message();
                    if("craft".equals(args[2]) && controller instanceof
                            com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity acc)
                        missing = acc.workerPlanningDetails();
                    if(missing.isEmpty()) missing = List.of(message);
                }else message = "Worker request queued";
            }
            default -> throw new UserMessageException(Component.translatable("createthrusters.text.unknown_blockmates_action"));
        }
        CompoundTag data = new CompoundTag();
        data.putBoolean("Linked", true);
        data.putString("Controller", target.getBlockState().getBlock().getName().getString());
        data.putInt("RequestLimit", TabletAppsServerConfig.REQUEST.get());
        ListTag workers = new ListTag();
        int visibleOrders = "workers".equals(action.tabId()) ? 128 : 0;
        var workerStream = controller.managedWorkers().stream();
        if(!"request".equals(action.tabId())) workerStream = workerStream.limit(32);
        workerStream.forEach(worker -> workers.add(worker.toTag(visibleOrders)));
        data.put("Workers", workers);
        ListTag tasks = new ListTag();
        controller.workerTaskNames().stream().limit(64).forEach(task -> tasks.add(StringTag.valueOf(task)));
        data.put("Tasks", tasks);
        if("fluids".equals(action.actionId())
                && controller instanceof com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity acc){
            ListTag fluids = new ListTag();
            java.util.Set<String> stockedFluids = new java.util.TreeSet<>();
            com.rieno.gadgetsandgizmos.content.WorkerStorageEndpoint.linked(acc, true).stream()
                    .filter(com.rieno.gadgetsandgizmos.content.WorkerStorageEndpoint::isWorkerRecipeSource)
                    .flatMap(endpoint -> endpoint.snapshot().resources().stream())
                    .filter(amount -> amount.resource().type() == com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType.FLUID
                            && amount.amount() > 0L)
                    .map(amount -> amount.resource().id().toString()).forEach(stockedFluids::add);
            for(var pod : com.rieno.gadgetsandgizmos.content.WorkerPodBlockEntity.linkedPods(acc)){
                for(UUID workerId : pod.compatibleWorkers(
                        com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType.FLUID, java.util.Set.of())){
                    if(!pod.compatibleWorkers(com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType.ITEM,
                            java.util.Set.of(workerId)).contains(workerId)) continue;
                    pod.workerPlanningStock(workerId).forEach((resource, amount) -> {
                        if(resource.type() == com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType.FLUID
                                && amount > 0L) stockedFluids.add(resource.id().toString());
                    });
                }
            }
            stockedFluids.stream().limit(128).forEach(id -> fluids.add(StringTag.valueOf(id)));
            data.put("Fluids", fluids);
        }
        if("inventory".equals(action.tabId())){
            int offset = "refresh".equals(action.actionId()) && !val.isBlank() ? Math.max(0, Integer.parseInt(val)) : 0;
            var containers = controller.linkedStorage().stream()
                    .filter(be -> WorldAccessPolicy.canAccessLocal(ctx.player(), ctx.player().serverLevel(),
                            SimulatedHelper.getContainingSubLevelId(be), be.getBlockPos())).toList();
            data.put("Inventory", ContainerNetworkSnapshot.capture(containers, offset, 32));
        }
        data.putString("Message", message);
        if("request".equals(action.actionId()) || "request_selected".equals(action.actionId())
                || "request_fluid".equals(action.actionId())
                || "request_fluid_selected".equals(action.actionId())){
            data.putUUID("RequestResponseId", UUID.randomUUID());
            data.putBoolean("RequestFailed", requestFailed);
            ListTag details = new ListTag();
            missing.forEach(detail -> details.add(StringTag.valueOf(detail)));
            data.put("Missing", details);
        }
        PaidTabletApps.snapshot(ctx, action.appId(), data);
        // Craft failures are shown in the Request tab's persistent diagnostic section.
        return requestFailed ? "" : message;
    }
}
