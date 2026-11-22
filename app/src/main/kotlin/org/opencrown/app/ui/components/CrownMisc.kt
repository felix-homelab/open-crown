package org.opencrown.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.CrownTheme

/** A quiet search field that matches the grouped-list language. */
@Composable
fun CrownSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val colors = CrownTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CrownTheme.shapes.control)
            .background(colors.fill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrownIcon(
            CrownGlyph.MAGNIFY,
            tint = colors.secondaryLabel,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = CrownTheme.type.body,
                    color = colors.tertiaryLabel,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = CrownTheme.type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.tint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** An explanatory note with a tone-coloured edge and no fake action. */
@Composable
fun CrownNote(
    title: String? = null,
    message: String,
    tone: CrownTone = CrownTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val colors = CrownTheme.colors
    val accent = when (tone) {
        CrownTone.NEUTRAL -> colors.tertiaryLabel
        CrownTone.INFO -> colors.tint
        CrownTone.SUCCESS -> colors.success
        CrownTone.WARNING -> colors.warning
        CrownTone.CRITICAL -> colors.destructive
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.card)
            .background(colors.cardBackground)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (title != null) 38.dp else 22.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(text = title, style = CrownTheme.type.headline, color = colors.label)
                Spacer(Modifier.height(3.dp))
            }
            Text(text = message, style = CrownTheme.type.footnote, color = colors.secondaryLabel)
        }
    }
}

/** A large, quiet loading/unknown placeholder for a screen body. */
@Composable
fun CrownLoadingRows(count: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) {
            CrownSkeleton(height = 52.dp, shape = CrownTheme.shapes.card)
        }
    }
}
