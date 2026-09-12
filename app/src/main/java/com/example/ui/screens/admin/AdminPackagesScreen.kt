package com.example.ui.screens.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import com.example.data.repository.AppRepository
import com.example.ui.components.PackageCard
import com.example.ui.screens.courier.DateFilter
import com.example.ui.theme.DuoBluePrimary
import com.example.ui.theme.DuoOrangeAccent
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPackagesScreen(
    repository: AppRepository,
    onSelectPackage: (PackageItem) -> Unit
) {
    val context = LocalContext.current
    val allPackages by repository.getAllPackages().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<PackageStatus?>(null) }
    var selectedCourierFilter by remember { mutableStateOf<String?>(null) }

    // Date filtering state
    var selectedDateMode by remember { mutableStateOf(DateFilter.TODAY) }
    var customCalendar by remember { mutableStateOf<Calendar?>(null) }

    val startOfDay = remember { repository.getStartOfDayTimestamp() }
    val oneDayMs = 86400_000L

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    val fullDateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")) }

    val distinctCouriers = remember(allPackages) {
        allPackages.map { it.courierName }.distinct().filter { it.isNotBlank() }
    }

    val filteredList = allPackages.filter { pkg ->
        val matchesSearch = searchQuery.isBlank() ||
                pkg.trackingNumber.contains(searchQuery, ignoreCase = true) ||
                pkg.recipientName.contains(searchQuery, ignoreCase = true) ||
                pkg.courierName.contains(searchQuery, ignoreCase = true) ||
                pkg.address.contains(searchQuery, ignoreCase = true)

        val matchesStatus = selectedStatus == null || pkg.status == selectedStatus
        val matchesCourier = selectedCourierFilter == null || pkg.courierName == selectedCourierFilter

        val matchesDate = when (selectedDateMode) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> pkg.scannedAt >= startOfDay
            DateFilter.YESTERDAY -> pkg.scannedAt in (startOfDay - oneDayMs) until startOfDay
        } && (customCalendar == null || {
            val pkgCal = Calendar.getInstance().apply { timeInMillis = pkg.scannedAt }
            pkgCal.get(Calendar.YEAR) == customCalendar!!.get(Calendar.YEAR) &&
                    pkgCal.get(Calendar.DAY_OF_YEAR) == customCalendar!!.get(Calendar.DAY_OF_YEAR)
        }())

        matchesSearch && matchesStatus && matchesCourier && matchesDate
    }

    // Rekap counts for current date filter
    val totalCount = filteredList.size
    val completedCount = filteredList.count { it.status == PackageStatus.SELESAI }
    val inTransitCount = filteredList.count { it.status == PackageStatus.DIBAWA_KURIR || it.status == PackageStatus.DIANTAR }
    val pendingCount = filteredList.count { it.status == PackageStatus.BARU }
    val successRate = if (totalCount > 0) (completedCount * 100) / totalCount else 100

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MONITORING LOGISTIK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoOrangeAccent
                            )
                        )
                        Text(
                            text = "Rekap & Filter Data Paket",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar (Resi, Penerima, Kurir)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari resi, penerima, atau kurir...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = DuoOrangeAccent)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_packages_search")
                )
            }

            // Date Filter Row with Calendar Picker
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Tanggal:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                item {
                    FilterChip(
                        selected = selectedDateMode == DateFilter.TODAY && customCalendar == null,
                        onClick = {
                            selectedDateMode = DateFilter.TODAY
                            customCalendar = null
                        },
                        shape = RoundedCornerShape(4.dp),
                        label = { Text("Hari Ini", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DuoOrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedDateMode == DateFilter.TODAY && customCalendar == null,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = DuoOrangeAccent,
                            borderWidth = 1.dp
                        )
                    )
                }

                item {
                    FilterChip(
                        selected = selectedDateMode == DateFilter.YESTERDAY && customCalendar == null,
                        onClick = {
                            selectedDateMode = DateFilter.YESTERDAY
                            customCalendar = null
                        },
                        shape = RoundedCornerShape(4.dp),
                        label = { Text("Kemarin", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DuoOrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedDateMode == DateFilter.YESTERDAY && customCalendar == null,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = DuoOrangeAccent,
                            borderWidth = 1.dp
                        )
                    )
                }

                item {
                    val isCustom = customCalendar != null
                    FilterChip(
                        selected = isCustom,
                        onClick = {
                            val now = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    customCalendar = picked
                                    selectedDateMode = DateFilter.ALL
                                },
                                now.get(Calendar.YEAR),
                                now.get(Calendar.MONTH),
                                now.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(4.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isCustom) Color.White else DuoOrangeAccent
                            )
                        },
                        label = {
                            Text(
                                text = if (isCustom) dateFormat.format(customCalendar!!.time) else "Pilih Tanggal 📅",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DuoOrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isCustom,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = DuoOrangeAccent,
                            borderWidth = 1.dp
                        )
                    )
                }

                item {
                    FilterChip(
                        selected = selectedDateMode == DateFilter.ALL && customCalendar == null,
                        onClick = {
                            selectedDateMode = DateFilter.ALL
                            customCalendar = null
                        },
                        shape = RoundedCornerShape(4.dp),
                        label = { Text("Semua Tanggal", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DuoOrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedDateMode == DateFilter.ALL && customCalendar == null,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = DuoOrangeAccent,
                            borderWidth = 1.dp
                        )
                    )
                }
            }

            // Status Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        shape = RoundedCornerShape(4.dp),
                        label = {
                            Text(
                                "Semua Status",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatus == null,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp
                        )
                    )
                }
                items(PackageStatus.values()) { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatus = if (selectedStatus == status) null else status },
                        shape = RoundedCornerShape(4.dp),
                        label = {
                            Text(
                                status.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatus == status,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp
                        )
                    )
                }
            }

            // Courier Filter Chips
            if (distinctCouriers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCourierFilter == null,
                            onClick = { selectedCourierFilter = null },
                            shape = RoundedCornerShape(4.dp),
                            label = { Text("Semua Kurir", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C3AED),
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCourierFilter == null,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = Color(0xFF7C3AED),
                                borderWidth = 1.dp
                            )
                        )
                    }
                    items(distinctCouriers) { cName ->
                        FilterChip(
                            selected = selectedCourierFilter == cName,
                            onClick = { selectedCourierFilter = if (selectedCourierFilter == cName) null else cName },
                            shape = RoundedCornerShape(4.dp),
                            label = { Text("Kurir: $cName", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7C3AED),
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCourierFilter == cName,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = Color(0xFF7C3AED),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            // Rekap Summary Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = DuoOrangeAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REKAP PENGIRIMAN: " + when {
                                    customCalendar != null -> dateFormat.format(customCalendar!!.time)
                                    selectedDateMode == DateFilter.TODAY -> "HARI INI (${dateFormat.format(System.currentTimeMillis())})"
                                    selectedDateMode == DateFilter.YESTERDAY -> "KEMARIN"
                                    else -> "SEMUA PERIODE"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 0.8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = StatusSuccess.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$successRate% Selesai",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = StatusSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Paket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalCount Paket", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column {
                            Text("Selesai (COD/Delivered)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$completedCount", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = StatusSuccess)
                        }
                        Column {
                            Text("Proses Antar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$inTransitCount", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = StatusWarning)
                        }
                        Column {
                            Text("Pending/Baru", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pendingCount", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DuoOrangeAccent)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Package Cards List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tidak Ada Data Paket",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tidak ditemukan catatan paket untuk filter tanggal atau kata kunci ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { pkg ->
                        PackageCard(
                            packageItem = pkg,
                            onClick = { onSelectPackage(pkg) },
                            showCourierName = true
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}
