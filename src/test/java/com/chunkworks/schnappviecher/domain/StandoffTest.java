/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;

import com.chunkworks.schnappviecher.domain.Standoff.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions. acceptable: inside the band; at either edge; nearer than the band; farther; no
 * slack; a bad ring or slack. candidates: the first is straight behind for yaw 0 and for a right
 * angle; the front and its neighbours within the cone are left out, a cone of 1 leaves all in;
 * the nearest to the mob comes first (the mob behind, the mob to one side, the mob beyond the
 * ring on a tie); one point; a bad ring, count or cone; the list is immutable. */
final class StandoffTest {
    private static final Point PLAYER = new Point(48, 48);
    private static void near(Point expected, Point actual) {
        assertEquals(expected.x(), actual.x(), 1e-6, "x of " + actual);
        assertEquals(expected.z(), actual.z(), 1e-6, "z of " + actual);
    }

    @Test void aPostIsKeptWhileThePlayerStaysInTheBand() {
        assertTrue(Standoff.acceptable(new Point(48, 36), PLAYER, 12, .25), "twelve away");
        assertTrue(Standoff.acceptable(new Point(48, 39), PLAYER, 12, .25), "nine, the near edge");
        assertTrue(Standoff.acceptable(new Point(48, 33), PLAYER, 12, .25), "fifteen, the far edge");
        assertFalse(Standoff.acceptable(new Point(48, 40), PLAYER, 12, .25), "eight: too near");
        assertFalse(Standoff.acceptable(new Point(48, 32), PLAYER, 12, .25), "sixteen: too far");
        assertTrue(Standoff.acceptable(new Point(60, 48), PLAYER, 12, 0), "no slack: exactly the ring");
        assertFalse(Standoff.acceptable(new Point(60.5, 48), PLAYER, 12, 0));
    }
    @Test void acceptableRefusesBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> Standoff.acceptable(PLAYER, PLAYER, 0, .25));
        assertThrows(IllegalArgumentException.class, () -> Standoff.acceptable(PLAYER, PLAYER, 12, -.1));
        assertThrows(IllegalArgumentException.class, () -> Standoff.acceptable(PLAYER, PLAYER, 12, 1));
    }
    @Test void theFirstCandidateIsStraightBehindTheHeading() {
        var behind = Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 8, .5);
        near(new Point(48, 36), behind.get(0));
        var facingMinusX = Standoff.candidates(new Point(60, 48), PLAYER, Math.toRadians(90), 12, 8, .5);
        near(new Point(60, 48), facingMinusX.get(0));
        near(new Point(48, 36), Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 1, .5).get(0));
        assertEquals(1, Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 1, .5).size());
    }
    @Test void pointsInFrontAreLeftOut() {
        var out = Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 8, .5);
        assertEquals(5, out.size(), "straight ahead and the two at forty-five degrees are in the cone: " + out);
        for (var p : out) assertTrue(p.z() <= 48 + 1e-6, "nothing ahead of the player: " + p);
        assertEquals(8, Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 8, 1).size(), "a cone of one leaves all in");
        assertEquals(7, Standoff.candidates(new Point(48, 36), PLAYER, 0, 12, 8, .99).size(), "just under one leaves out straight ahead only");
    }
    @Test void theNearestToTheMobComesFirst() {
        var fromTheEast = Standoff.candidates(new Point(70, 48), PLAYER, 0, 12, 8, .5);
        near(new Point(60, 48), fromTheEast.get(0));
        var fromTheWest = Standoff.candidates(new Point(30, 50), PLAYER, 0, 12, 8, .5);
        near(new Point(36, 48), fromTheWest.get(0));
        var farBehind = Standoff.candidates(new Point(48, 0), PLAYER, 0, 12, 8, .5);
        near(new Point(48, 36), farBehind.get(0));
        var tie = Standoff.candidates(new Point(48, 100), PLAYER, 0, 12, 8, .5);
        var first = tie.get(0);
        assertEquals(48, first.z(), 1e-6, "the two beside the player tie; the earlier round the ring from behind wins: " + tie);
        assertTrue(Math.abs(first.x() - 48) > 11.9);
    }
    @Test void candidatesRefuseBadArgumentsAndAreImmutable() {
        assertThrows(IllegalArgumentException.class, () -> Standoff.candidates(PLAYER, PLAYER, 0, 0, 8, .5));
        assertThrows(IllegalArgumentException.class, () -> Standoff.candidates(PLAYER, PLAYER, 0, 12, 0, .5));
        assertThrows(IllegalArgumentException.class, () -> Standoff.candidates(PLAYER, PLAYER, 0, 12, 8, 1.5));
        assertThrows(UnsupportedOperationException.class, () -> Standoff.candidates(PLAYER, PLAYER, 0, 12, 8, .5).clear());
        assertEquals(5, new Point(0, 0).distanceTo(new Point(3, 4)), 1e-9);
        assertEquals(5, new Point(3, 4).distanceTo(new Point(0, 0)), 1e-9, "distance is symmetric");
    }
}
