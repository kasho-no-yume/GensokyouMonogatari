package com.bitsson.gensokyou.client.resident;

/** Immutable visual choices. No world seed, entity or mutable random source is involved. */
public record ResidentAppearance(Outfit outfit, HeadStyle headStyle, Accessory accessory,
                                 int cloth, int sash, int skin, int hair, float scale,
                                 double speed, double pauseTicks, double phaseTicks) {
    public enum Outfit { COAT, ROBE, APRON }
    public enum HeadStyle { SHORT_HAIR, TOPKNOT, HEADSCARF, STRAW_HAT }
    public enum Accessory { NONE, SATCHEL, BUNDLE }

    private static final int[] CLOTH = {
            0xFF546A8B, 0xFF697B60, 0xFFAD935F, 0xFF846651, 0xFF8B6062, 0xFF656C78
    };
    private static final int[] SASH = {0xFFC9B997, 0xFF4C4A45, 0xFF8F5E45};
    private static final int[] SKIN = {0xFFFFFFFF, 0xFFE8D0B9, 0xFFCBAD91};
    private static final int[] HAIR = {0xFF332D29, 0xFF554235, 0xFF837E71};

    public static ResidentAppearance of(String identity) {
        long seed = hash(identity);
        Outfit outfit = Outfit.values()[index(seed, 0, Outfit.values().length)];
        HeadStyle head = HeadStyle.values()[index(seed, 1, HeadStyle.values().length)];
        Accessory accessory = Accessory.values()[index(seed, 2, Accessory.values().length)];
        // A work apron and a bulky back bundle are an awkward combination.
        if (outfit == Outfit.APRON && accessory == Accessory.BUNDLE) accessory = Accessory.SATCHEL;
        return new ResidentAppearance(outfit, head, accessory,
                CLOTH[index(seed, 3, CLOTH.length)], SASH[index(seed, 4, SASH.length)],
                SKIN[index(seed, 5, SKIN.length)], HAIR[index(seed, 6, HAIR.length)],
                0.92F + index(seed, 7, 9) * 0.0125F,
                0.030 + index(seed, 8, 11) * 0.0015,
                35 + index(seed, 9, 66), index(seed, 10, 4000));
    }

    public static long hash(String text) {
        long value = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            value = (value ^ text.charAt(i)) * 0x100000001b3L;
        }
        return value;
    }

    private static int index(long seed, int channel, int count) {
        long value = seed + (channel + 1L) * 0x9e3779b97f4a7c15L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return (int) Math.floorMod(value ^ (value >>> 31), count);
    }
}
