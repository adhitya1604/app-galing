package com.example.ui.screens.courier

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.OcrResult
import com.example.ocr.OcrExtractor
import com.example.ui.theme.DuoCyanScan
import com.example.ui.theme.DuoNavyDark
import com.example.ui.theme.DuoOrangeAccent
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConfirmation: (ocrResult: OcrResult, imageUri: String?) -> Unit,
    onManualInput: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var isTorchOn by remember { mutableStateOf(false) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var ocrStatusMessage by remember { mutableStateOf<String?>(null) }
    var ocrStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<String?>(null) }

    // Automatic scanner settings
    var isAutoScanActive by remember { mutableStateOf(true) }
    var hasAutoTriggered by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isProcessingOcr = true
            ocrStatusMessage = "Membaca label paket..."
            ocrStatusSuccess = null
            scope.launch {
                val result = OcrExtractor.processImage(context, selectedUri)
                delay(400)
                isProcessingOcr = false
                if (result.isRecognized) {
                    ocrStatusMessage = "Data paket berhasil dibaca."
                    ocrStatusSuccess = true
                    delay(500)
                    onNavigateToConfirmation(result, selectedUri.toString())
                } else {
                    ocrStatusMessage = "Label paket belum terbaca dengan jelas."
                    ocrStatusSuccess = false
                    lastCapturedUri = selectedUri.toString()
                }
            }
        }
    }

    // Helper to process preset test label
    fun processPreset(preset: OcrExtractor.PresetLabel) {
        isProcessingOcr = true
        ocrStatusMessage = "Membaca label paket..."
        ocrStatusSuccess = null

        scope.launch {
            // Save sample label asset to temp file
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.sample_shipping_label)
            val file = File(context.cacheDir, "sample_scan_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
            fos.close()

            delay(600) // Brief animation delay for OCR UX
            val ocrRes = OcrResult(
                rawText = "RESI: ${preset.trackingNumber}\nPENERIMA: ${preset.recipientName}\nALAMAT: ${preset.address}\nEKSPEDISI: ${preset.courierCompany}",
                trackingNumber = preset.trackingNumber,
                recipientName = preset.recipientName,
                address = preset.address,
                courierCompany = preset.courierCompany,
                confidence = 0.98f,
                isRecognized = true
            )
            isProcessingOcr = false
            ocrStatusMessage = "Data paket berhasil dibaca."
            ocrStatusSuccess = true
            delay(400)
            onNavigateToConfirmation(ocrRes, Uri.fromFile(file).toString())
        }
    }

    // Check camera permission
    if (!cameraPermissionState.status.isGranted) {
        CameraPermissionFallback(
            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onUsePreset = { showPresetDialog = true },
            onManualInput = onManualInput,
            onNavigateBack = onNavigateBack
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_scan_screen")
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val lifecycleOwner = LocalLifecycleOwner.current

        // CameraX Preview View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        var lastAnalysisTime = 0L
                        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            if (!isAutoScanActive || isProcessingOcr || hasAutoTriggered || (currentTime - lastAnalysisTime < 600)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            lastAnalysisTime = currentTime

                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                textRecognizer.process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        val ocrResult = OcrExtractor.parseExtractedText(visionText.text)
                                        if (ocrResult.isRecognized && ocrResult.trackingNumber.isNotBlank() && !hasAutoTriggered) {
                                            hasAutoTriggered = true
                                            val bitmap = imageProxy.toBitmap()
                                            val photoFile = File(context.cacheDir, "autoscan_${System.currentTimeMillis()}.jpg")
                                            try {
                                                val fos = FileOutputStream(photoFile)
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                                                fos.flush()
                                                fos.close()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            val savedUri = Uri.fromFile(photoFile)

                                            scope.launch {
                                                val courierInfo = if (ocrResult.courierCompany.isNotBlank()) " (${ocrResult.courierCompany})" else ""
                                                ocrStatusMessage = "Resi otomatis terbaca: ${ocrResult.trackingNumber}$courierInfo"
                                                ocrStatusSuccess = true
                                                delay(450)
                                                onNavigateToConfirmation(ocrResult, savedUri.toString())
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture,
                            imageAnalysis
                        )
                        camera.cameraControl.enableTorch(isTorchOn)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Viewfinder Scanner Overlay (Custom Canvas)
        val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
        val laserPosition by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laser_y"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val frameW = canvasW * 0.84f
            val frameH = canvasH * 0.42f
            val frameLeft = (canvasW - frameW) / 2f
            val frameTop = (canvasH - frameH) / 2.3f

            // Dark semi-transparent scrim around viewfinder
            drawRect(
                color = Color.Black.copy(alpha = 0.65f),
                size = size
            )

            // Clear center frame
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Viewfinder Border with Corner Accents
            drawRoundRect(
                color = DuoCyanScan.copy(alpha = 0.5f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Animated Laser Line
            val laserY = frameTop + (frameH * laserPosition)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        DuoCyanScan,
                        DuoOrangeAccent,
                        Color.Transparent
                    )
                ),
                start = Offset(frameLeft + 16.dp.toPx(), laserY),
                end = Offset(frameLeft + frameW - 16.dp.toPx(), laserY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Top Navigation Bar and Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "SCANNER OCR RESI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = { isTorchOn = !isTorchOn },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Senter",
                        tint = if (isTorchOn) DuoOrangeAccent else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Viewfinder Instruction Pill & Auto-Scan Mode Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isAutoScanActive) StatusSuccess.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAutoScanActive) StatusSuccess else Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { isAutoScanActive = !isAutoScanActive }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isAutoScanActive) Color.White else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAutoScanActive) "⚡ AUTO-SCAN RESI: AKTIF" else "AUTO-SCAN: MANUAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isAutoScanActive) StatusSuccess.copy(alpha = 0.5f) else DuoOrangeAccent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (isAutoScanActive)
                        "Arahkan kamera ke label resi — otomatis terbaca tanpa perlu tekan tombol"
                    else
                        "Arahkan bingkai ke label lalu tekan tombol foto di bawah",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.4.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // OCR UX Feedback Banner
        if (isProcessingOcr || ocrStatusMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.testTag("ocr_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isProcessingOcr) {
                            CircularProgressIndicator(
                                color = DuoOrangeAccent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Membaca Label Resi...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mengekstrak nomor resi, penerima, dan alamat pengiriman",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else if (ocrStatusSuccess == true) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Data Paket Berhasil Dibaca",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else if (ocrStatusSuccess == false) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Label Belum Terbaca Jelas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        ocrStatusMessage = null
                                        ocrStatusSuccess = null
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Foto Ulang", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = onManualInput,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Input Manual", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Shutter button, Gallery picker, Demo sample preset
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Sample Preset trigger for easy emulator testing
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DuoOrangeAccent),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showPresetDialog = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("preset_label_selector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = DuoOrangeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONTOH LABEL RESI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = DuoOrangeAccent
                    )
                }
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .testTag("open_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Pilih dari Galeri",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shutter Camera Capture Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(DuoOrangeAccent)
                        .clickable {
                            val capture = imageCapture
                            if (capture != null) {
                                val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                isProcessingOcr = true
                                ocrStatusMessage = "Membaca label paket..."
                                ocrStatusSuccess = null

                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val savedUri = Uri.fromFile(photoFile)
                                            scope.launch {
                                                val ocrResult = OcrExtractor.processImage(context, savedUri)
                                                delay(400)
                                                isProcessingOcr = false
                                                if (ocrResult.isRecognized) {
                                                    ocrStatusMessage = "Data paket berhasil dibaca."
                                                    ocrStatusSuccess = true
                                                    delay(500)
                                                    onNavigateToConfirmation(ocrResult, savedUri.toString())
                                                } else {
                                                    ocrStatusMessage = "Label paket belum terbaca dengan jelas."
                                                    ocrStatusSuccess = false
                                                    lastCapturedUri = savedUri.toString()
                                                }
                                            }
                                        }

                                        override fun onError(exc: ImageCaptureException) {
                                            isProcessingOcr = false
                                            ocrStatusMessage = "Gagal mengambil foto. Silakan coba lagi."
                                            ocrStatusSuccess = false
                                        }
                                    }
                                )
                            } else {
                                // Fallback: If camera hardware capture not initialized (e.g. basic emulator), process preset
                                processPreset(OcrExtractor.PRESET_LABELS[0])
                            }
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Ambil Foto",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Manual Input Button
                IconButton(
                    onClick = onManualInput,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .testTag("manual_input_shortcut_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Input Manual",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Preset Label Selector Dialog for Immediate Testing in Emulator
    if (showPresetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = {
                Text(
                    text = "Label Resi Sampel",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pilih label pengiriman ekspedisi untuk disimulasikan ke sistem OCR Duo Galing:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OcrExtractor.PRESET_LABELS.forEach { preset ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPresetDialog = false
                                    processPreset(preset)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = DuoOrangeAccent
                                )
                                Text(
                                    text = "Resi: ${preset.trackingNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Penerima: ${preset.recipientName} (${preset.address.take(30)}...)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            confirmButton = {
                OutlinedButton(
                    onClick = { showPresetDialog = false },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun CameraPermissionFallback(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onUsePreset: () -> Unit,
    onManualInput: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DuoOrangeAccent.copy(alpha = 0.12f))
                .border(1.dp, DuoOrangeAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = DuoOrangeAccent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Izin Kamera Diperlukan",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Duo Galing membutuhkan akses kamera untuk melakukan scan label paket dan membaca informasi secara otomatis menggunakan OCR.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Izinkan Akses Kamera", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text("Buka Pengaturan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Alternative options: Test with sample preset label or manual input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onUsePreset,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Label Sampel", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onManualInput,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Input Manual", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        androidx.compose.material3.TextButton(onClick = onNavigateBack) {
            Text("Kembali ke Beranda", color = MaterialTheme.colorScheme.outline)
        }
    }
}
