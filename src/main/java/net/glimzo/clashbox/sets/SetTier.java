package net.glimzo.clashbox.sets;

public enum SetTier {

    ONE  (1, "&7", "\u2605\u2606\u2606\u2606\u2606", "Common"),
    TWO  (2, "&e", "\u2605\u2605\u2606\u2606\u2606", "Uncommon"),
    THREE(3, "&d", "\u2605\u2605\u2605\u2606\u2606", "Rare"),
    FOUR (4, "&9", "\u2605\u2605\u2605\u2605\u2606", "Epic"),
    FIVE (5, "&8", "\u2605\u2605\u2605\u2605\u2605", "Legendary");

    private final int    number;
    private final String color;
    private final String stars;
    private final String rarity;

    SetTier(int number, String color, String stars, String rarity) {
        this.number = number;
        this.color  = color;
        this.stars  = stars;
        this.rarity = rarity;
    }

    public int    getNumber() { return number; }
    public String getColor()  { return color; }
    public String getStars()  { return stars; }
    public String getRarity() { return rarity; }

    public String display() {
        return color + stars + " &7(" + rarity + ")";
    }

    public static SetTier fromNumber(int n) {
        for (SetTier t : values()) if (t.number == n) return t;
        return ONE;
    }
}
