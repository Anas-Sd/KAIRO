package com.kairo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.Task
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh

@Composable
fun DeleteTaskConfirmDialog(
    task: Task,
    hasSubtasks: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (deleteSubtasks: Boolean) -> Unit
) {
    if (hasSubtasks) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Delete '${task.title}'?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This task has subtasks. What would you like to do?",
                    fontSize = 14.sp,
                    color = Color(0xFFC8C4D9)
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onConfirmDelete(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = KairoError),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete it with all its subtasks", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onConfirmDelete(false) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KairoOutlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete only this task", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF918EA2))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Delete Task?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${task.title}'?",
                    fontSize = 14.sp,
                    color = Color(0xFFC8C4D9)
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmDelete(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = KairoError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF918EA2))
                }
            }
        )
    }
}
