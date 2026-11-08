package com.reconvene.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuEmailTest {

    @Test
    fun verifiedBuEmailIsAllowed() {
        assertTrue(isAllowedBuEmail("terrier@bu.edu", emailVerified = true))
        assertTrue(isAllowedBuEmail("Terrier@BU.EDU", emailVerified = true))
    }

    @Test
    fun unverifiedBuEmailIsRejected() {
        assertFalse(isAllowedBuEmail("terrier@bu.edu", emailVerified = false))
    }

    @Test
    fun otherDomainsAreRejected() {
        assertFalse(isAllowedBuEmail("terrier@gmail.com", emailVerified = true))
        assertFalse(isAllowedBuEmail("terrier@notbu.edu", emailVerified = true))
        assertFalse(isAllowedBuEmail("terrier@bu.edu.evil.com", emailVerified = true))
        assertFalse(isAllowedBuEmail(null, emailVerified = true))
    }
}
