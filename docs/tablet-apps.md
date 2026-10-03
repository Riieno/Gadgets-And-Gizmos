# Paid tablet apps

Default prices: Digisable 10 emeralds, Manifest 8 emeralds, Blockmates 7 emeralds. Buy and install them through the tablet App Store. Existing server price overrides continue to apply.

## Targets

Digisable opens directly, with Stored as its first tab. To store a world sublevel, press Store from world or switch to Reader mode, then use the handheld tablet on a block in that sublevel. Alt+Use toggles Reader mode. Manifest reads the container behind a placed tablet; with a handheld tablet, select Manifest and use it on a container or attached shipping manifest within 16 blocks. You can also look at a container and press Inspect in the app, then press Stop inspecting to release the target. Manifest remains in Standard mode. Only Blockmates uses Reader mode to pair an ACC.

## Digisable

Browse accessible sublevels and inspect an interactive 3D preview. Storing an assembly removes it in a soft smoke plume. Selecting Extract on a stored assembly opens a translucent in-world placement preview. Scroll to push or pull it along your view; hold the Physics Staff rotate key (Tab by default) and move the mouse to rotate; Use confirms and stays in the world; Escape cancels and returns to the tablet. The server checks the complete rotated destination bounds, claims and collisions before restoring the archive. Drag rotates the tablet preview; scrolling zooms it. Teleportation requires creative mode or operator permissions. Deletion requires singleplayer or operator permissions, an entirely unclaimed connected assembly, and a second confirmation click.

The Manifest Resources tab shows fluid tank fill levels with the fluid's texture and a red FE fill bar. It inspects sided FE capabilities and long-capacity FE block entities.

Observed unloaded sublevels retain labelled last-known coordinates across restarts; teleporting loads that destination. Viewing, storing and deleting a body require it to be loaded. Existing bodies never observed since installation are not part of the location index yet.

Archives preserve native blocks, block entities, inventories, UUIDs and original plot addresses. Extraction is in the original dimension and requires a clear, fully loaded destination. Moving Create contraptions must be disassembled before storage. Archives are retained as server world files, not client data or tablet item NBT. Unfinished transactions with live/native-held original bodies are blocked against duplicate extraction.

## Manifest

Cargo shows counts grouped by exact item components, occupied slots, capacity, fluid tanks and energy. Hold an item, list filter or attribute filter in the offhand and choose Set filter; Clear filter accepts any item. Locked containers allow owner/operator menu access, accept matching automated inserts, and allow matching extraction only when Push is enabled. Create multiblocks and double-chest halves share a storage identity.

Push exports matching excess to adjacent consumers. Pull imports matching items from adjacent inventories. Stock targets preserve the requested minimum during pushing and fetch deficits from adjacent inventories first. Nearby, loaded worker controllers can supply remaining shortages with worker deliveries or crafting without linking Manifest to an ACC. Matching offhand samples preserve exact components in stock targets; component-specific shortages can be replenished from adjacent inventories but not from generic worker item requests. Requests include unfinished deliveries so automation does not repeatedly queue the same shortage.

## Blockmates

The paired ACC owns worker orchestration. Manage its linked worker pods, pause/resume individual workers, cancel current orders, and request delivery or crafting into the player's inventory. Automatic crafting uses stocked executable recipe chains and linked processors/endpoints; named worker tasks use the ACC's worker graph. Requests fail visibly when ingredients, recipes or compatible workers are unavailable. Pausing and cancellation retain real carried cargo for recovery.

## Server settings

World configuration is `serverconfig/createthrusters-tablet-apps-server.toml`.

- Digisable: independent storage/locating/teleport/deletion switches; 16 archives per player, 32 bodies and 32768 blocks per archive; 64-block storage range, 2048-block locate/delete range and 100-tick operation cooldown by default.
- Manifest: 64 transferred items per configured container per second by default; up to 16 stock targets and 128 configured containers per owner.
- Blockmates: 4096 items per request by default; the controller caps outstanding app request orders.

Additional mods can register protection providers through the library. Aeroclaims integration denies protected access and refuses deletion of claimed or registered sublevels.
