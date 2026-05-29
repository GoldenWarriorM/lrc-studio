package com.lrcstudio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.model.LrcLine

@Composable
fun LrcLineItem(
    line: LrcLine,
    isCurrent: Boolean,
    isEditing: Boolean,
    editingText: String,
    onTextClick: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isCurrent)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = bgColor,
        tonalElevation = if (isCurrent) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.timestampFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = onEditChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onEditDone) {
                    Text("Done", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text(
                    text = line.text.ifEmpty { "Tap to add text" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.text.isEmpty())
                        MaterialTheme.colorScheme.outline
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTextClick() }
                )
            }
        }
    }
}
