package com.askvocate.backend.entity;

/**
 * How the user authenticates:
 *  - LOCAL  → classic email/phone + password (registration form)
 *  - GOOGLE → federated via Google Sign-In (no local password)
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
