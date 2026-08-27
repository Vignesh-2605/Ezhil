package com.ezhil.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAuthTest {

    @Test
    fun `dobToPin handles ISO format`() {
        assertEquals("0512", dobToPin("2016-05-12"))
        assertEquals("1122", dobToPin("2015-11-22"))
    }

    @Test
    fun `dobToPin handles DD-slash-MM-slash-YYYY format`() {
        assertEquals("0512", dobToPin("12/05/2016"))
    }

    @Test
    fun `dobToPin finds date embedded in longer text`() {
        assertEquals("0105", dobToPin("born 2016-01-05 in Madurai"))
    }

    @Test
    fun `dobToPin returns null for absent or unparseable dob`() {
        assertNull(dobToPin(null))
        assertNull(dobToPin(""))
        assertNull(dobToPin("unknown"))
    }

    @Test
    fun `hashPin matches the server's SHA-256 hex convention`() {
        // Must equal python: hashlib.sha256(b"1234").hexdigest() — the backend
        // verifies against this exact digest.
        assertEquals(
            "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
            hashPin("1234")
        )
    }

    @Test
    fun `hashPin is deterministic and pin-sensitive`() {
        assertEquals(hashPin("0512"), hashPin("0512"))
        assert(hashPin("0512") != hashPin("0513"))
    }
}
