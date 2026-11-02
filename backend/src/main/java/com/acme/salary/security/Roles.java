package com.acme.salary.security;

/** SpEL expressions for {@code @PreAuthorize}, kept in one place so permissions are auditable. */
public final class Roles {

    public static final String CAN_MANAGE_EMPLOYEES = "hasRole('HR_MANAGER')";

    private Roles() {
    }
}
