package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Persist the player-authored SCM configuration independently of measured control
// curves. Unit references deliberately use sub-level/block identities instead of
// calibration indices, which may change when a craft is rescanned.
public final class ScmConfigurationProfile {
    private static final int VERSION = 4;
    /**
     * Optional profile-only grouping. It is not a graph node, but its selected
     * units are read as direct live adapters just like units in a flight-action
     * group. It never requests discovery, probing or response-curve calibration.
     */
    public static final String AUTO_ACTION = "scm_auto";
    /**
     * Optional analogue speed-control grouping. Its members are driven from
     * the live translation request rather than treated as propulsion geometry.
     * This is intended for transmissions, governors and similar controls in a
     * craft's drive chain.
     */
    public static final String ACCELERATION_ACTION = "scm_acceleration";

    private UUID mapId;
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, String> actionGroups = new LinkedHashMap<>();
    private final Set<UnitReference> excludedUnits = new LinkedHashSet<>();

    public static ScmConfigurationProfile empty() {
        return new ScmConfigurationProfile();
    }

    public UUID mapId() {
        return mapId;
    }

    public void setMapId(UUID value) {
        mapId = value;
    }

    public List<Group> groups() {
        return List.copyOf(groups);
    }

    public Map<String, String> actionGroups() {
        return Map.copyOf(actionGroups);
    }

    public Set<UnitReference> excludedUnits() {
        return Set.copyOf(excludedUnits);
    }

    public boolean isConfiguredFor(ShipControlMap map) {
        return map != null && mapId != null && mapId.equals(map.id());
    }

    public static boolean isAutoAction(String action) {
        return AUTO_ACTION.equals(action);
    }

    public static boolean isAccelerationAction(String action) {
        return ACCELERATION_ACTION.equals(action);
    }

    /**
     * Return the units assigned to the optional Auto group. These remain
     * ordinary profile units and are initialized from their live adapter data.
     */
    public Set<UnitReference> autoUnits() {
        String groupId = actionGroups.get(AUTO_ACTION);
        if (groupId == null || groupId.isBlank()) {
            return Set.of();
        }
        return groups.stream().filter(group -> groupId.equals(group.id()))
                .findFirst().map(Group::units).orElse(Set.of());
    }

    public boolean hasAutoUnits() {
        return !autoUnits().isEmpty();
    }

    /**
     * Return the blocks whose values are set as a speed/acceleration control
     * while a translational SCM command is active.
     */
    public Set<UnitReference> accelerationUnits() {
        String groupId = actionGroups.get(ACCELERATION_ACTION);
        if (groupId == null || groupId.isBlank()) {
            return Set.of();
        }
        return groups.stream().filter(group -> groupId.equals(group.id()))
                .findFirst().map(Group::units).orElse(Set.of());
    }

    public boolean allows(ShipControlMap.PropulsionUnit unit) {
        return unit != null && excludedUnits.stream().noneMatch(reference -> reference.sameBlock(unit));
    }

    /**
     * A group assignment is deliberately all-or-nothing for the currently
     * executing SCM actions. Falling back to every actuator when one action is
     * not configured would make a partially configured craft surprisingly
     * unsafe (for example, a Forward action waking vertical lift units).
     */
    public boolean hasActionGroupsFor(Collection<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        return actions.stream().allMatch(action -> {
            String group = actionGroups.get(action);
            return group != null && groups.stream().anyMatch(value -> value.id().equals(group));
        });
    }

    /**
     * Whether this profile declares routed units. The Auto group is a real
     * routing group: it contributes its units to every active SCM action,
     * while a named group contributes only to its matching action.
     */
    public boolean hasActionBindings() {
        return actionGroups.values().stream().anyMatch(group -> !group.isBlank()
                && groups.stream().anyMatch(value -> value.id().equals(group)));
    }

    /**
     * Return the units permitted for the currently active commands. Auto is
     * included with every active action; unassigned units remain excluded once
     * a profile declares routing.
     */
    public Set<UnitReference> unitsForExplicitActions(Collection<String> actions) {
        if (actions == null || actions.isEmpty() || actionGroups.isEmpty()) {
            return Set.of();
        }
        Set<String> requestedGroups = new LinkedHashSet<>();
        String autoGroup = actionGroups.get(AUTO_ACTION);
        if (autoGroup != null && !autoGroup.isBlank()) {
            requestedGroups.add(autoGroup);
        }
        for (String action : actions) {
            String group = actionGroups.get(action);
            if (group != null && !group.isBlank()) {
                requestedGroups.add(group);
            }
        }
        Set<UnitReference> selected = new LinkedHashSet<>();
        groups.stream().filter(group -> requestedGroups.contains(group.id()))
                .forEach(group -> selected.addAll(group.units()));
        return Set.copyOf(selected);
    }

