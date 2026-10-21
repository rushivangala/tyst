package app.tyst.keyboard.ime

import android.content.Context
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/**
 * Android's inline autofill. The autofill service (NordPass or any other) supplies the pills and the
 * system draws them; Tyst only places them in the strip and never sees what they contain.
 */
object InlineAutofill {
    private const val MAX_SUGGESTIONS = 4

    fun request(context: Context): InlineSuggestionsRequest {
        val styles = UiVersions.newStylesBuilder()
            .addStyle(InlineSuggestionUi.newStyleBuilder().build())
            .build()
        val min = Size(context.dp(48f).toInt(), context.dp(32f).toInt())
        val max = Size(context.dp(320f).toInt(), context.dp(40f).toInt())
        val spec = InlinePresentationSpec.Builder(min, max).setStyle(styles).build()
        return InlineSuggestionsRequest.Builder(List(MAX_SUGGESTIONS) { spec })
            .setMaxSuggestionCount(MAX_SUGGESTIONS)
            .build()
    }

    /** Inflates every pill, then hands them to [done] on the main thread in the order received. */
    fun inflate(context: Context, response: InlineSuggestionsResponse, done: (List<View>) -> Unit) {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            done(emptyList())
            return
        }
        val views = arrayOfNulls<View>(suggestions.size)
        var pending = suggestions.size
        val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        suggestions.forEachIndexed { i, s ->
            s.inflate(context, size, context.mainExecutor) { view ->
                views[i] = view
                pending--
                if (pending == 0) done(views.filterNotNull())
            }
        }
    }
}
