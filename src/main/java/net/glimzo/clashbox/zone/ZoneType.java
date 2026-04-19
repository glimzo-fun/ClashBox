package net.glimzo.clashbox.zone;

public enum ZoneType {
    OUTER,
    MID,
    CORE;

    public String displayName() {
        return switch (this) {
            case OUTER -> "&aOuter Zone";
            case MID   -> "&eMiddle Zone";
            case CORE  -> "&cCore Zone";
        };
    }

    public String configKey() {
        return name().toLowerCase();
    }
}
