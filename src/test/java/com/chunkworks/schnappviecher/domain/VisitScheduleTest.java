/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Partitions: first/repeat visit; just before/at cooldown; inactive/indebted;
 * interval endpoints/equal endpoints; invalid bounds/random samples. */
class VisitScheduleTest {
    @Test void firstVisitAndCooldownBoundary() {
        assertTrue(VisitSchedule.eligible(0,-1,432000,true,false));
        assertFalse(VisitSchedule.eligible(432999,1000,432000,true,false));
        assertTrue(VisitSchedule.eligible(433000,1000,432000,true,false));
        assertFalse(VisitSchedule.eligible(0,1000,432000,true,false));
    }
    @Test void spectatorsAndExistingDebtsAreNeverNewVictims() {
        assertFalse(VisitSchedule.eligible(100000,-1,0,false,false));
        assertFalse(VisitSchedule.eligible(100000,-1,0,true,true));
    }
    @Test void randomIntervalIncludesBothEndpoints() {
        assertEquals(144000,VisitSchedule.interval(144000,288000,0));
        assertEquals(288000,VisitSchedule.interval(144000,288000,Math.nextDown(1.0)));
        assertEquals(7,VisitSchedule.interval(7,7,.5));
    }
    @Test void rejectsInvalidSamplingInputs() {
        assertThrows(IllegalArgumentException.class,()->VisitSchedule.interval(4,3,.5));
        assertThrows(IllegalArgumentException.class,()->VisitSchedule.interval(1,2,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->VisitSchedule.interval(1,2,1));
    }
}
