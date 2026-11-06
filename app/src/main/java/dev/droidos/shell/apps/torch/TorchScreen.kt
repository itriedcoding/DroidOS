package dev.droidos.shell.apps.torch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.droidos.shell.ui.components.DroidRadius
import dev.droidos.shell.ui.components.EmptyState
import dev.droidos.shell.ui.components.GlassCard
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.components.ScreenScaffold
import dev.droidos.shell.ui.components.SectionLabel

@Composable
fun TorchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { TorchController(context) }
    var camera by remember { mutableStateOf(controller.flashCamera()) }
    var on by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inUse by remember { mutableStateOf(false) }

    // Follow state changes made anywhere else, so the UI cannot claim the light is on when
    // the platform turned it off (another app opening the camera, for instance).
    DisposableEffect(camera?.id) {
        val id = camera?.id
        val callback = controller.observe { changedId, enabled ->
            if (changedId == id) on = enabled
        }
        onDispose { controller.stopObserving(callback) }
    }

    val fill by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "torchFill"
    )
    val iconTint by animateColorAsState(
        targetValue = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "torchIcon"
    )

    ScreenScaffold(
        title = "Torch",
        subtitle = if (camera == null) "No flash unit reported" else "Camera ${camera!!.id} - flash mode",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                camera = controller.flashCamera()
                inUse = camera != null && controller.setTorch(camera!!.id, false).isFailure
                if (!inUse) error = null
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Recheck the flash unit")
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (camera == null) {
                EmptyState(
                    icon = Icons.Rounded.FlashlightOff,
                    title = "No flash on this device",
                    message = "CameraManager reports no camera with FLASH_INFO_AVAILABLE, so there is " +
                        "nothing to switch. Rather than show a dead button, this screen stays empty."
                )
                return@Column
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(fill.copy(alpha = if (on) 0.9f else 1f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                    .clickable {
                        val id = camera?.id ?: return@clickable
                        controller.setTorch(id, !on)
                            .onSuccess {
                                on = !on
                                error = null
                            }
                            .onFailure {
                                on = false
                                error = it.message ?: "The flash could not be switched"
                            }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = if (on) "Turn the torch off" else "Turn the torch on",
                    tint = iconTint,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                if (on) "On" else "Off",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (on) "Tap the ring to switch the flash off" else "Tap the ring to switch the flash on",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                MessageBanner(error!!, isError = true)
            }
            if (inUse) {
                Spacer(Modifier.height(10.dp))
                MessageBanner("Another app is holding the camera. Close it and recheck.", isError = false)
            }

            SectionLabel("Flash hardware")
            GlassCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Detail("Camera id", camera!!.id)
                    Detail("Units with a flash", controller.cameras().count { it.hasFlash }.toString())
                    Detail("Cameras reported", controller.cameras().size.toString())
                    Text(
                        "The torch is driven by CameraManager.setTorchMode, so it works without " +
                            "opening a preview and is released as soon as the camera is needed elsewhere.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp)
    }
}
