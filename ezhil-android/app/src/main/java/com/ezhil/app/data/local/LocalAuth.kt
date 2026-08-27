package com.ezhil.app.data.local

import java.security.MessageDigest

fun hashPin(pin: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

/** Derives the default 4-digit PIN from a DOB string (any format containing YYYY-MM-DD or DD/MM/YYYY).
 *  Returns MMDD, e.g. "2015-05-12" → "0512", or null if DOB is absent/unreadable. */
fun dobToPin(dob: String?): String? {
    if (dob.isNullOrBlank()) return null
    // ISO format: 2015-05-12
    val iso = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(dob)
    if (iso != null) return "${iso.groupValues[2]}${iso.groupValues[3]}"
    // DD/MM/YYYY format: 12/05/2015
    val dmy = Regex("""(\d{2})/(\d{2})/(\d{4})""").find(dob)
    if (dmy != null) return "${dmy.groupValues[2]}${dmy.groupValues[1]}"
    return null
}
