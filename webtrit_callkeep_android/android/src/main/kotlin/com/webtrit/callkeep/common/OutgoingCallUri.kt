package com.webtrit.callkeep.common

import android.net.Uri
import android.telecom.PhoneAccount

/**
 * Single owner of the decoy [Uri] passed to `TelecomManager.placeCall` for outgoing calls.
 *
 * The dialled number is carried separately in the call metadata and is never read back from
 * this Uri, so the user part only has to be a stable, valid placeholder. Some OEM Telecom
 * forks run a scheme-agnostic emergency-number check on that Uri and silently divert the call
 * to the system dialer when its user part looks like an emergency number - which breaks
 * legitimate PBX extensions that collide with a device emergency number (for example an
 * extension "112"). To avoid that, every digit is masked to a letter (0..9 -> a..j) and any
 * other character is preserved and percent-encoded; the resulting user part carries no digit
 * for such a check to match, while the real number keeps flowing unchanged in the metadata.
 *
 * Build the Uri only through [of]; never assemble it by hand, otherwise the digit masking can
 * be forgotten in one place and the call will fail on those devices. The dialled number is
 * read from the call metadata, never parsed back from this Uri - it is a one-way placeholder.
 */
object OutgoingCallUri {
    // RFC 9476 reserved .internal TLD, guaranteed to never resolve publicly. This host is
    // never used for actual dialing, only for Telecom bookkeeping.
    private const val HOST = "portadialer.internal"

    /** The Uri for placing an outgoing call to [number], with its digits masked to letters. */
    fun of(number: String): Uri =
        Uri.parse("${PhoneAccount.SCHEME_SIP}:${Uri.encode(maskDigits(number))}@$HOST")

    // Each decimal digit -> the letter at the same offset from a (0 -> a .. 9 -> j).
    // Every other character is left untouched, so the user part never carries a digit.
    private fun maskDigits(value: String): String =
        buildString {
            for (c in value) {
                val digit = Character.digit(c, 10)
                append(if (digit in 0..9) 'a' + digit else c)
            }
        }
}
