package me.foesio.foColours.api;

/** Result of an API action. Storage failures throw IllegalStateException instead of reporting success. */
public enum ColourChangeResult {
    CHANGED,
    NO_CHANGE,
    UNKNOWN_OR_DISABLED_COLOUR,
    NOT_UNLOCKED,
    DEFAULT_PROTECTED,
    RESET_DISABLED,
    NO_ENABLED_COLOURS
}
