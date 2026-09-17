package lk.sliit.electronest.common.validation;

/** Shared strict rule; input is not truncated or silently stripped of invalid characters. */
public final class SriLankanPhone {
    public static final String REGEX = "(?:0[0-9]{9}|\\+94[0-9]{9})";

    private SriLankanPhone() {}
}
