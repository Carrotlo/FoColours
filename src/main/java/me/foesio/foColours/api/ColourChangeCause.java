package me.foesio.foColours.api;

/** Origin of a persisted FoColours state change. */
public enum ColourChangeCause {
    PLAYER_COMMAND,
    PLAYER_MENU,
    ADMIN_COMMAND,
    API,
    EZCOLORS_SYNC,
    RELOAD
}
