package com.smartboard.teach.core.util

import java.security.MessageDigest

/**
 * SHA-256 over the seeded demo passwords.
 *
 * This is deliberately NOT a password-storage scheme fit for real credentials
 * — there is no salt and no key stretching. It exists only so the Phase 1
 * login screen compares hashes rather than plaintext while the ERP is absent.
 * Phase 2 delegates authentication to the ERP and stops reading this column
 * entirely; no real teacher password ever reaches this function.
 */
object Hashing {
    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
