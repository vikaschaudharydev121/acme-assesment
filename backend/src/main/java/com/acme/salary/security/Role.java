package com.acme.salary.security;

public enum Role {
    /** Full read/write access to employee and salary data. */
    HR_MANAGER,
    /** Read-only access: directory, salary history and insights. */
    HR_VIEWER
}
