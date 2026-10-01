package de.example.totemautoinv;

import meteordevelopment.meteorclient.systems.System;
import net.minecraft.nbt.CompoundTag;

/** Persistent activation timestamp for the separate time-limited beta build. */
public final class BetaTrialConfig extends System<BetaTrialConfig> {
    private static final String ACTIVATED_AT_KEY = "activated-at";
    private long activatedAt;

    public BetaTrialConfig() {
        super("chalk-client-beta/trial");
    }

    public boolean isActivated() {
        return activatedAt > 0L;
    }

    public void activate(long currentTimeMillis) {
        activatedAt = Math.max(1L, currentTimeMillis);
        save();
    }

    public boolean isExpired(long currentTimeMillis, long durationMillis) {
        return isActivated() && currentTimeMillis - activatedAt >= durationMillis;
    }

    public long getRemainingMillis(long currentTimeMillis, long durationMillis) {
        if (!isActivated()) return 0L;
        return Math.max(0L, durationMillis - (currentTimeMillis - activatedAt));
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2);
        tag.putLong(ACTIVATED_AT_KEY, activatedAt);
        return tag;
    }

    @Override
    public BetaTrialConfig fromTag(CompoundTag tag) {
        activatedAt = Math.max(0L, tag.getLongOr(ACTIVATED_AT_KEY, 0L));
        return this;
    }
}
