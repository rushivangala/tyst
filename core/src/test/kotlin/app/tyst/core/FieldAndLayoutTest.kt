package app.tyst.core

import app.tyst.core.field.Android
import app.tyst.core.field.EnterAction
import app.tyst.core.field.FieldClassifier
import app.tyst.core.field.FieldKind
import app.tyst.core.field.KeyLayoutKind
import app.tyst.core.layout.KeyGeometry
import app.tyst.core.layout.KeyType
import app.tyst.core.layout.Layouts
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FieldAndLayoutTest {
    @Test fun classifiesFields() {
        val email = FieldClassifier.classify(Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, Android.IME_ACTION_NEXT)
        assertThat(email.kind).isEqualTo(FieldKind.EMAIL)
        assertThat(email.autocorrect).isFalse()
        assertThat(email.enterAction).isEqualTo(EnterAction.NEXT)

        val pin = FieldClassifier.classify(Android.TYPE_CLASS_NUMBER or Android.TYPE_NUMBER_VARIATION_PASSWORD, 0)
        assertThat(pin.kind).isEqualTo(FieldKind.CODE)
        assertThat(pin.layout).isEqualTo(KeyLayoutKind.NUMBER_PAD)
        assertThat(pin.incognito).isTrue()

        val phone = FieldClassifier.classify(Android.TYPE_CLASS_PHONE, 0)
        assertThat(phone.layout).isEqualTo(KeyLayoutKind.PHONE_PAD)

        val search = FieldClassifier.classify(Android.TYPE_CLASS_TEXT, Android.IME_ACTION_SEARCH)
        assertThat(search.kind).isEqualTo(FieldKind.SEARCH)
        assertThat(search.enterAction).isEqualTo(EnterAction.SEARCH)

        val multi = FieldClassifier.classify(Android.TYPE_CLASS_TEXT or Android.TYPE_TEXT_FLAG_MULTI_LINE, Android.IME_ACTION_SEND)
        assertThat(multi.enterAction).isEqualTo(EnterAction.NEWLINE)

        val otpText = FieldClassifier.classify(Android.TYPE_CLASS_TEXT, Android.IME_ACTION_DONE, hint = "Enter the 6-digit code")
        assertThat(otpText.kind).isEqualTo(FieldKind.CODE)

        val nul = FieldClassifier.classify(Android.TYPE_NULL, 0)
        assertThat(nul.predictions).isFalse()
    }

    @Test fun browsersDontCaptureMemory() {
        val f = FieldClassifier.classify(Android.TYPE_CLASS_TEXT, 0, "com.android.chrome")
        assertThat(f.memoryAllowed).isFalse()
        assertThat(f.incognito).isFalse()
    }

    @Test fun lettersLayoutHasLongPressSwedishLetters() {
        val l = Layouts.letters(numberRow = false, swedishRow = false)
        val a = l.find { it.text == "a" }!!
        assertThat(a.popup.take(2)).containsExactly("å", "ä").inOrder()
        assertThat(l.find { it.text == "o" }!!.popup).contains("ö")
        assertThat(l.find { it.text == "q" }!!.popup.first()).isEqualTo("1")
        assertThat(l.rows).hasSize(4)
        assertThat(Layouts.letters(numberRow = true, swedishRow = true).rows).hasSize(6)
    }

    @Test fun geometryMatchesDesignSpec() {
        // 412dp wide at 1px = 1dp: 4dp side padding, 52dp keys, 6/10dp gaps.
        val g = KeyGeometry(Layouts.letters(false, false), 412f, 52f, 6f, 10f, 4f, 8f, 4f)
        val q = g.keys.first { it.key.text == "q" }
        assertThat(q.slot.width).isWithin(0.01f).of(40.4f)
        assertThat(q.visual.width).isWithin(0.01f).of(34.4f)
        assertThat(q.visual.height).isEqualTo(52f)
        assertThat(g.height).isEqualTo(8f + 4 * 62f + 4f)
        // Every touch lands on a key, including the spacer next to "a".
        assertThat(g.keyAt(5f, 8f + 62f + 30f)!!.key.text).isEqualTo("a")
        assertThat(g.keyAt(q.slot.centerX, q.slot.centerY)!!.key.text).isEqualTo("q")
        assertThat(g.keyAt(206f, 8f + 3 * 62f + 30f)!!.key.type).isEqualTo(KeyType.SPACE)
        assertThat(g.keyAt(-50f, -50f)!!.key.text).isEqualTo("q")
    }

    @Test fun swedishRowIsCentredAndQwertyDoesNotMove() {
        val plain = KeyGeometry(Layouts.letters(false, false), 412f, 52f, 6f, 10f, 4f, 8f, 4f)
        val sv = KeyGeometry(Layouts.letters(false, true), 412f, 52f, 6f, 10f, 4f, 8f, 4f)
        val aa = sv.keys.first { it.key.text == "ä" }
        assertThat(aa.slot.centerX).isWithin(0.5f).of(206f)
        assertThat(sv.keys.first { it.key.text == "q" }.slot.left).isEqualTo(plain.keys.first { it.key.text == "q" }.slot.left)
    }

    @Test fun numberPadKeysAreLarge() {
        val g = KeyGeometry(Layouts.numberPad, 412f, 52f, 6f, 10f, 4f, 8f, 4f)
        assertThat(g.keys.first { it.key.text == "5" }.slot.width).isGreaterThan(130f)
    }
}
