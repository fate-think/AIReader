package com.example.aireader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** One chevron for every expanded and collapsed control. */
@Composable
internal fun ExpandCollapseArrow(expanded: Boolean) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(24.dp).rotate(if (expanded) -90f else 90f),
        tint = MaterialTheme.colorScheme.onSurface
    )
}
