# Worker runtime discovery and changes

## Scope and sources

Discovery inventoried 829 addon Java files and 1,230 resources, then traced worker requests from the graph editor and tablet through ACC compilation, queues, storage, crafting, navigation, delivery, and persistence. The library's worker, storage policy, discovery, pathfinding, and client UI APIs were inspected alongside those consumers.

The supplied `E:/GIT/thrusters-and-things/reference/_assets` directory does not exist. The available reference assets are in `E:/GIT/thrusters-and-things/reference_assets`; decompiled dependencies are in `E:/GIT/thrusters-and-things/jar_src`. These were read only. Minecraft/NeoForge sources came from the local Gradle source archive. No previous project notes were used.

The asset inventory covers Create, Create Addition, CC:Tweaked, Sable, Simulated, Aero, Amendments, Flywheel, Minecraft, Music Notification, and Offroad. Create's `RecipeFinder` and `ItemVaultBlockEntity`, Minecraft's GUI item renderer, player view settings, chunk tickets, and recipe contracts were inspected for the reported failures.

## Codebase map

| Area | Responsibility relevant to workers |
| --- | --- |
| `content/AdvancedContraptionControllerBlockEntity.java` | Compile graph/tablet requests, adapt recipes and SCM endpoints, enqueue prerequisite orders |
| `content/WorkerPodBlockEntity.java` | Per-worker runtime, cargo custody, virtual crafting, machine interaction, navigation, completion and save/load |
| `content/WorkerStorageEndpoint.java` | Addon storage/machine adapters, SCM discovery, manifests, vaults and virtual crafting stations |
| `content/WorkerPlayerEndpoint.java` | Player availability and inventory delivery |
| `content/advanced` | Graph catalogue, node creation, functions and execution |
| `content/ContraptionNetworkLinker*` and SCM compatibility | Linked target identities, modes, discovery and sublevel coordinates |
| `content/ShippingManifest*`, smart storage and tablet apps | Access policy, filters and managed containers |
| `neoforge/client/AdvancedContraptionControllerScreen.java` | Worker node/inspector rendering, target selection, filter slots and pointer handling |
| `neoforge/network` | Server-authoritative graph/task and worker configuration messages |
| Library `lib/worker` | Reusable jobs, resources, endpoints, recipe planning, delivery selection, travel and navigation |
| Library storage/discovery/pathfinding APIs | Capability policies, container identity, loaded sublevels and incremental route search |
| Addon registries, bootstrap, mixins, resources and build | Registration, integration, presentation assets, packaging and validation |

## API boundary established before implementation

The library owns recipe dependency search, stock reservations, quantity clamping, destination ranking, distant travel, navigation recovery and GUI item depth control. It accepts generic resources, endpoint contracts, recipe definitions and an executability predicate.

The addon owns Minecraft/Create recipe adaptation, crafting-table semantics, mannequin cargo and animation, SCM/manifest policy, graph controls and saved worker runtime. No addon implementation type is exposed by the new library APIs.

## Findings and changes

1. GUI item rendering adds its own depth offset. Filter previews now use `LayeredItemRenderer` and existing overlay/node occlusion checks. Right-click filter slots are handled before canvas panning and context handling.
2. Recipe selection previously committed too early to ingredients and incomplete prerequisite plans. `WorkerRecipePlanner` searches backwards, reserves shared stock, backtracks alternatives and recipes, rejects cycles, and returns dependency-first steps with the exact final requested quantity. The addon adapts current recipes without a persistent Create recipe-cache key.
3. Crafting tables have neither a block entity nor an inventory. SCM discovery now accepts loaded virtual stations; recipe execution consumes planned ingredients and creates the result at the station. Virtual stations cannot accept ordinary storage delivery.
4. Distant player delivery uses persisted `WorkerDeliveryTravel`: beyond 100 blocks, enter outside the effective client/server chunk view, walk to the recipient, walk out of view and return to the departure area. Expiring chunk tickets keep the worker and pod available during the journey.
5. Delivery offers only the requested quantity. Surplus remains in worker custody until inserted into matching stocked/filtered storage or another available container. Fulfilled quantity and surplus state survive reloads.
6. Navigation no longer discards unfinished incremental searches by immediately following partial routes. Stalled navigators reset for another attempt; block-height stepping uses a whole-block search grid. Worker animation follows actual movement. Pending jobs retain worker chunks, refresh endpoint discovery after load and restore machine-output waits.
7. Missing block entities and unloaded sublevels are transient unavailable endpoints. Create vault members are read live rather than through combined handlers that may have cached empty placeholders during initialization. Canonical item capability access prevents duplicate face counts, and planning deduplicates storage identities. Explicit graph endpoints retain their selected access instead of being overridden by dock routing.
8. Recipe jobs waiting on a processor retain their selected output requirement rather than completing on a timeout with arbitrary contents. Multi-ingredient plans retain their selected processor across ingredient trips and reloads.

## Verification

Regression coverage includes nested log/plank/table recipes, partial and shared stock, sibling and nested recipe backtracking, cycles, unsupported processors, quantity overflow, exact delivery with surplus, storage priorities and availability, view boundaries, travel persistence, incremental navigation recovery, SCM crafting-table discovery, late-loading inventories and worker save migration.

The build command is `gradlew.bat clean build --no-daemon --console=plain`. The addon build also performs the sibling library's clean build and validates library boundaries, mixin configuration and generated ComputerCraft documentation.

Both clean builds passed on 2026-09-27: 37 addon tests and 218 library tests, with no failures or errors. The library-boundary, mixin and ComputerCraft documentation checks passed.

The development client was launched with `gradlew.bat runClient -x buildLibrary --no-daemon --console=plain`. Mod loading stopped before gameplay: the configured NeoForge 21.1.225 is older than the 21.1.228 required by several local mods. The runtime also reports missing `sable_schematic_api`, `ldlib2`, `aeronautics_bundled` and `dragonlib`. See `run/client/logs/latest.log`. Gradle reports a successful launch task even though Minecraft rejects this mod set, so this is not a passed gameplay check.

Gameplay checks should cover overlapping filter nodes/modal windows, right-click removal, logs-only table crafting, multi-ingredient crafting at an SCM table, a one-plank player delivery with three planks returned, a delivery beyond 100 blocks, and save/reload during navigation and machine processing. Automated tests do not establish visual or in-world correctness for these scenarios.
