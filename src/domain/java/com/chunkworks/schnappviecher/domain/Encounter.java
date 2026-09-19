/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;

/**
 * Immutable progress of one prank. AF: stage, age in that stage, landed blows,
 * remaining hit immunity, and consecutive unseen ticks describe the encounter.
 * RI: all counters nonnegative, blows <= 3, stage nonnull. Item ownership is
 * deliberately outside this value and changes only after the real inventory does.
 */
public record Encounter(Stage stage, int age, int blows, int immunity, int unseen) {
    public enum Stage { STALK, CHASE, RANSOM, RETREAT }
    public static final int SURRENDER_BLOWS = 3;
    public static final int HIT_INTERVAL = 10;
    public static final int ESCAPE_UNSEEN = 40;

    /** requires: nonnull stage and counters satisfying RI; effects: creates immutable progress; throws: IllegalArgumentException for invalid counters. */
    public Encounter {
        if (stage == null || age < 0 || blows < 0 || blows > SURRENDER_BLOWS || immunity < 0 || unseen < 0)
            throw new IllegalArgumentException("invalid encounter counters");
    }
    /** requires: none; effects: creates a fresh stalk; throws: none. */
    public static Encounter start() { return new Encounter(Stage.STALK, 0, 0, 0, 0); }
    /** requires: none; effects: advances one tick; throws: none. */
    public Encounter tick(boolean watched) {
        return new Encounter(stage, Math.min(age, 999_999) + 1, blows,
                Math.max(0, immunity - 1), watched ? 0 : Math.min(unseen, 999_999) + 1);
    }
    /** requires: none; effects: counts at most one hit per immunity interval; throws: none. */
    public Encounter hit() {
        if (immunity > 0 || stage == Stage.RETREAT) return this;
        int count = Math.min(blows + 1, SURRENDER_BLOWS);
        return new Encounter(count == SURRENDER_BLOWS ? Stage.RETREAT : stage, age, count, HIT_INTERVAL, unseen);
    }
    /** requires: stage STALK; effects: begins the chase only after an item was taken; throws: IllegalStateException otherwise. */
    public Encounter stolen() {
        if (stage != Stage.STALK) throw new IllegalStateException("not stalking");
        return new Encounter(Stage.CHASE, 0, blows, immunity, 0);
    }
    /** requires: minimumTicks >= 0; effects: tests a fair chase and actual escape from sight; throws: IllegalArgumentException for negative time. */
    public boolean canEscape(int minimumTicks, double distance) {
        if (minimumTicks < 0) throw new IllegalArgumentException("negative chase time");
        return stage == Stage.CHASE && age >= minimumTicks && distance >= 12 && unseen >= ESCAPE_UNSEEN;
    }
    /** requires: none; effects: changes stage and resets its age; throws: IllegalArgumentException for null stage. */
    public Encounter enter(Stage next) { return new Encounter(next, 0, blows, immunity, 0); }
    /** requires: delay >= 0; effects: allows theft only during an unwatched, in-reach stalk; throws: IllegalArgumentException for negative delay. */
    public boolean canSteal(int delay, boolean watched, boolean clearPath, double distance) {
        if (delay < 0) throw new IllegalArgumentException("negative stalk time");
        return stage == Stage.STALK && age >= delay && !watched && clearPath && distance <= 2.4;
    }
}
