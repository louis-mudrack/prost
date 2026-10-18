package dev.louismudrack.prost.api;

/** Where sips came from: a lost challenge, the roulette, or a winner handing them out. */
public enum SipSource {
    /** a lost challenge */
    CHALLENGE,
    /** the action roulette */
    ROULETTE,
    /** a winner handing sips out */
    HANDOUT
}
