# Gadgets & Gizmos Blocks and Interactions

This page covers the Gadgets & Gizmos blocks, tools, and the interactions used to configure them. Most configurable blocks open their screen with Use. Create Wrenches keep their normal rotation and pickup behaviour where supported.

## Contents

- [Propulsion and Flight](#propulsion-and-flight)
- [Controllers and Inputs](#controllers-and-inputs)
- [Displays, Navigation, and Utilities](#displays-navigation-and-utilities)
- [Ship Blocks](#ship-blocks)
- [Kinetics and Power](#kinetics-and-power)
- [Physics Movement](#physics-movement)
- [Tools and Other Interactions](#tools-and-other-interactions)

## Propulsion and Flight

<details>
<summary><strong>Thrusters, Bearings, and Orientation</strong></summary>

| Block | What it does | How to use it |
| --- | --- | --- |
| Thruster | Produces thrust for a physics assembly and can also process items in its exhaust with upgrades. | Configure its fuel, oxidizer, upgrades, control mode, and throttle. Supply the required fuel or FE when using Focused Mode. |
| RCS Thruster | Provides four independent reaction-control nozzles without fuel. | Supply rotation through the bottom shaft. Sneak and Use to configure each nozzle's Redstone Link frequencies, or control precise throttle values from a controller or computer. |
| Fuel Oxidizer | Stores the oxidizer used by compatible Thrusters. | Connect it to the Thruster's fuel system and keep it supplied. |
| Servo Bearing | Assembles and rotates a mounted sub-level containing Thrusters. | Build on its head, assemble it, then use its screen, ACC target, or CC:Tweaked peripheral to control the bearing and attached Thrusters. |
| Vector Bearing | Moves an assembled head on two axes and can stabilize selected world axes. | Build on the head, assemble it, and configure control mode, max tilt, and stabilization in the bearing screen. |
| Aileron Bearing | Assembles and rotates a mounted aileron or control surface. | Build on the head, assemble it, then configure or control its angle. |
| Scissor Piston, and Arm | Builds a linked multi-section scissor arm for a physics assembly. | Build the arm from Scissor Piston Arms, use the piston as the drive, and use a Link Block when the mechanism needs a paired endpoint. |
| Advanced Data Link | Connects Data sources to consumers wirelessly. | Place it facing the required direction and open its screen to configure the link. |
| Blackstone Alloy Block | A sturdy construction block for Gadgets & Gizmos builds. | Use it as a normal building block. |

</details>

## Controllers and Inputs

<details>
<summary><strong>Controllers and Player Controls</strong></summary>

| Block or Item | What it does | How to use it |
| --- | --- | --- |
| Advanced Contraption Controller | Runs programmable node graphs for inputs, logic, data, outputs, displays, and ship control. | Use it to open the node graph editor. See [Advanced Contraption Controller](Advanced-Contraption-Controller). |
| Sable Contraption Controller | Provides configurable controller channels, bindings, targets, and outputs. | Use it to configure the controls, then add bindings or wireless frequencies for the blocks it should control. |
| Analogue Joystick | Produces an independent 0 to 15 redstone signal on each side. | Tilt the joystick and connect its side outputs to the control you need. |
| Double Button | Holds two independent wireless redstone buttons. | Use a red or blue slot while holding an item to set that button's frequency. Sneak and Use with an empty hand to select its response mode. |
| Copycat Double Button | A Double Button that copies another block's appearance. | Use a full block on a button to apply its material, then configure each button's response mode with Sneak and Use. |
| Contraption Network Linker | Binds blocks to a Contraption Controller without a wireless frequency. | Use it on a block while setting up a controller input or output. Select the created binding from the controller or ACC node. |
| Smart Goggles | Shows live physics force, propulsion, and assembly information. | Equip them in the head slot and look at nearby physics assemblies. |
| Configuration Clipboard | Copies supported Gadgets & Gizmos block configuration. | Sneak and Use on a compatible block to copy its settings, then use it on the same block type to apply them. Sneak and Use in the air to clear it. |

</details>

## Displays, Navigation, and Utilities

<details>
<summary><strong>Displays, Navigation, Utilities, and Logistics Automation</strong></summary>

| Block or Item | What it does | How to use it |
| --- | --- | --- |
| ACC Display | Forms a larger interactive display when placed next to other ACC Displays. | Connect adjacent displays, then use ACC display nodes to show widgets, graphs, plotters, external sources, or ship information. |
| Universal Display Adapter | Makes an adjacent computer, monitor, or Display Link source available to ACC Displays. | Place it beside the source and select it from the ACC display nodes. |
| Smart Tablet | Opens supported controller and dock tools remotely. | Use to open it, Sneak and Use to place it, then open the needed app and use it on a controller or dock to link it. |
| Advanced Navigation Table | Stores several filled maps and exposes the selected navigation target. | Insert maps by hand or automation, open the table to select the active map, then use its pitch and roll outputs from a controller or script. |
| Shipping Manifest | Shows the items, fluids, and FE stored in the container it is attached to. | Place it on the selected face of an item, fluid, or energy container, then Use it to open Create's read-only clipboard view. |
| Player Mannequin | Displays a posed player or bundled supporter skin with an optional nameplate. | Use the poser to choose whether its name is visible. Changing the skin updates the name to the resolved player or supporter name and keeps the chosen visibility. |
| Smart Vault | A smart version of the Create Vault. has 1.25x the capacity. | Use the `Contraption Network Linker` and the `Advanced Control Computer` to create storage networks, smart containers can be used from any other and share inventory when in a network. When on a `Ship Control Module` auto piloted ship, smart containers allow wireless logistic routing for automation and thruster fueling. |
| Smart Battery | An FE storage vault. Can store up to 5GFE at full size. | Use the `Contraption Network Linker` and the `Advanced Control Computer` to create storage networks, smart containers can be used from any other and share inventory when in a network. When on a `Ship Control Module` auto piloted ship, smart containers allow wireless logistic routing for automation and thruster fueling. |
| Smart Tank | A smart version of the Create Fluid Tank. Can store multiple Fluid types. has 2X capacity | Use the `Contraption Network Linker` and the `Advanced Control Computer` to create storage networks, smart containers can be used from any other and share inventory when in a network. When on a `Ship Control Module` auto piloted ship, smart containers allow wireless logistic routing for automation and thruster fueling. |
| Thruster Dye Applicator | Used to dye thruster plumes. | Store `Dyes` and `Glow Ink Sacks` inside it's inventory to be able to set up custom colors and gradients for the thruster plumes. |

</details>

## Ship Blocks

<details>
<summary><strong>Ships, Docks, and Shipping</strong></summary>

| Block or Item | What it does | How to use it |
| --- | --- | --- |
| Ship Control Module | Maps a Sable ship so an attached ACC can control it as one vehicle. | Mount an ACC on the module's exposed face, then scan the assembled ship from the ACC graph. See [Ship Control Module](Ship-Control_Module). |
| Ship Coupler | Couples independent ship carriages while leaving yaw free. | Power it to attach by default and remove power to release. Use an empty hand to invert that redstone behaviour, or control it from an SCM graph or Shipping Schedule. |
| Ship Dock | Provides a named docking destination and optional refuelling, restocking, package, and door services. | Use the dock to name it, enable its capabilities, choose the connectors for each capability, and select the docked ship doors to open. |
| Shipping Schedule | Sends an initialized ship through Ship Docks and route instructions. | Add stops, then assign the schedule to a seated pilot directly in front of an ACC mounted on a Ship Control Module. |

</details>

## Kinetics and Power

<details>
<summary><strong>Rotation and FE</strong></summary>

| Block | What it does | How to use it |
| --- | --- | --- |
| Smart Gearbox | Carries two independent kinetic lanes through one block or controls face-mounted Servo Bearings from orientation data. | Connect East/West and North/South shafts. Use a face to invert that output. Add an Advanced Data Link on top and enable Servo Mode for orientation control. Set Data exposes face angles and limits in Servo modes, or inversion, RPM caps, and separate 0 to 100% lane speeds in Passthrough modes. |
| Vertical Smart Gearbox | A vertical form of the Smart Gearbox. | Use it like the Smart Gearbox where the build needs a vertical layout. |
| Bi-directional Gearshift | Provides two independent kinetic lanes with per-face direction controls. | Connect the lanes to the matching axes. Use an empty hand on an output face to invert it, or open the screen for lane settings. |
| Variable Transmission | Scales rotation from stopped to full speed with redstone without adding stress. | Connect input and output shafts on opposing faces. Redstone 0 stops the output, 15 passes full speed, and intermediate strength scales it proportionally. |
| Vertical Variable Transmission | A vertical form of the Variable Transmission. | Use it like the Variable Transmission where the build needs a vertical layout. |
| Industrial Alternator | Turns shaft rotation into FE. | Connect a rotating shaft and draw FE from any face with Andesite Cable. Faster rotation produces more FE and applies more kinetic stress. |
| Industrial Motor | Turns FE into shaft rotation. | Supply FE with a cable or battery, connect machinery to the shaft, and set the target RPM from the motor's back face. |
| Andesite Cable | Transfers FE between connected machines. | Place it beside FE producers or consumers. It connects automatically and transfers power through the cable network. |

</details>

## Physics Movement

<details>
<summary><strong>Gantry, Rope, and Launcher Blocks</strong></summary>

| Block or Item | What it does | How to use it |
| --- | --- | --- |
| Physics Gantry Shaft | Forms a powered straight track for a Physics Gantry Carriage. | Build a continuous shaft track and supply rotation. |
| Physics Gantry Carriage | Assembles a payload and carries it along a Physics Gantry Shaft. | Place it on a shaft face, assemble the payload, and drive the shaft to move it along the track. |
| Physics Gantry Belt Wheel | Transfers rotation between a fixed and moving gantry assembly. | Use a Belt Connector on one wheel, then use it on the other wheel to link them. Use shears on a linked wheel to remove the link. |
| Powered Zipline | A powered trolley that rides on rope and can carry a platform. | Use it on a highlighted rope to attach it. Use it to ride, W and S to move, Sneak to dismount, and Sneak and Use to configure wireless movement frequencies. |
| Claw | Hangs from a rope and grabs nearby Rope Connectors. | Hang it from a rope on its top face. Redstone 0 opens it, 1 through 14 attempts a grip, and 15 locks it shut. |
| Entity Launcher | Fires a grappling hook that pulls an entity or the player. | Hold Use to charge, then release to fire. Hold Use while hooked to a block to pull yourself, and use Alt plus Use to switch between Charge and Instant power. |

</details>

## Tools and Other Interactions

<details>
<summary><strong>Physics Staff</strong></summary>

The Physics Staff grabs, drags, and pins physics assemblies using pressure from an equipped Backtank. Use it on an assembly to grab the point under the crosshair, then look around to drag it and scroll to change the reach. Attack while aiming at an assembly to pin it at its current position.

The status bar and Shift tooltip show the Staff's live state, locked assembly count, and available pressure.

</details>
