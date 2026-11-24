package com.example.couchto5k.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private data class LocationPermissionStatus(
    val hasFine: Boolean,
    val hasCoarse: Boolean,
) {
    val hasAny: Boolean get() = hasFine || hasCoarse
    val isApproximateOnly: Boolean get() = !hasFine && hasCoarse
}

@Composable
fun LocationStatusBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStatus by remember {
        mutableStateOf(checkLocationPermissionStatus(context))
    }
    var isGpsEnabled by remember {
        mutableStateOf(checkGpsEnabled(context))
    }
    var requestedOnce by remember { mutableStateOf(value = false) }
    var requestedPreciseOnce by remember { mutableStateOf(value = false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        requestedOnce = true
        if (permissionStatus.isApproximateOnly) {
            requestedPreciseOnce = true
        }
        permissionStatus = checkLocationPermissionStatus(context)
        isGpsEnabled = checkGpsEnabled(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = checkLocationPermissionStatus(context)
                isGpsEnabled = checkGpsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isMissingOrGpsOff = !permissionStatus.hasAny || !isGpsEnabled
    val isApproximateOnly = permissionStatus.isApproximateOnly && isGpsEnabled
    val showBanner = isMissingOrGpsOff || isApproximateOnly

    AnimatedVisibility(
        visible = showBanner,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val containerColor = if (isMissingOrGpsOff) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val contentColor = if (isMissingOrGpsOff) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        val iconTint = if (isMissingOrGpsOff) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
        val bannerText = when {
            !permissionStatus.hasAny || !isGpsEnabled ->
                "Location disabled. Workout timer will continue, but GPS distance and route tracking require Location permission."
            else ->
                "Approximate location is active. Enable precise location to improve distance, pace, and route mapping accuracy."
        }
        val actionLabel = when {
            !permissionStatus.hasAny -> if (!requestedOnce) "Grant Permission" else "App Settings"
            !isGpsEnabled -> "Enable GPS"
            else -> if (!requestedPreciseOnce) "Enable Precise" else "App Settings"
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (isMissingOrGpsOff) Icons.Default.LocationDisabled else Icons.Default.MyLocation,
                        contentDescription = if (isMissingOrGpsOff) "Location disabled warning" else "Approximate location notice",
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            when {
                                !permissionStatus.hasAny -> {
                                    if (!requestedOnce) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ),
                                        )
                                    } else {
                                        openAppLocationSettings(context)
                                    }
                                }
                                !isGpsEnabled -> openSystemLocationSettings(context)
                                else -> {
                                    if (!requestedPreciseOnce) {
                                        requestedPreciseOnce = true
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ),
                                        )
                                    } else {
                                        openAppLocationSettings(context)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = actionLabel,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private fun checkLocationPermissionStatus(context: Context): LocationPermissionStatus {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return LocationPermissionStatus(hasFine = fine, hasCoarse = coarse)
}

private fun checkGpsEnabled(context: Context): Boolean {
    val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
        ?: return false
    return try {
        LocationManagerCompat.isLocationEnabled(locationManager)
    } catch (_: Exception) {
        false
    }
}

private fun openSystemLocationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun openAppLocationSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
