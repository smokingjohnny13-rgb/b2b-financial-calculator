package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.OcrExtractedDealData
import com.example.ui.i18n.tr
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExecutiveBorder
import com.example.ui.theme.ExecutiveMidnight
import com.example.ui.theme.ExecutiveSurface
import com.example.ui.viewmodel.BigDealViewModel

@Composable
fun CommercialOcrDialog(
    viewModel: BigDealViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isScanning by viewModel.isOcrScanning.collectAsState()
    val scanResult by viewModel.ocrScanResult.collectAsState()
    val ocrError by viewModel.ocrError.collectAsState()

    var selectedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 1. Android Photo Picker (zero permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    selectedPreviewBitmap = bmp
                    viewModel.scanDocumentBitmap(bmp)
                }
            }.onFailure { e ->
                Toast.makeText(context, "Error opening photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Camera Snapshot Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedPreviewBitmap = bitmap
            viewModel.scanDocumentBitmap(bitmap)
        }
    }

    // 3. Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(
                context,
                "Camera permission needed to snap contract photos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isScanning) onDismiss()
        },
        containerColor = ExecutiveMidnight,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialog_commercial_ocr"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tr("Scan-to-Waterfall OCR", "OCR-сканер документов"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tr("Multimodal Gemini 3.8 Flash Parser", "Мультимодальный разбор условий Gemini"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isScanning) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = tr(
                        "Snap or select a photo of a supermarket agreement, commercial specification, price list, or protocol of disagreements. AI extracts RSP, discounts, retro rebates, and deferrals directly into your active Deal Desk.",
                        "Сфотографируйте или выберите скан спецификации, протокола разногласий или прайс-листа сети. AI извлечет РРЦ, наценку сети, скидки, ретро-бонусы и отсрочку в Ценовой водопад."
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons to pick or snap
                if (scanResult == null && !isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_ocr_camera")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Camera", "Камера"), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_ocr_gallery")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Gallery", "Галерея / Скан"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Scanning Progress Indicator
                AnimatedVisibility(visible = isScanning, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        color = ExecutiveSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = ElectricCyan,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = tr("Analyzing document with Gemini 3.8 Flash...", "Распознавание условий через Gemini 3.8 Flash..."),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr("Deconstructing price waterfall & terms", "Выделение РРЦ, скидок в накладной и ретро-бонусов"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error Message
                if (ocrError != null && !isScanning) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = ocrError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Extracted Results Card
                if (scanResult != null && !isScanning) {
                    val result = scanResult!!
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = ExecutiveSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("EXTRACTED CONDITIONS", "РАСПОЗНАННЫЕ УСЛОВИЯ"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = result.documentType,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (result.clientName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${tr("Account", "Клиент")}: ${result.clientName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = ExecutiveBorder
                            )

                            // Summary Findings Callout
                            if (result.summaryFindings.isNotBlank()) {
                                Text(
                                    text = result.summaryFindings,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Metric Grid
                            OcrMetricRow(label = tr("Shelf Price (RSP)", "Полочная цена (РРЦ)"), value = result.shelfPriceRsp?.let { "$it ${result.currencyCode}" } ?: "-")
                            OcrMetricRow(label = tr("Retailer Margin", "Маржа сети"), value = result.retailerMarginPct?.let { "$it%" } ?: "-")
                            OcrMetricRow(label = tr("On-Invoice Discount", "Скидка в накладной"), value = result.frontDiscountPct?.let { "$it%" } ?: "-")
                            OcrMetricRow(label = tr("Off-Invoice Retro Bonus", "Ретро-бонус / Премия"), value = result.backBonusPct?.let { "$it%" } ?: "-")
                            OcrMetricRow(label = tr("Logistics Cost", "Логистика / доставка"), value = result.logisticsCostPerUnit?.let { "$it ${result.currencyCode}" } ?: "-")
                            OcrMetricRow(label = tr("Supplier COGS", "Входная себестоимость"), value = result.supplierCogs?.let { "$it ${result.currencyCode}" } ?: "-")
                            OcrMetricRow(label = tr("Payment Deferral", "Отсрочка платежа"), value = result.paymentTermsDays?.let { "$it days" } ?: "-")
                            OcrMetricRow(label = tr("Contract Volume", "Объем поставки"), value = result.contractVolumeUnits?.let { "$it units" } ?: "-")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.applyOcrExtractedData(result)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_apply_ocr_terms")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Apply to Deal Desk", "Применить к сделке"),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.ocrScanResult.value = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Rescan", "Заново"), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun OcrMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (value != "-") ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
