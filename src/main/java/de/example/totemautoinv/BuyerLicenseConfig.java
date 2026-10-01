package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.System;
import net.minecraft.nbt.CompoundTag;

/** Stores only the unlocked state for the separate buyer edition. */
public final class BuyerLicenseConfig extends System<BuyerLicenseConfig> {
    private boolean licensed;

    public BuyerLicenseConfig() {
        super("chalk-client/license");
    }

    public boolean isLicensed() {
        return licensed;
    }

    public void unlock() {
        licensed = true;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putBoolean("licensed", licensed);
        return tag;
    }

    @Override
    public BuyerLicenseConfig fromTag(CompoundTag tag) {
        licensed = tag.getBooleanOr("licensed", false);
        return this;
    }
}
