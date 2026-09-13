![Gadgets & Gizmos](https://cdn.modrinth.com/data/cached_images/a1b244f112912d23f2fe47503c038240e3b5a3ec.png)

Logo by [Djrag](https://modrinth.com/user/Djrag)

![Special thanks](https://cdn.modrinth.com/data/cached_images/4aeb7c19b0e5e023762bc55022ed58bb9748c6d9_0.webp)
## I want to thank everyone in the community for helping make this addon what it is today! It wouldn't be were it is without your suggestions, feedback, and most importantly, bug reports. So thank you all ❤️
## Showcase


<details>
<summary>Spoiler</summary>

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/lWIy_6SdxhA" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

</details>

> Take your Create Aeronautics contraptions to the next level. With advanced control blocks, compact Ailerons, or Automated air ships!

Join the [Discord](https://discord.gg/zhvuEMEpZR)!



---

## New Blocks

<details>
<summary>Propulsion Blocks</summary>

|  |  |  |  |
| --- | --- | --- | --- |
| Thruster | Mini Thruster | Beam Thruster | RCS Thruster |
|  |  |  |  |

</details>

<details>
<summary>Control Blocks</summary>

|  |  |  |  |
| --- | --- | --- | --- |
| Contraption Controller | Advanced Contraption Controller | Joystick | Advanced Data Link |
| Double Button | Ship Control Module |  |  |
|  |  |  |  |

</details>

<details>
<summary>Bearings, Actuators, and Sub-Level Manipulation</summary>

|  |  |  |  |
| --- | --- | --- | --- |
| Servo Bearing | Vector Bearing | Aileron Bearing | Scissor Piston |
| Smart Gearbox | Bi-Directional Gearshift | Variable Transmission | Physics Gantry |
| Claw | Powered Zipline | Entity Launcher |  |
|  |  |  |  |

</details>

<details>
<summary>Other Blocks</summary>

|  |  |  |  |
| --- | --- | --- | --- |
| Industrial Alternator | Industrial Motor | Fuel Oxidizer | ACC Display |
| Ship Dock | Universal Display Adaptor |  |  |
|  |  |  |  |

</details>

---

## New Items

|  |  |  |
|---|---|---|
| Thruster Lens | Physics Staff | Smart Goggles |
| Contraption Network Linker | Configuration Clipboard | Propulsion Upgrade |
| Smelting Upgrade | Smoking Upgrade | Haunting Upgrade |
| Computation Mechanism | Shipping Schedule | Blackstone Alloy |
| Blackstone Sheet | Portable Contraption Controller | Portable Advanced Contraption Controller |
|  |  |  |

---
## Ship Control Module
The `Ship Control Module` is a new multiblock that requires an `Advanced Contraption Controller` to be placed directly on top of it.
It enables create train style automation using the `Shipping Schedule` and `Ship Docks`, it does it's own live path finding and sub-level/world collision avoidance.
Currently it only supports ships that are steered and propelled by `Thrusters` added by this mod, `Create Propulsion Simulated` thrusters do work to a degree but not fully.
> Known issues with the `Ship Control Module`:
- Doesn't work correctly with planes, boats, ground vehicles or sail like blocks on bearings.
- Most features are only fully supported with the thrusters added by G&G, other propulsion mod support is being worked on.

`Gadgets and Gizmos` update 1.3.0 is already being worked on and was being worked on side-by-side with the 1.2.0 release, 1.3.0 will bring better usability to the `Ship Control Module` including a full GUI and better ground vehicle, plane, and boat support.

## Versioning Guide

|               |                         |                      |
| ------------- |------------------------ |--------------------- |
| Version Number |Version Type |Purpose |
|  | | |
| x.0.0 |Major Version           |Reserved for codebase rewrites and major breaking changes.       |
| 1.x.0 |Minor Version |New features, blocks, and mechanics. |
| 1.0.x          |Patch Version          |Reserved for bug fixes, patches, and hotfixes.       |
|               |                         |                      |               |

---

# Contributor and Supporter Mannequins
- Christeroph: For continued support through all my projects and his band allowing me to include their music in the addon!
- Djrag: For providing the amazing new block models and textures! More to come!
- RayRay: For providing the fantastic new GUI and interface textures! More to come!
- BigJim: For his continued support and showcases in his videos!
---

## Special thanks to [Lomens](https://linktr.ee/Lomens) for providing the music for the addon! check them out on all major streaming platforms.

---
## CC:Tweaked integration for Everything!

**CC:Tweaked** peripheral support for major systems, including thrusters, bearings, gearbox, joystick, controller, and more. Control single devices or control attached thruster groups from code.
> Bundled **in-game docs browser** at `/rom/thrusters/docs.lua`
> Complete `Advanced Contraption Controller -> CC:Tweaked` bridge allowing you to get information from the `ACC` and author complete graphs from within `CC:Tweaked`. Wiki availible here [Github](https://github.com/Riieno/Gadgets-And-Gizmos-Library/wiki/CC:Tweaked-Bridge)

---

## Pack Authors

Inclusion of `Gadgets & Gizmos` the `Gadgets & Gizmos Library` or any other mods by me are allowed to be included in mod packs without limitation, this is perpetual permission.

## Developers

People looking to expand `Gadgets and Gizmos` or build against it should build against `Main` or `InDev`, Use `main` if you want to be compatible with the current stable release, and `InDev` if you want to be ahead of the curve and build against new features being added in the next release. Experimental branches should not be pulled or built against.
