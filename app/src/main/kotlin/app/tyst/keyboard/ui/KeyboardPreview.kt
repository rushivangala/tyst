package app.tyst.keyboard.ui

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tyst.core.field.EnterAction
import app.tyst.core.icons.Icons
import app.tyst.core.layout.Layouts
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.theme.KeyboardColors
import app.tyst.keyboard.ime.KeyboardView

/** A live, non-interactive copy of the keyboard that follows the settings as they change. */
@Composable
fun KeyboardPreview(settings: KeyboardSettings, colors: KeyboardColors, modifier: Modifier = Modifier) {
    val n = colors.n
    Column(
        modifier.fillMaxWidth().background(Color(n.plate))
            .semantics { contentDescription = "Keyboard preview" },
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(Color(n.strip)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TIcon(Icons.SPARKLE, Color(n.textSecondary), size = 22.dp)
            Spacer(Modifier.width(12.dp))
            listOf("Väst" to false, "Västerås" to true, "Växjö" to false).forEach { (w, primary) ->
                Text(
                    w, Modifier.weight(1f), color = Color(n.textPrimary), fontSize = 16.sp, textAlign = TextAlign.Center,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                )
            }
            Spacer(Modifier.width(34.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                KeyboardView(ctx).apply {
                    interactive = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    enterAction = EnterAction.SEND
                }
            },
            update = { v ->
                v.colors = colors
                v.layout = Layouts.letters(settings.numberRow, settings.swedishRow)
                v.keyHeightDp = settings.keyHeightDp
                v.keyBorders = settings.keyBorders
                v.spaceLabel = if (settings.swedishWords) "EN · SV" else "EN"
            },
        )
    }
}
