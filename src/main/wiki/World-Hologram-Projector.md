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

Zero sampling offset uses the lowest average visible surface height among the loaded chunks. It displays terrain from ten blocks below that surface through build height, preserving mountains and higher structures without exporting deep cave systems or bedrock. Empty columns and invisible light blocks do not set the surface height. Other offsets retain their 64-block cutaway ending at the regional average surface height plus the offset. Negative sampling offsets reveal lower layers; positive offsets select higher terrain and sky structures. Height and Size move/scale the physical projection and stay nonnegative. Hologram fragments respect scene depth, so solid world blocks occlude the projection.

The server setting `server.world_hologram_projector.obfuscateOres` defaults to true. Ores below the actual surface are replaced with stone/deepslate before transmission, including cutaway faces. The original surface height remains the masking reference when moving the sampling slice.

The ship toggle includes tracked Sable bodies within the selected world region. Their geometry is cached and culled, and their translation/rotation follow interpolated live poses. Player/mob positions and changing average heights are smoothed as well.

Blocks use retained GPU surface meshes with baked model data. Source model-data refreshes invalidate affected meshes; rotational speed changes do not. Ship geometry includes the outside hull and interiors connected to transparent blocks such as glass or open entrances. Sealed interiors are culled across chunk boundaries. Sampling cutaways open the clipped parts of the hull. Very large sparse bounds use conservative face visibility rather than risk missing visible blocks.

Projected block mechanisms are static. Flywheel partial models remain present, with native rotation and texture scrolling frozen on projection-owned instances. Other block entity renderers record their geometry once instead of animating every frame. Create tanks and Smart Tanks use their closed housing models without fluid contents. Source blocks, stored fluids, machine animation and container visibility settings remain unchanged. Texture sampling uses the block atlas mipmaps when available, reducing distant texture detail. Renderer-only blocks remain visible; live ACC and diagnostic screen contents are excluded to prevent recursive rendering. Moving ships retain their live interpolated poses.

The emitter produces ordinary level-12 block light, and the projection maintains a shared level-15 invisible light source at its world position. Light ownership is released on removal/unload; optional colored lighting adds a cyan point light. All hologram geometry is full-bright.

Only loaded terrain and tracked bodies are used. The projector never force-loads world chunks. Viewing range is 64 blocks; changing controls requires build permission within eight blocks. Terrain sampling remains budgeted across projectors, with progressive capture at large ranges.

## Client resolution

**Client Settings → World Hologram Projector Resolution** controls local rendering quality. The TOML key is `client.worldHologramProjectorResolution` in `createthrusters-client.toml`.

| Setting | Rendering |
| --- | --- |
| FULL (default) | Original textured models. |
| HIGH | Average and merge solid surfaces below about 2 pixels per source block. |
| MEDIUM | Always average eligible solid faces, including nearby blocks. |
| LOW | Always average eligible solid faces and group their colors in tiles of at least 2×2 blocks. |

Smaller projected blocks use larger color-averaging tiles. Adjacent occupied faces on the same plane can become one quad. Layered opaque cubes such as grass combine their base and overlay colors. Glass, faces without an opaque base, fluids, thin models and block entity partials retain their existing geometry. Window gaps and silhouettes remain open. Medium and Low keep their minimum simplification when a chunk crosses the camera; High continues to adapt to projected size. A small overlap between distance levels avoids repeated rebuilds while moving the camera.

Changes apply to active holograms as their budgeted meshes rebuild. Full restores their original models. Texture colors are cached from the loaded resource pack and refreshed on resource reload. This changes client presentation only; server sampling and saved projector settings are unchanged. Performance gains depend on how much eligible solid geometry is visible.
