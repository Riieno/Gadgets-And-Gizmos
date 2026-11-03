# Smart container rendering

Right-click a Smart Vault or Smart Battery with a Create wrench to show or hide its tablet. Tablets start hidden, including in worlds saved before this setting existed.

Right-click a Smart Tank with a wrench to cycle **fluids only → tablet only → neither → both → fluids only**. Tanks start with fluids only. Sneak-wrench dismantling keeps its existing behavior.

The choice applies to the entire multiblock and is saved on its members. Tanks use closed housings in tablet-only and neither modes, and restore their window layout in fluid-visible modes. Older mismatched saved housings are repaired when their members finish loading. Active boilers retain their normal closed housings. Stored items, fluids, energy and automation remain available.

When its tablet is hidden, right-click any face of the container with an empty hand to open the same interactive tablet GUI. Opening the GUI leaves world tablet rendering disabled. Wrenches, buckets and other held items keep their existing interactions; storage access still uses the existing permission and menu checks.

Enabled tablets update within 32 blocks of the viewing camera. Beyond 32 blocks they show their last completed image, or a shared manifest image if they have not been viewed nearby. Beyond 64 blocks the tablet is culled. These distances follow a ship's physical position. Returning nearby resumes live updates and normal interaction.
