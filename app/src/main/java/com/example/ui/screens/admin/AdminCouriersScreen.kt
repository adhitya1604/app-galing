package com.example.ui.screens.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.AppRepository
import com.example.ui.theme.DuoBluePrimary
import com.example.ui.theme.DuoOrangeAccent
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCouriersScreen(
    repository: AppRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val couriers by repository.getAllCouriers().collectAsState(initial = emptyList())
    val allPackages by repository.getAllPackages().collectAsState(initial = emptyList())
    var selectedCourierDetail by remember { mutableStateOf<User?>(null) }

    var showAddCourierDialog by remember { mutableStateOf(false) }
    var newCourierName by remember { mutableStateOf("") }
    var newCourierPhone by remember { mutableStateOf("") }
    var newCourierEmail by remember { mutableStateOf("") }
    var newCourierPassword by remember { mutableStateOf("kurir123") }
    var addErrorMessage by remember { mutableStateOf<String?>(null) }
    var isAddingCourier by remember { mutableStateOf(false) }

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MANAJEMEN PERSONEL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoOrangeAccent
                            )
                        )
                        Text(
                            text = "Tim Kurir Cabang",
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newCourierName = ""
                    newCourierPhone = ""
                    newCourierEmail = ""
                    newCourierPassword = "kurir123"
                    addErrorMessage = null
                    showAddCourierDialog = true
                },
                containerColor = DuoOrangeAccent,
                contentColor = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("admin_add_courier_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Kurir")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tambah Kurir",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (couriers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DuoOrangeAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = DuoOrangeAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Belum Ada Data Kurir",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Data kurir telah dibersihkan. Buat profil kurir pertama Anda untuk mulai menugaskan paket pengiriman.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddCourierDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Akun Kurir Baru")
                            }
                        }
                    }
                }
            } else {
                items(couriers, key = { it.id }) { courier ->
                val courierPkgCount = allPackages.count { it.courierId == courier.courierId }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedCourierDetail = courier }
                        .testTag("courier_item_${courier.id}"),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = courier.name.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = courier.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ID: ${courier.courierId} • ${courier.phone}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = StatusSuccessBg,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (courier.isActive) "AKTIF" else "NONAKTIF",
                                    color = StatusSuccess,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = DuoOrangeAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Paket Ditugaskan: $courierPkgCount Paket",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = DuoOrangeAccent
                                )
                            }

                            OutlinedButton(
                                onClick = { selectedCourierDetail = courier },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "Detail Kurir",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
        }
    }

    // Courier Detail Dialog
    selectedCourierDetail?.let { courier ->
        AlertDialog(
            onDismissRequest = { selectedCourierDetail = null },
            title = {
                Text(
                    text = "Detail Kurir: ${courier.name}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ID Kurir: ${courier.courierId}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("Email: ${courier.email}")
                    Text("WhatsApp: ${courier.phone}")
                    Text("Status Akun: Aktif Bertugas", color = StatusSuccess, fontWeight = FontWeight.Bold)
                    Text("Area Operasional: Bandung & Sekitarnya")
                    Text("Tingkat Penyelesaian: 98% Tepat Waktu")
                }
            },
            shape = RoundedCornerShape(8.dp),
            confirmButton = {
                Button(
                    onClick = {
                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${courier.phone}"))
                        context.startActivity(callIntent)
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hubungi Kurir")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { selectedCourierDetail = null },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    // Add New Courier Dialog
    if (showAddCourierDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isAddingCourier) showAddCourierDialog = false
            },
            title = {
                Text(
                    text = "Tambah Kurir Baru",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Daftarkan personil kurir Anda. Akun akan tersinkron otomatis ke cloud database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    addErrorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedTextField(
                        value = newCourierName,
                        onValueChange = { newCourierName = it },
                        label = { Text("Nama Lengkap Kurir") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_new_courier_name")
                    )

                    OutlinedTextField(
                        value = newCourierPhone,
                        onValueChange = { newCourierPhone = it },
                        label = { Text("Nomor HP / WhatsApp") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_new_courier_phone")
                    )

                    OutlinedTextField(
                        value = newCourierEmail,
                        onValueChange = { newCourierEmail = it },
                        label = { Text("Email (Opsional)") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_new_courier_email")
                    )

                    OutlinedTextField(
                        value = newCourierPassword,
                        onValueChange = { newCourierPassword = it },
                        label = { Text("Password Akun") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_new_courier_password")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCourierName.isBlank()) {
                            addErrorMessage = "Nama kurir harus diisi"
                            return@Button
                        }
                        if (newCourierPhone.isBlank() || newCourierPhone.length < 9) {
                            addErrorMessage = "Nomor HP minimal 9 digit"
                            return@Button
                        }
                        if (newCourierPassword.length < 4) {
                            addErrorMessage = "Password minimal 4 karakter"
                            return@Button
                        }

                        val email = if (newCourierEmail.isNotBlank()) {
                            newCourierEmail.trim()
                        } else {
                            "kurir.${newCourierPhone.takeLast(4)}@duogaling.com"
                        }

                        isAddingCourier = true
                        addErrorMessage = null
                        scope.launch {
                            val res = repository.register(
                                name = newCourierName.trim(),
                                email = email,
                                phone = newCourierPhone.trim(),
                                password = newCourierPassword,
                                role = UserRole.COURIER
                            )
                            isAddingCourier = false
                            if (res.isSuccess) {
                                showAddCourierDialog = false
                            } else {
                                addErrorMessage = res.exceptionOrNull()?.message ?: "Gagal menambah kurir"
                            }
                        }
                    },
                    enabled = !isAddingCourier,
                    colors = ButtonDefaults.buttonColors(containerColor = DuoOrangeAccent),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("admin_confirm_add_courier_button")
                ) {
                    if (isAddingCourier) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Simpan Kurir")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddCourierDialog = false },
                    enabled = !isAddingCourier,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
