package com.kairo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.TaskSort
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    initialSort: TaskSort,
    onApplySort: (TaskSort) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var selectedSort by remember { mutableStateOf(initialSort) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KairoSurfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(KairoOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header Row: Title & Reset Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort Tasks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Reset",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = KairoPrimary,
                    modifier = Modifier
                        .clickable {
                            selectedSort = TaskSort.DATE_NEAR
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. DATE SECTION
            SectionTitle(title = "DATE")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SortChip(
                    label = "Near (Earliest)",
                    isSelected = selectedSort == TaskSort.DATE_NEAR,
                    onClick = { selectedSort = TaskSort.DATE_NEAR }
                )
                SortChip(
                    label = "Late (Latest)",
                    isSelected = selectedSort == TaskSort.DATE_LATE,
                    onClick = { selectedSort = TaskSort.DATE_LATE }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. PRIORITY SECTION
            SectionTitle(title = "PRIORITY")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SortChip(
                    label = "Highest First",
                    isSelected = selectedSort == TaskSort.PRIORITY_HIGH,
                    onClick = { selectedSort = TaskSort.PRIORITY_HIGH }
                )
                SortChip(
                    label = "Lowest First",
                    isSelected = selectedSort == TaskSort.PRIORITY_LOW,
                    onClick = { selectedSort = TaskSort.PRIORITY_LOW }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. ALPHABETICAL SECTION
            SectionTitle(title = "NAME")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SortChip(
                    label = "A to Z",
                    isSelected = selectedSort == TaskSort.TITLE_AZ,
                    onClick = { selectedSort = TaskSort.TITLE_AZ }
                )
                SortChip(
                    label = "Z to A",
                    isSelected = selectedSort == TaskSort.TITLE_ZA,
                    onClick = { selectedSort = TaskSort.TITLE_ZA }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // 4. APPLY SORTING BUTTON
            Button(
                onClick = {
                    onApplySort(selectedSort)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KairoPrimaryContainer,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Apply Sorting",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = Color(0xFF918EA2)
    )
}

@Composable
private fun SortChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) KairoPrimaryContainer else KairoSurfaceContainerHighest
    val textColor = if (isSelected) Color.White else Color(0xFFC8C4D9)
    val borderColor = if (isSelected) KairoPrimary else KairoOutlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
