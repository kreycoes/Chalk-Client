package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Stores Chalk-only module state separately from Meteor's global modules.nbt.
 * The file is written by Meteor's normal system save lifecycle and is safe to
 * remove when the user wants to reset only Chalk Client.
 */
public final class ChalkClientConfig extends System<ChalkClientConfig> {
    private static final String MODULES_KEY = "modules";

    public ChalkClientConfig() {
        super("chalk-client/settings");
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2);

        ListTag moduleTags = new ListTag();
        addCategoryModules(moduleTags, TotemAutoInvAddon.KREY_ADDON);
        addCategoryModules(moduleTags, TotemAutoInvAddon.CHALK_ESP);
        addCategoryModules(moduleTags, TotemAutoInvAddon.CHALK_COMBAT);
        addCategoryModules(moduleTags, TotemAutoInvAddon.CHALK_UTILITY);
        tag.put(MODULES_KEY, moduleTags);

        return tag;
    }

    private static void addCategoryModules(ListTag moduleTags, meteordevelopment.meteorclient.systems.modules.Category category) {
        for (Module module : Modules.get().getGroup(category)) {
            CompoundTag moduleTag = module.toTag();
            if (moduleTag != null) moduleTags.add(moduleTag);
        }
    }

    @Override
    public ChalkClientConfig fromTag(CompoundTag tag) {
        ListTag moduleTags = tag.getListOrEmpty(MODULES_KEY);
        for (int index = 0; index < moduleTags.size(); index++) {
            CompoundTag moduleTag = moduleTags.getCompoundOrEmpty(index);
            String name = moduleTag.getStringOr("name", "");
            Module module = Modules.get().get(name);

            // Never let this file alter a Meteor module with the same name.
            if (module != null && TotemAutoInvAddon.isChalkCategory(module.category)) {
                module.fromTag(moduleTag);
            }
        }

        return this;
    }
}
