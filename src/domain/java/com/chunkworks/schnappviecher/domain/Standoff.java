/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a stalking creature waits relative to its victim: a post on a ring around the player.
 * A post, once taken, is kept while the player stays within the ring's band of it, so a turn of
 * the player's head moves the creature nowhere (being looked at freezes it; that is another
 * rule) and only their walking away or toward it makes it move. When a new post is needed the
 * candidates are the ring's points out of the player's forward view, nearest to the creature
 * first, not the one straight behind: a small step round the ring rather than a march to the far
 * side. Pure geometry on the horizontal plane; reachability is the caller's.
 */
public final class Standoff {
    /** A horizontal position. */
    public record Point(double x, double z) {
        /** requires: none; effects: the horizontal distance to the other point; throws: none. */
        public double distanceTo(Point other) { return Math.hypot(x - other.x, z - other.z); }
    }
    private Standoff() {}

    /** requires: ring > 0, 0 <= slack < 1; effects: whether the post is still a place to wait:
     * its distance from the player within ring times (1 - slack) to ring times (1 + slack),
     * both ends included; throws: IllegalArgumentException for a bad ring or slack. */
    public static boolean acceptable(Point post, Point player, double ring, double slack) {
        if (!(ring > 0) || !(slack >= 0 && slack < 1)) throw new IllegalArgumentException("ring or slack");
        double d = post.distanceTo(player);
        return d >= ring * (1 - slack) - 1e-9 && d <= ring * (1 + slack) + 1e-9;
    }

    /** requires: ring > 0, count >= 1, -1 <= coneCos <= 1; effects: count points evenly spaced on
     * the ring around the player, the first straight behind the player's heading (Minecraft's
     * yaw in radians: 0 faces +z, a right angle faces -x), leaving out those in front: a point
     * is in front when the cosine of the angle between the heading and the direction to it
     * exceeds coneCos, so 1 leaves out none and 0.5 the sixty degrees either side of straight
     * ahead; ordered by their distance from the mob, ties by their place round the ring from
     * behind; throws: IllegalArgumentException for bad arguments. */
    public static List<Point> candidates(Point mob, Point player, double yawRadians, double ring, int count, double coneCos) {
        if (!(ring > 0) || count < 1 || !(coneCos >= -1 && coneCos <= 1)) throw new IllegalArgumentException("ring, count or cone");
        double fx = -Math.sin(yawRadians), fz = Math.cos(yawRadians);
        record Ranked(Point point, double distance, int index) {}
        var ranked = new ArrayList<Ranked>(count);
        for (int i = 0; i < count; i++) {
            double theta = Math.PI + i * (2 * Math.PI / count);
            double cos = Math.cos(theta), sin = Math.sin(theta);
            double dx = fx * cos - fz * sin, dz = fx * sin + fz * cos;
            if (dx * fx + dz * fz > coneCos) continue;
            var point = new Point(player.x + dx * ring, player.z + dz * ring);
            ranked.add(new Ranked(point, point.distanceTo(mob), i));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::distance).thenComparingInt(Ranked::index));
        return ranked.stream().map(Ranked::point).toList();
    }
}
