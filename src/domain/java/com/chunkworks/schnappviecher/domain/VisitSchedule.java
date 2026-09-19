/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;

/** Pure scheduling rules. No instances or mutable representation. */
public final class VisitSchedule {
    private VisitSchedule() {}
    /** requires: nonnegative ticks and cooldown; effects: checks eligibility without subtraction overflow; throws: IllegalArgumentException for invalid times. */
    public static boolean eligible(long now, long lastVisit, long cooldown, boolean playing, boolean owes) {
        if (now < 0 || lastVisit < -1 || cooldown < 0) throw new IllegalArgumentException("invalid time");
        return playing && !owes && (lastVisit == -1 || now >= lastVisit && now - lastVisit >= cooldown);
    }
    /** requires: 0 <= unit < 1, 0 < min <= max; effects: chooses an inclusive interval; throws: IllegalArgumentException on invalid input. */
    public static int interval(int min, int max, double unit) {
        if (min < 1 || max < min || !(unit >= 0 && unit < 1)) throw new IllegalArgumentException("invalid interval");
        return min + (int) ((max - (long) min + 1) * unit);
    }
}
