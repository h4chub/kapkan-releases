package gg.kapkan.presence.cosmetics;

public final class DurabilityStyleTest {
    private static int checks;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
    private static int channel(int color, int shift) { return color >>> shift & 255; }
    private static void color(int remaining, int maximum, int expected, String message) {
        check(DurabilityStyle.color(remaining, maximum) == expected, message);
    }
    private static void label(int damage, int maximum, String expected) {
        check(DurabilityStyle.label(damage, maximum).equals(expected),
            "Remaining/maximum label for damage=" + damage + ", maximum=" + maximum);
    }

    public static void main(String[] args) {
        color(0, 500, 0xFFE04B4B, "Empty durability is readable crimson");
        color(250, 500, 0xFFE2B93B, "Half durability is golden yellow");
        color(500, 500, 0xFF388C4A, "Full durability is muted dark green");
        color(498, 500, 0xFF398C4A, "498/500 stays close to the full-durability green");
        color(125, 500, 0xFFE18243, "Quarter durability blends red and yellow");
        color(375, 500, 0xFF8DA343, "Three-quarter durability blends yellow and green");
        int nearEmpty = DurabilityStyle.color(5, 500);
        check(channel(nearEmpty, 16) > 2 * channel(nearEmpty, 8), "Near-empty durability reads red");
        check(channel(nearEmpty, 16) > 2 * channel(nearEmpty, 0), "Near-empty red exceeds blue");
        int nearFull = DurabilityStyle.color(498, 500);
        check(channel(nearFull, 8) > channel(nearFull, 16)
            && channel(nearFull, 8) > channel(nearFull, 0), "498/500 reads green");

        color(-1, 500, 0xFFE04B4B, "Negative remaining durability clamps to empty");
        color(Integer.MIN_VALUE, 500, 0xFFE04B4B, "Minimum integer remaining clamps safely");
        color(501, 500, 0xFF388C4A, "Excess remaining durability clamps to full");
        color(Integer.MAX_VALUE, 500, 0xFF388C4A, "Maximum integer remaining clamps safely");
        color(Integer.MAX_VALUE, Integer.MAX_VALUE, 0xFF388C4A, "Large maximum does not overflow");
        color(Integer.MIN_VALUE, Integer.MAX_VALUE, 0xFFE04B4B, "Large range still clamps negative remaining");
        color(Integer.MAX_VALUE / 2, Integer.MAX_VALUE, 0xFFE2B93B, "Large lower midpoint rounds to yellow");
        color(Integer.MAX_VALUE / 2 + 1, Integer.MAX_VALUE, 0xFFE2B93B, "Large upper midpoint rounds to yellow");
        color(0, 1, 0xFFE04B4B, "Single-point durability supports empty");
        color(1, 1, 0xFF388C4A, "Single-point durability supports full");
        for (int maximum : new int[] {0, -1, Integer.MIN_VALUE}) {
            for (int remaining : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                color(remaining, maximum, 0xFF9FAAA5, "Invalid maximum uses neutral, not an empty-durability error");
            }
        }

        for (int maximum : new int[] {1000, 1_000_000, Integer.MAX_VALUE}) {
            int middle = maximum / 2;
            int before = DurabilityStyle.color(middle - 1, maximum);
            int at = DurabilityStyle.color(middle, maximum);
            int after = DurabilityStyle.color(middle + 1, maximum);
            for (int shift : new int[] {16, 8, 0}) {
                check(Math.abs(channel(at, shift) - channel(before, shift)) <= 1,
                    "No abrupt color jump immediately below half at maximum=" + maximum);
                check(Math.abs(channel(after, shift) - channel(at, shift)) <= 1,
                    "No abrupt color jump immediately above half at maximum=" + maximum);
            }
        }
        int previous = DurabilityStyle.color(0, 500);
        for (int remaining = 25; remaining <= 500; remaining += 25) {
            int current = DurabilityStyle.color(remaining, 500);
            check(current >>> 24 == 255, "Every gradient sample remains opaque");
            boolean lowerHalf = remaining <= 250;
            check(lowerHalf ? channel(current, 16) >= channel(previous, 16)
                : channel(current, 16) <= channel(previous, 16), "Red follows the intended gradient");
            check(lowerHalf ? channel(current, 8) >= channel(previous, 8)
                : channel(current, 8) <= channel(previous, 8), "Green follows the intended gradient");
            check(lowerHalf ? channel(current, 0) <= channel(previous, 0)
                : channel(current, 0) >= channel(previous, 0), "Blue follows the intended gradient");
            previous = current;
        }

        label(2, 500, "498/500");
        label(0, 500, "500/500");
        label(250, 500, "250/500");
        label(499, 500, "1/500");
        label(500, 500, "0/500");
        label(501, 500, "0/500");
        label(-1, 500, "500/500");
        label(Integer.MIN_VALUE, 500, "500/500");
        label(Integer.MAX_VALUE, 500, "0/500");
        label(Integer.MIN_VALUE, Integer.MAX_VALUE, "2147483647/2147483647");
        label(Integer.MAX_VALUE, Integer.MAX_VALUE, "0/2147483647");
        label(1, Integer.MAX_VALUE, "2147483646/2147483647");
        label(Integer.MAX_VALUE, 0, "0/0");
        label(Integer.MIN_VALUE, -1, "0/0");
        label(Integer.MAX_VALUE, Integer.MIN_VALUE, "0/0");
        System.out.println("Durability style: " + checks + " checks passed");
    }
}