    /**
     * Return the stable actuator identities selected by all currently active
     * action groups. An empty result is meaningful: it means that the player
     * intentionally selected an empty group, and therefore no unit is allowed.
     */
    public Set<UnitReference> unitsForActions(Collection<String> actions) {
        if (!hasActionGroupsFor(actions)) {
            return Set.of();
        }
        Set<String> requestedGroups = new LinkedHashSet<>();
        for (String action : actions) {
            requestedGroups.add(actionGroups.get(action));
        }
        Set<UnitReference> selected = new LinkedHashSet<>();
        groups.stream().filter(group -> requestedGroups.contains(group.id()))
                .forEach(group -> selected.addAll(group.units()));
        return Set.copyOf(selected);
    }

    public void replace(UUID targetMapId, List<Group> requestedGroups,
                        Map<String, String> requestedActionGroups,
                        Set<UnitReference> requestedExcludedUnits) {
        mapId = targetMapId;
        groups.clear();
        if (requestedGroups != null) {
            requestedGroups.stream().filter(Objects::nonNull)
                    .map(Group::normalized).forEach(groups::add);
        }
        actionGroups.clear();
        if (requestedActionGroups != null) {
            requestedActionGroups.forEach((action, group) -> {
                String normalizedAction = clean(action, 96);
                String normalizedGroup = clean(group, 64);
                if (!normalizedAction.isBlank() && !normalizedGroup.isBlank()
                        && groups.stream().anyMatch(value -> value.id().equals(normalizedGroup))) {
                    actionGroups.put(normalizedAction, normalizedGroup);
                }
            });
        }
        excludedUnits.clear();
        if (requestedExcludedUnits != null) {
            requestedExcludedUnits.stream().filter(Objects::nonNull)
                    .forEach(excludedUnits::add);
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        if (mapId != null) {
            tag.putUUID("MapId", mapId);
        }
        ListTag groupTags = new ListTag();
        groups.forEach(group -> groupTags.add(group.toTag()));
        tag.put("Groups", groupTags);
        CompoundTag bindings = new CompoundTag();
        actionGroups.forEach(bindings::putString);
        tag.put("ActionGroups", bindings);
        ListTag excludedTags = new ListTag();
        excludedUnits.forEach(unit -> excludedTags.add(unit.toTag()));
        tag.put("ExcludedUnits", excludedTags);
        return tag;
    }

    public static ScmConfigurationProfile fromTag(CompoundTag tag) {
        ScmConfigurationProfile profile = new ScmConfigurationProfile();
        if (tag == null || tag.isEmpty()) {
            return profile;
        }
        profile.mapId = tag.hasUUID("MapId") ? tag.getUUID("MapId") : null;
        ListTag groupTags = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int index = 0; index < groupTags.size() && profile.groups.size() < 128; index++) {
            Group group = Group.fromTag(groupTags.getCompound(index));
            if (!group.id().isBlank() && profile.groups.stream()
                    .noneMatch(value -> value.id().equals(group.id()))) {
                profile.groups.add(group);
            }
        }
        CompoundTag bindings = tag.getCompound("ActionGroups");
        for (String action : bindings.getAllKeys()) {
            String group = clean(bindings.getString(action), 64);
            if (!clean(action, 96).isBlank() && profile.groups.stream()
                    .anyMatch(value -> value.id().equals(group))) {
                profile.actionGroups.put(clean(action, 96), group);
            }
        }
        ListTag excludedTags = tag.getList("ExcludedUnits", Tag.TAG_COMPOUND);
        for (int index = 0; index < excludedTags.size() && profile.excludedUnits.size() < 2048; index++) {
            UnitReference unit = UnitReference.fromTag(excludedTags.getCompound(index));
            if (unit.isValid()) {
                profile.excludedUnits.add(unit);
            }
        }
        return profile;
    }

