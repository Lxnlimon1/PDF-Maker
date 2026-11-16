package com.example.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ConversionToolType
import com.example.data.model.ToolCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onSelectTool: (ConversionToolType) -> Unit,
    contentBottomPadding: PaddingValues
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text("Conversion & PDF Tools", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "13 native tools • 100% on-device processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentBottomPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ToolCategory.entries.forEach { category ->
                val tools = ConversionToolType.entries.filter { it.category == category }
                item(key = category.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        tools.forEach { tool ->
                            ToolRowCard(
                                tool = tool,
                                onClick = { onSelectTool(tool) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRowCard(
    tool: ConversionToolType,
    onClick: () -> Unit
) {
    val (icon, tint, container) = toolStyle(tool)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${tool.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun toolStyle(tool: ConversionToolType): Triple<ImageVector, Color, Color> {
    return when (tool) {
        ConversionToolType.IMAGE_TO_PDF -> Triple(
            Icons.Default.Image,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
        ConversionToolType.WORD_TO_PDF, ConversionToolType.PDF_TO_WORD -> Triple(
            Icons.Default.Description,
            Color(0xFF1D4ED8),
            Color(0xFFDBEAFE)
        )
        ConversionToolType.EXCEL_TO_PDF, ConversionToolType.PDF_TO_EXCEL -> Triple(
            Icons.Default.TableChart,
            Color(0xFF0E8A6E),
            Color(0xFFD7F5EC)
        )
        ConversionToolType.PPT_TO_PDF, ConversionToolType.PDF_TO_PPT -> Triple(
            Icons.Default.Slideshow,
            Color(0xFFC2410C),
            Color(0xFFFFEDD5)
        )
        ConversionToolType.TEXT_TO_PDF -> Triple(
            Icons.Default.TextSnippet,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        ConversionToolType.HTML_TO_PDF -> Triple(
            Icons.Default.Code,
            Color(0xFF7C3AED),
            Color(0xFFEDE9FE)
        )
        ConversionToolType.PDF_TO_IMAGES -> Triple(
            Icons.Default.PhotoLibrary,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        ConversionToolType.COMPRESS_PDF -> Triple(
            Icons.Default.Compress,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
        ConversionToolType.MERGE_PDF -> Triple(
            Icons.Default.MergeType,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        ConversionToolType.SPLIT_PDF -> Triple(
            Icons.Default.ContentCut,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    }
}
