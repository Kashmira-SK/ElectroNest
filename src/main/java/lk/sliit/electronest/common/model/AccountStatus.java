package lk.sliit.electronest.common.model;

/**
 * Tracks the lifecycle state of a user account.
 * ACTIVE      - normal, can log in and use the platform
 * DEACTIVATED - turned off by the user or admin and cannot log in
 * SUSPENDED   - can log in and browse, but cannot change platform state
 */
public enum AccountStatus {
    ACTIVE,
    DEACTIVATED,
    SUSPENDED
}