    public record Group(String id, String label, Set<UnitReference> units) {
        public Group {
            id = clean(id, 64);
            label = clean(label, 96);
            units = units == null ? Set.of() : Set.copyOf(units);
        }

        private Group normalized() {
            return new Group(id, label.isBlank() ? id : label, units);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Label", label);
            ListTag unitTags = new ListTag();
            units.stream().filter(UnitReference::isValid).forEach(unit -> unitTags.add(unit.toTag()));
            tag.put("Units", unitTags);
            return tag;
        }

        public static Group fromTag(CompoundTag tag) {
            Set<UnitReference> units = new LinkedHashSet<>();
            ListTag unitTags = tag.getList("Units", Tag.TAG_COMPOUND);
            for (int index = 0; index < unitTags.size() && units.size() < 2048; index++) {
                UnitReference unit = UnitReference.fromTag(unitTags.getCompound(index));
                if (unit.isValid()) {
                    units.add(unit);
                }
            }
            return new Group(tag.getString("Id"), tag.getString("Label"), units);
        }
    }

    /**
     * One player-selected SCM target. {@code adapter} is optional: an empty
     * value means "resolve the best safe control through the SCM probe API at
     * initialization". A face identifies one redstone input face on the block
     * and is deliberately part of the identity so opposite inputs can belong
     * to different action groups.
     */
    public record UnitReference(UUID subLevelId, BlockPos blockPosition, String adapter,
                                Direction face) {
        public UnitReference {
            blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
            adapter = clean(adapter, 128);
        }

        public UnitReference(UUID subLevelId, BlockPos blockPosition, String adapter) {
            this(subLevelId, blockPosition, adapter, null);
        }

        public static UnitReference of(ShipControlMap.PropulsionUnit unit) {
            return new UnitReference(unit.subLevelId(), unit.blockPosition(), unit.adapter());
        }

        public boolean isValid() {
            return subLevelId != null;
        }

        public boolean usesFaceControl() {
            return face != null;
        }

        public UnitReference withFace(Direction value) {
            return new UnitReference(subLevelId, blockPosition, adapter, value);
        }

        public boolean sameBlock(UnitReference other) {
            return other != null && Objects.equals(subLevelId, other.subLevelId)
                    && blockPosition.equals(other.blockPosition);
        }

        public boolean sameBlock(ShipControlMap.PropulsionUnit unit) {
            return unit != null && Objects.equals(subLevelId, unit.subLevelId())
                    && blockPosition.equals(unit.blockPosition());
        }

        public boolean matches(ShipControlMap.PropulsionUnit unit) {
            if (!sameBlock(unit)) return false;
            if (adapter.isBlank()) return true;
            if (adapter.equals(unit.adapter())) {
                return true;
            }
            // A face selection starts from a general block candidate (whose
            // adapter may be direct_signal_v5 or another ordinary adapter),
            // but initialization intentionally upgrades that selection to a
            // face-scoped SCM probe. The physical block and face are the
            // identity in that case, not the pre-selection adapter string.
            if (face != null && unit.adapter().startsWith("scm:")
                    && unit.adapter().endsWith(":face_" + face.getSerializedName())) {
                return true;
            }
            // Face bindings add a stable suffix to the runtime probe id. Accept
            // the base id as well so profiles authored by an older client, or
            // supplied by an integration, continue to identify that same face.
            return face != null && unit.adapter().equals(
                    adapter + ":face_" + face.getSerializedName());
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (subLevelId != null) {
                tag.putUUID("SubLevelId", subLevelId);
            }
            tag.putInt("X", blockPosition.getX());
            tag.putInt("Y", blockPosition.getY());
            tag.putInt("Z", blockPosition.getZ());
            tag.putString("Adapter", adapter);
            if (face != null) {
                tag.putString("Face", face.getSerializedName());
            }
            return tag;
        }

        public static UnitReference fromTag(CompoundTag tag) {
            Direction face = Direction.byName(tag.getString("Face"));
            return new UnitReference(tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : null,
                    new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                    tag.getString("Adapter"), face);
        }
    }

    private static String clean(String value, int maximum) {
        String result = value == null ? "" : value.strip();
        if (result.length() > maximum) {
            result = result.substring(0, maximum);
        }
        return result.chars().anyMatch(Character::isISOControl) ? "" : result;
    }
}
