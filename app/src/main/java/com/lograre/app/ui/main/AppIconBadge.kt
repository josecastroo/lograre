package com.lograre.app.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lograre.app.data.apps.AppIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconBadge(
    packageName: String,
    appLabel: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            AppIconLoader.loadIconBitmap(context, packageName)
        }
    }

    val bitmap = iconBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = appLabel,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = appLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
