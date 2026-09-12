package com.example.ui.screens.courier

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PackageItem
import com.example.data.model.PackageStatus
import com.example.data.model.User
import com.example.data.repository.AppRepository
import com.example.ui.components.PackageCard
import com.example.ui.theme.DuoBluePrimary
import com.example.ui.theme.DuoOrangeAccent
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class DateFilter(val label: String) {
    ALL("Semua Tanggal"),
    TODAY("Hari Ini"),
    YESTERDAY("Kemarin")
}

enum class SortOption(val label: String) {
    NEWEST("Terbaru"),
    OLDEST("Terlama"),
    NAME_AZ("Nama (A-Z)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierPackagesScreen(
    currentUser: User,
    repository: AppRepository,
    onSelectPackage: (PackageItem) -> Unit,
    onNavigateToScan: () -> Unit
) {
    val context = LocalContext.current
    val packages by repository.getPackagesForCourier(currentUser.courierId).collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<PackageStatus?>(null) }
    var selectedDateFilter by remember { mutableStateOf(DateFilter.TODAY) }
    var customCalendar by remember { mutableStateOf<Calendar?>(null) }
    var selectedSort by remember { mutableStateOf(SortOption.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    val startOfDay = remember { repository.getStartOfDayTimestamp() }
    val oneDayMs = 86400_000L
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }

    // Filtering logic
    val filteredPackages = packages.filter { pkg ->
        val matchesSearch = searchQuery.isBlank() ||
                pkg.trackingNumber.contains(searchQuery, ignoreCase = true) ||
                pkg.recipientName.contains(searchQuery, ignoreCase = true) ||
                pkg.address.contains(searchQuery, ignoreCase = true)

        val matchesStatus = selectedStatus == null || pkg.status == selectedStatus

        val matchesDate = when (selectedDateFilter) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> pkg.scannedAt >= startOfDay
            DateFilter.YESTERDAY -> pkg.scannedAt in (startOfDay - oneDayMs) until startOfDay
        } && (customCalendar == null || {
            val pkgCal = Calendar.getInstance().apply { timeInMillis = pkg.scannedAt }
            pkgCal.get(Calendar.YEAR) == customCalendar!!.get(Calendar.YEAR) &&
                    pkgCal.get(Calendar.DAY_OF_YEAR) == customCalendar!!.get(Calendar.DAY_OF_YEAR)
        }())

        matchesSearch && matchesStatus && matchesDate
    }.let { list ->
        when (selectedSort) {
            SortOption.NEWEST -> list.sortedByDescending { it.scannedAt }
            SortOption.OLDEST -> list.sortedBy { it.scannedAt }
            SortOption.NAME_AZ -> list.sortedBy { it.recipientName.lowercase() }
        }
    }

    // Rekap counts
    val totalCount = filteredPackages.size
    val completedCount = filteredPackages.count { it.status == PackageStatus.SELESAI }
    val inTransitCount = filteredPackages.count { it.status == PackageStatus.DIBAWA_KURIR || it.status == PackageStatus.DIANTAR }
    val pendingCount = filteredPackages.count { it.status == PackageStatus.BARU }
    val completionRate = if (totalCount > 0) (completedCount * 100) / totalCount else 100

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DAFTAR MANIFES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoOrangeAccent
                            )
                        )
                        Text(
                            text = "Paket Bawaan",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(imageVector = Icons.Default.Sort, contentDescription = "Urutkan", tint = DuoOrangeAccent)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { sortOpt ->
                                DropdownMenuItem(
                                    text = { Text(sortOpt.label, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedSort = sortOpt
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
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
            // Search Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari resi, penerima, atau alamat...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = DuoOrangeAccent)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Hapus")
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
                        .testTag("packages_search_bar")
                )
            }

            // Status Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            selectedContainerColor = DuoOrangeAccent,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStatus == status,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = DuoOrangeAccent,
                            borderWidth = 1.dp
                        )
                    )
                }
            }

            // Date Filters Row & Calendar Picker
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(DateFilter.values()) { dFilter ->
                    Surface(
                        color = if (selectedDateFilter == dFilter && customCalendar == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedDateFilter == dFilter && customCalendar == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable {
                            selectedDateFilter = dFilter
                            customCalendar = null
                        }
                    ) {
                        Text(
                            text = dFilter.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selectedDateFilter == dFilter && customCalendar == null) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedDateFilter == dFilter && customCalendar == null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Custom Date Picker Chip
                item {
                    val isCustomActive = customCalendar != null
                    Surface(
                        color = if (isCustomActive) DuoOrangeAccent else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCustomActive) DuoOrangeAccent else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable {
                            val now = customCalendar ?: Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 0, 0, 0)
                                    }
                                    customCalendar = picked
                                    selectedDateFilter = DateFilter.ALL
                                },
                                now.get(Calendar.YEAR),
                                now.get(Calendar.MONTH),
                                now.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (isCustomActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (customCalendar != null) dateFormat.format(customCalendar!!.time) else "Pilih Tanggal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isCustomActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (customCalendar != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Hapus",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { customCalendar = null }
                                )
                            }
                        }
                    }
                }
            }

            // Rekap Pengiriman Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "REKAP PENGIRIMAN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = DuoOrangeAccent
                            )
                        )
                        Text(
                            text = when {
                                customCalendar != null -> "Tanggal: ${dateFormat.format(customCalendar!!.time)}"
                                selectedDateFilter == DateFilter.TODAY -> "Hari Ini"
                                selectedDateFilter == DateFilter.YESTERDAY -> "Kemarin"
                                else -> "Semua Riwayat"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Total", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$completedCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = StatusSuccess
                            )
                            Text("Selesai", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$inTransitCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = DuoBluePrimary
                            )
                            Text("Diantar", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$pendingCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = StatusWarning
                            )
                            Text("Pending", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Package List or Empty State
            if (filteredPackages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = DuoOrangeAccent,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Tidak Ada Paket Ditemukan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Mulai scan label paket untuk menambahkan paket bawaan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateToScan,
                                colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("empty_state_scan_button")
                            ) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "SCAN PAKET",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
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
                    items(filteredPackages, key = { it.id }) { pkg ->
                        PackageCard(
                            packageItem = pkg,
                            onClick = { onSelectPackage(pkg) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
