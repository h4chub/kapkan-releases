package gg.kapkan.presence.cosmetics;

/** Durability presentation without game state or rendering dependencies. */
public final class DurabilityStyle {
    private static final int LOW = 0xFFE04B4B;
    private static final int HALF = 0xFFE2B93B;
    private static final int FULL = 0xFF388C4A;
    private static final int UNKNOWN = 0xFF9FAAA5;

    private DurabilityStyle() {}

    /** Opaque red through yellow to muted green, based on remaining durability. */
    public static int color(int remaining, int maximum) {
        if (maximum <= 0) return UNKNOWN;
        long position = 2L * Math.max(0, Math.min(remaining, maximum));
        return position <= maximum
            ? interpolate(LOW, HALF, position, maximum)
            : interpolate(HALF, FULL, position - maximum, maximum);
    }

    /** Remaining/maximum; invalid maxima describe unknown durability as 0/0. */
    public static String label(int damage, int maximum) {
        int total = Math.max(0, maximum);
        int worn = Math.max(0, Math.min(damage, total));
        return (total - worn) + "/" + total;
    }

    private static int interpolate(int from, int to, long position, long span) {
        return 0xFF000000
            | channel(from >>> 16 & 255, to >>> 16 & 255, position, span) << 16
            | channel(from >>> 8 & 255, to >>> 8 & 255, position, span) << 8
            | channel(from & 255, to & 255, position, span);
    }

    private static int channel(int from, int to, long position, long span) {
        return (int) ((from * (span - position) + to * position + span / 2) / span);
    }
}
