package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.OcrResult
import com.example.data.model.PackageItem
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.data.repository.AppRepository
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.admin.AdminCouriersScreen
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.admin.AdminPackagesScreen
import com.example.ui.screens.admin.AdminProfileScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.RegisterScreen
import com.example.ui.screens.courier.CourierDashboardScreen
import com.example.ui.screens.courier.CourierPackagesScreen
import com.example.ui.screens.courier.CourierProfileScreen
import com.example.ui.screens.courier.PackageConfirmationScreen
import com.example.ui.screens.courier.PackageDetailScreen
import com.example.ui.screens.courier.ScanScreen
import com.example.ui.theme.DuoBluePrimary
import com.example.ui.theme.DuoOrangeAccent

sealed class AppDestination {
    object Splash : AppDestination()
    object Login : AppDestination()
    object Register : AppDestination()
    object Main : AppDestination()
    object Scan : AppDestination()
    data class ConfirmPackage(val ocrResult: OcrResult?, val imageUri: String?) : AppDestination()
    data class PackageDetail(val packageItem: PackageItem) : AppDestination()
}

enum class CourierTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Beranda", Icons.Filled.Home, Icons.Outlined.Home),
    PACKAGES("Paket Saya", Icons.Filled.LocalShipping, Icons.Outlined.LocalShipping),
    SCAN("Scan", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    PROFILE("Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

enum class AdminTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Home, Icons.Outlined.Home),
    MONITORING("Monitoring", Icons.Filled.Assignment, Icons.Outlined.Assignment),
    COURIERS("Kelola Kurir", Icons.Filled.Group, Icons.Outlined.Group),
    PROFILE("Profil", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun MainApp(
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository.getInstance(context) }
    val currentUser by repository.currentUser.collectAsState()

    var currentDestination by remember { mutableStateOf<AppDestination>(AppDestination.Splash) }
    var selectedCourierTab by remember { mutableStateOf(CourierTab.HOME) }
    var selectedAdminTab by remember { mutableStateOf(AdminTab.DASHBOARD) }

    when (val destination = currentDestination) {
        is AppDestination.Splash -> {
            SplashScreen(
                onSplashFinished = {
                    if (currentUser != null) {
                        currentDestination = AppDestination.Main
                    } else {
                        currentDestination = AppDestination.Login
                    }
                }
            )
        }

        is AppDestination.Login -> {
            LoginScreen(
                repository = repository,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onLoginSuccess = { user ->
                    selectedCourierTab = CourierTab.HOME
                    selectedAdminTab = AdminTab.DASHBOARD
                    currentDestination = AppDestination.Main
                },
                onNavigateToRegister = {
                    currentDestination = AppDestination.Register
                }
            )
        }

        is AppDestination.Register -> {
            RegisterScreen(
                repository = repository,
                onRegisterSuccess = {
                    currentDestination = AppDestination.Login
                },
                onNavigateBack = {
                    currentDestination = AppDestination.Login
                }
            )
        }

        is AppDestination.Scan -> {
            ScanScreen(
                onNavigateBack = {
                    currentDestination = AppDestination.Main
                },
                onNavigateToConfirmation = { ocrResult, imageUri ->
                    currentDestination = AppDestination.ConfirmPackage(ocrResult, imageUri)
                },
                onManualInput = {
                    currentDestination = AppDestination.ConfirmPackage(null, null)
                }
            )
        }

        is AppDestination.ConfirmPackage -> {
            currentUser?.let { user ->
                PackageConfirmationScreen(
                    initialOcrResult = destination.ocrResult,
                    capturedImageUri = destination.imageUri,
                    currentUser = user,
                    repository = repository,
                    onNavigateBack = {
                        currentDestination = AppDestination.Main
                    },
                    onScanAgain = {
                        currentDestination = AppDestination.Scan
                    },
                    onViewPackagesList = {
                        selectedCourierTab = CourierTab.PACKAGES
                        currentDestination = AppDestination.Main
                    }
                )
            } ?: run {
                currentDestination = AppDestination.Login
            }
        }

        is AppDestination.PackageDetail -> {
            PackageDetailScreen(
                packageItem = destination.packageItem,
                repository = repository,
                onNavigateBack = {
                    currentDestination = AppDestination.Main
                }
            )
        }

        is AppDestination.Main -> {
            val user = currentUser
            if (user == null) {
                currentDestination = AppDestination.Login
                return
            }

            val isAdmin = user.role == UserRole.ADMIN

            Scaffold(
                bottomBar = {
                    val outlineColor = MaterialTheme.colorScheme.outline
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = outlineColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    ) {
                        if (isAdmin) {
                            AdminTab.values().forEach { tab ->
                                val isSelected = selectedAdminTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedAdminTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = {
                                        Text(
                                            tab.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("admin_nav_${tab.name.lowercase()}")
                                )
                            }
                        } else {
                            CourierTab.values().forEach { tab ->
                                val isSelected = selectedCourierTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (tab == CourierTab.SCAN) {
                                            currentDestination = AppDestination.Scan
                                        } else {
                                            selectedCourierTab = tab
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            tint = if (tab == CourierTab.SCAN) DuoOrangeAccent else if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    label = {
                                        Text(
                                            tab.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = if (tab == CourierTab.SCAN) DuoOrangeAccent else MaterialTheme.colorScheme.primary,
                                        selectedTextColor = if (tab == CourierTab.SCAN) DuoOrangeAccent else MaterialTheme.colorScheme.primary,
                                        indicatorColor = if (tab == CourierTab.SCAN) DuoOrangeAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("courier_nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isAdmin) {
                        when (selectedAdminTab) {
                            AdminTab.DASHBOARD -> AdminDashboardScreen(
                                currentUser = user,
                                repository = repository,
                                onNavigateToCouriers = { selectedAdminTab = AdminTab.COURIERS },
                                onNavigateToPackages = { selectedAdminTab = AdminTab.MONITORING }
                            )
                            AdminTab.MONITORING -> AdminPackagesScreen(
                                repository = repository,
                                onSelectPackage = { pkg ->
                                    currentDestination = AppDestination.PackageDetail(pkg)
                                }
                            )
                            AdminTab.COURIERS -> AdminCouriersScreen(
                                repository = repository
                            )
                            AdminTab.PROFILE -> AdminProfileScreen(
                                currentUser = user,
                                repository = repository,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onLogout = {
                                    currentDestination = AppDestination.Login
                                }
                            )
                        }
                    } else {
                        when (selectedCourierTab) {
                            CourierTab.HOME -> CourierDashboardScreen(
                                user = user,
                                repository = repository,
                                onNavigateToScan = {
                                    currentDestination = AppDestination.Scan
                                },
                                onNavigateToPackages = {
                                    selectedCourierTab = CourierTab.PACKAGES
                                },
                                onSelectPackage = { pkg ->
                                    currentDestination = AppDestination.PackageDetail(pkg)
                                }
                            )
                            CourierTab.PACKAGES -> CourierPackagesScreen(
                                currentUser = user,
                                repository = repository,
                                onSelectPackage = { pkg ->
                                    currentDestination = AppDestination.PackageDetail(pkg)
                                },
                                onNavigateToScan = {
                                    currentDestination = AppDestination.Scan
                                }
                            )
                            CourierTab.SCAN -> {
                                // Handled via destination navigation to AppDestination.Scan
                            }
                            CourierTab.PROFILE -> CourierProfileScreen(
                                currentUser = user,
                                repository = repository,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onLogout = {
                                    currentDestination = AppDestination.Login
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
