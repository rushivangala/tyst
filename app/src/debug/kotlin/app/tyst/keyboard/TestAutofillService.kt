package app.tyst.keyboard

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/**
 * A stand-in password manager for end-to-end tests (debug builds only). It answers every fill
 * request with two logins, offered as inline suggestions the way NordPass does.
 */
@SuppressLint("RestrictedApi")
@Suppress("DEPRECATION")
class TestAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val ids = ArrayList<AutofillId>()
        fun walk(node: AssistStructure.ViewNode) {
            val editable = node.autofillHints?.isNotEmpty() == true || node.className?.endsWith("EditText") == true
            if (editable) node.autofillId?.let { ids += it }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode)
        lastRequestHadInline = request.inlineSuggestionsRequest != null
        requests++
        if (ids.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        val inline = request.inlineSuggestionsRequest
        val attribution = PendingIntent.getService(this, 0, Intent(this, TestAutofillService::class.java), PendingIntent.FLAG_IMMUTABLE)
        val response = FillResponse.Builder()
        for (k in 0 until 2) {
            val label = "test-user-$k"
            val dataset = Dataset.Builder()
            for (id in ids) {
                val menu = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply { setTextViewText(android.R.id.text1, label) }
                val spec = inline?.inlinePresentationSpecs?.let { it[minOf(k, it.size - 1)] }
                val pill = spec?.takeIf { UiVersions.getVersions(it.style).contains(UiVersions.INLINE_UI_VERSION_1) }?.let {
                    InlinePresentation(InlineSuggestionUi.newContentBuilder(attribution).setTitle(label).build().slice, it, false)
                }
                if (pill != null) dataset.setValue(id, AutofillValue.forText(label), menu, pill)
                else dataset.setValue(id, AutofillValue.forText(label), menu)
            }
            response.addDataset(dataset.build())
        }
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()

    companion object {
        @Volatile var requests = 0
        @Volatile var lastRequestHadInline = false
    }
}
