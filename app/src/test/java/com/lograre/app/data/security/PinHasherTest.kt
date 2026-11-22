package com.lograre.app.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher(iterations = 10_000)

    @Test
    fun `hash verifies matching pin and rejects wrong pin`() {
        val record = hasher.hash("482910")

        assertTrue(hasher.verify("482910", record))
        assertFalse(hasher.verify("482911", record))
        assertFalse(hasher.verify("4829", record))
    }

    @Test
    fun `same pin produces different salted hashes`() {
        val first = hasher.hash("1234")
        val second = hasher.hash("1234")

        assertNotEquals(first, second)
        assertFalse(first.contains("1234"))
        assertTrue(hasher.verify("1234", first))
        assertTrue(hasher.verify("1234", second))
    }

    @Test
    fun `corrupted records return false safely`() {
        assertFalse(hasher.verify("1234", ""))
        assertFalse(hasher.verify("1234", "v1:bad:salt:hash"))
        assertFalse(hasher.verify("1234", "v2:10000:AAAA:BBBB"))
        assertFalse(hasher.verify("1234", "v1:10000:!!!:@@@"))
    }
}
