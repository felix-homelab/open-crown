package org.opencrown.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.theme.CrownType
import org.opencrown.app.ui.theme.LocalCrownColors
import org.opencrown.app.ui.theme.LocalCrownSpacing

/**
 * The standard screen shell: a large title that scrolls away, a compact bar
 * that fades in, safe-area handling, and an optional bottom action area.
 */
@Composable
fun CrownScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalCrownColors.current
    val spacing = LocalCrownSpacing.current
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.groupedBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.screenInset,
                end = spacing.screenInset,
                bottom = spacing.xxl + 72.dp,
            ),
        ) {
            item(key = "large-title") {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions()
                }
                Text(
                    text = title,
                    style = CrownType.largeTitle,
                    color = colors.label,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(spacing.xs))
                    Text(text = subtitle, style = CrownType.subheadline, color = colors.secondaryLabel)
                }
                Spacer(Modifier.height(spacing.lg))
            }
            content()
            item(key = "bottom-inset") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        Box(Modifier.align(Alignment.TopCenter)) {
            CrownCompactBar(
                title = title,
                onBack = onBack,
                collapsed = collapsed,
                actions = actions,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            bottomBar()
        }
    }
}

@Composable
private fun CrownCompactBar(
    title: String,
    onBack: (() -> Unit)?,
    collapsed: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = LocalCrownColors.current
    AnimatedVisibility(
        visible = collapsed,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.barBackground.copy(alpha = 0.97f))
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onBack != null) {
                    CrownBackButton(onBack)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = CrownType.headline,
                    color = colors.label,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator)
            )
        }
    }
}

@Composable
fun CrownBackButton(onBack: () -> Unit) {
    val colors = LocalCrownColors.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(10.dp),
    ) {
        CrownIcon(CrownGlyph.CHEVRON_LEFT, tint = colors.tint, contentDescription = "Back")
    }
}

/** A fixed bottom action area with a material background and safe inset. */
@Composable
fun CrownBottomAction(content: @Composable () -> Unit) {
    val colors = LocalCrownColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.groupedBackground.copy(alpha = 0.97f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        content()
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}