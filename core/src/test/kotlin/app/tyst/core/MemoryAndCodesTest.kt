package app.tyst.core

import app.tyst.core.codes.OtpDetector
import app.tyst.core.field.FieldInfo
import app.tyst.core.field.FieldKind
import app.tyst.core.memory.MemoryExtractor
import app.tyst.core.memory.MemoryMatcher
import app.tyst.core.memory.MemoryStore
import app.tyst.core.memory.MemoryType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MemoryAndCodesTest {
    @Test fun extractsTheFiveCopyExamples() {
        val found = MemoryExtractor.extract(
            "My address is Kungsgatan 4, 411 19 Göteborg. The booking ref is ABX42K for SAS. " +
                "My number is 070 123 45 67. Email me at elin.berg@hey.com thanks",
        )
        assertThat(found.map { it.type to it.value }).containsExactly(
            MemoryType.ADDRESS to "Kungsgatan 4, 411 19 Göteborg",
            MemoryType.BOOKING to "ABX42K",
            MemoryType.PHONE to "070 123 45 67",
            MemoryType.EMAIL to "elin.berg@hey.com",
        )
    }

    @Test fun neverKeepsCardNumbersOrCodes() {
        assertThat(MemoryExtractor.extract("my number is 4111 1111 1111 1111")).isEmpty()
        assertThat(MemoryExtractor.extract("booking code 482913")).isEmpty()
        assertThat(MemoryExtractor.extract("I live at home")).isEmpty()
        assertThat(MemoryExtractor.looksSecret("4111111111111111")).isTrue()
    }

    @Test fun ignoresOtherPeoplesEmail() {
        assertThat(MemoryExtractor.extract("Ask anna@example.com about it")).isEmpty()
    }

    @Test fun storeDedupesSearchesExpiresAndRoundTrips() {
        val s = MemoryStore()
        val day = MemoryStore.DAY_MS
        val a = s.add(MemoryType.ADDRESS, "Kungsgatan 4, Göteborg", "com.whatsapp", 0)
        s.add(MemoryType.ADDRESS, "kungsgatan 4 göteborg", "com.whatsapp", day)
        assertThat(s.size).isEqualTo(1)
        val b = s.add(MemoryType.BOOKING, "ABX42K", "com.google.android.gm", 2 * day)
        s.setPinned(b.id, true)
        assertThat(s.search("abx").map { it.id }).containsExactly(b.id)
        assertThat(s.search("booking").map { it.id }).containsExactly(b.id)

        val copy = MemoryStore.decode(s.encode())
        assertThat(copy.all).isEqualTo(s.all)

        assertThat(s.expire(200 * day, 90)).isEqualTo(1)
        assertThat(s.all.map { it.id }).containsExactly(b.id)
        val removed = s.remove(b.id)!!
        s.restore(removed)
        assertThat(s.get(b.id)).isEqualTo(removed)
        assertThat(s.add(MemoryType.EMAIL, "x@y.se", "", 0).id).isNotEqualTo(a.id)
    }

    @Test fun previewTruncatesForShoulderSurfers() {
        val s = MemoryStore()
        val item = s.add(MemoryType.ADDRESS, "Kungsgatan 4, 411 19 Göteborg", "", 0)
        assertThat(item.preview()).isEqualTo("Kungsgatan 4, 411…")
    }

    @Test fun matcherUsesTriggersAndFieldKinds() {
        val s = MemoryStore()
        s.add(MemoryType.ADDRESS, "Kungsgatan 4", "", 1)
        s.add(MemoryType.EMAIL, "elin.berg@hey.com", "", 2)
        val text = FieldInfo()
        assertThat(MemoryMatcher.match("ok so my address is ", text, s).map { it.value }).containsExactly("Kungsgatan 4")
        assertThat(MemoryMatcher.match("Email me at", text, s).map { it.value }).containsExactly("elin.berg@hey.com")
        assertThat(MemoryMatcher.match("Lunch at noon", text, s)).isEmpty()
        assertThat(MemoryMatcher.match("", FieldInfo(kind = FieldKind.EMAIL), s).single().type).isEqualTo(MemoryType.EMAIL)
        assertThat(MemoryMatcher.match("my address is ", FieldInfo(incognito = true), s)).isEmpty()
    }

    @Test fun otpDetection() {
        assertThat(OtpDetector.extract("482913")).isEqualTo("482913")
        assertThat(OtpDetector.extract(" 482 913 ")).isEqualTo("482913")
        assertThat(OtpDetector.extract("G-731004 is your Google verification code.")).isEqualTo("731004")
        assertThat(OtpDetector.extract("Din kod är 4821")).isEqualTo("4821")
        assertThat(OtpDetector.extract("Meet at 1530 near gate 12")).isNull()
        assertThat(OtpDetector.extract("Call +46701234567 code")).isNull()
        assertThat(OtpDetector.extract("Codes 1234 and 5678")).isNull()
        assertThat(OtpDetector.extract("123")).isNull()
        assertThat(OtpDetector.extract(null)).isNull()
        assertThat(OtpDetector.grouped("482913")).isEqualTo("482 913")
    }
}
