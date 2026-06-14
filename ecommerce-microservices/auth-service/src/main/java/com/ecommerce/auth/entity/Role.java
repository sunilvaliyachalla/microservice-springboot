package com.ecommerce.auth.entity;

/**
 * Roles ordered by privilege. A user may only create users whose rank is
 * strictly lower than their own.
 */
public enum Role {
    USER(0),
    MANAGER(1),
    ADMIN(2),
    SUPERADMIN(3);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public static Role from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
