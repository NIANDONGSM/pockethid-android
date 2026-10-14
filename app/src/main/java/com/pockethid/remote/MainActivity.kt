package com.pockethid.remote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pockethid.remote.hid.HidService
import com.pockethid.remote.ui.PocketHidScreen
import com.pockethid.remote.ui.PocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            PocketTheme {
                var requiredPermissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    requiredPermissionsGranted = hasRequiredPermissions()
                    if (requiredPermissionsGranted) HidService.start(this)
                }
                val enableBluetoothLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    if (requiredPermissionsGranted) {
                        HidService.start(this)
                        com.pockethid.remote.hid.HidHub.register()
                    }
                }
                val discoverableLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    com.pockethid.remote.hid.HidHub.refreshPairedHosts()
                }

                fun requestPermissions() {
                    val permissions = mutableListOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                    )
                    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                LaunchedEffect(Unit) {
                    if (requiredPermissionsGranted) HidService.start(this@MainActivity)
                    else requestPermissions()
                }

                PocketHidScreen(
                    permissionsGranted = requiredPermissionsGranted,
                    onRequestPermissions = ::requestPermissions,
                    onEnableBluetooth = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onMakeDiscoverable = {
                        discoverableLauncher.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                            },
                        )
                    },
                    onOpenBluetoothSettings = {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    onRestartService = { com.pockethid.remote.hid.HidHub.register() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.pockethid.remote.hid.HidHub.refreshPairedHosts()
    }

    private fun hasRequiredPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
}
