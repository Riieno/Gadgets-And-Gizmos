# World Hologram Projector

The World Hologram Projector displays loaded terrain around its actual Sable-aware world position. Moving projectors use interpolated poses and retain overlapping chunk meshes while crossing chunk boundaries.

Right-click to configure:

| Control | Range | Default |
| --- | --- | --- |
| Radius | 1?1 to 9?9 chunks total | 3?3 |
| Projection Height | 0 to 32 blocks above the emitter | 2 |
| Size | 0.5 to 16 blocks wide | 4 |
| Sampling Y Offset | ?512 to +512 source blocks | 0 |
| Show Players | On/off | On |
| Show Hostile Mobs | On/off | Off |
| Show Passive Mobs | On/off | Off |
| Show Ships / Sub-levels | On/off | On |

Zero sampling offset follows the average surface height of every loaded non-void column in the selected chunks. Other offsets select a 64-block cutaway ending at that average plus the offset. Negative sampling offsets reveal lower layers; positive offsets select higher terrain and sky structures. Height and Size move/scale the physical projection and stay nonnegative.

The server setting `server.world_hologram_projector.obfuscateOres` defaults to true. Ores below the actual surface are replaced with stone/deepslate before transmission, including cutaway faces. The original surface height remains the masking reference when moving the sampling slice.

The ship toggle includes tracked Sable bodies within the selected world region. Their geometry is cached and culled, and their translation/rotation follow interpolated live poses. Player/mob positions and changing average heights are smoothed as well.

Create blocks use their live block model data and native Flywheel visualizers, including animated partials. The library provides the corresponding BER/partial fallback when Flywheel is disabled. Server snapshots carry public rotational speed for detached kinetic previews without inventories. Native visuals retain their own meshes, textures and animation plans under emissive translucent projection materials.

The emitter produces ordinary level-12 block light, and the projection maintains a shared level-15 invisible light source at its world position. Light ownership is released on removal/unload; optional colored lighting adds a cyan point light. All hologram geometry is full-bright.

Only loaded terrain and tracked bodies are used. The projector never force-loads world chunks. Viewing range is 64 blocks; changing controls requires build permission within eight blocks. Terrain sampling remains budgeted across projectors, with progressive capture at large ranges.
