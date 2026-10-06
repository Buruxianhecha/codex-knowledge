package com.cleo.cleos.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cleo.cleos.data.AvatarPreset
import com.cleo.cleos.data.AvatarPresets
import com.cleo.cleos.glass.LocalGlassPalette

@Composable
internal fun AvatarPresetPicker(currentFile: String?, onDismiss: () -> Unit, onPick: (AvatarPreset) -> Unit) {
    val palette = LocalGlassPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择头像") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AvatarPresets.all, key = { it.number }) { preset ->
                    val selected = preset.isSelected(currentFile)
                    Column(
                        modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = { onPick(preset) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AsyncImage(
                            model = preset.assetUri,
                            contentDescription = "预设头像 ${preset.number}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                .then(if (selected) Modifier.border(3.dp, palette.accent, CircleShape) else Modifier),
                        )
                        Text(
                            if (selected) "已选" else "头像 ${preset.number}",
                            color = if (selected) palette.accent else palette.contentSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
