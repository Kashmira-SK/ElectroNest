package lk.sliit.electronest.admin.entity;

/**
 * Tracks the lifecycle state of a user account.
 * ACTIVE      - normal, can log in and use the platform
 * DEACTIVATED - turned off by the user or admin, can be reactivated
 * SUSPENDED   - forcibly disabled by an admin due to a policy violation
 */
public enum AccountStatus {
    ACTIVE,
    DEACTIVATED,
    SUSPENDED
}
