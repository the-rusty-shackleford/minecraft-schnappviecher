/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: watched/unwatched, before/at delay, near/far/occluded, each stage;
 * hits before/at immunity boundary, three blows, sight lost/reacquired, invalid values,
 * ordinary and maximum integer counters restored from saved state. */
class EncounterTest {
    @Test void theftNeedsTimeAnUnseenApproachAndReach() {
        Encounter e = new Encounter(Encounter.Stage.STALK, 1200, 0, 0, 0);
        assertTrue(e.canSteal(1200, false, true, 2.4));
        assertFalse(e.canSteal(1201, false, true, 2));
        assertFalse(e.canSteal(1200, true, true, 2));
        assertFalse(e.canSteal(1200, false, false, 2));
        assertFalse(e.canSteal(1200, false, true, 2.401));
        assertFalse(e.stolen().canSteal(0, false, true, 1));
    }
    @Test void rapidHitsCannotSkipTheChaseButThreeSpacedHitsSurrender() {
        Encounter e = Encounter.start().stolen().hit();
        assertEquals(1, e.blows());
        assertSame(e, e.hit());
        for (int i=0;i<9;i++) e=e.tick(false);
        assertSame(e,e.hit());
        e=e.tick(false).hit();
        assertEquals(2,e.blows());
        for (int i=0;i<10;i++) e=e.tick(false);
        e=e.hit();
        assertEquals(Encounter.Stage.RETREAT,e.stage());
        assertSame(e,e.hit());
    }
    @Test void anEscapeRequiresBothAChaseWindowAndSustainedConcealment() {
        Encounter e = new Encounter(Encounter.Stage.CHASE,600,0,0,40);
        assertTrue(e.canEscape(600,12));
        assertFalse(e.canEscape(601,12));
        assertFalse(e.canEscape(600,11.99));
        assertFalse(e.tick(true).canEscape(600,20));
        assertFalse(e.enter(Encounter.Stage.RANSOM).canEscape(0,20));
    }
    @Test void transitionsPreserveHitsAndResetTheirOwnClock() {
        Encounter e=Encounter.start().tick(false).hit().stolen();
        assertEquals(0,e.age()); assertEquals(1,e.blows());
        assertEquals(Encounter.HIT_INTERVAL,e.immunity());
        assertEquals(0,e.unseen());
    }
    @Test void largeRestoredCountersSaturateInsteadOfOverflowing() {
        Encounter e=new Encounter(Encounter.Stage.CHASE,Integer.MAX_VALUE,0,0,Integer.MAX_VALUE).tick(false);
        assertTrue(e.age()>=600&&e.unseen()>=Encounter.ESCAPE_UNSEEN);
        assertTrue(e.canEscape(600,12));
    }
    @Test void invalidStatesAndWrongPhaseTransitionsFailAtTheirBoundary() {
        assertThrows(IllegalArgumentException.class,()->new Encounter(null,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Encounter(Encounter.Stage.STALK,-1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Encounter(Encounter.Stage.STALK,0,4,0,0));
        assertThrows(IllegalStateException.class,()->Encounter.start().stolen().stolen());
    }
}
