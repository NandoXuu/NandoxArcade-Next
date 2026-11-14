package com.nandox.arcade.emulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.swordfish.libretrodroid.GLRetroView

@Composable
fun EmulatorScreen(
    view: GLRetroView?,
    status: String?,
    layout: PadLayout,
    onKey: (keyCode: Int, down: Boolean) -> Unit,
    onMotion: (source: Int, x: Float, y: Float) -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var stickMode by remember { mutableStateOf(true) }
    var cPressed by remember { mutableStateOf(emptySet<Char>()) }

    BackHandler { menu = true }

    fun setC(c: Char, down: Boolean) {
        cPressed = if (down) cPressed + c else cPressed - c
        val x = (if ('R' in cPressed) 1f else 0f) - (if ('L' in cPressed) 1f else 0f)
        val y = (if ('D' in cPressed) 1f else 0f) - (if ('U' in cPressed) 1f else 0f)
        onMotion(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (view != null) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        }

        if (status != null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(status, color = Color.White)
            }
        }

        if (view != null && showControls) {
            val useStick = if (layout.canToggleStick) stickMode else layout.stick
            val useDpad = if (layout.canToggleStick) !stickMode else layout.dpad

            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(16.dp),
            ) {
                Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    layout.shouldersLeft.forEach { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.size(64.dp, 38.dp), RoundedCornerShape(12.dp), 14)
                    }
                }
                Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    layout.shouldersRight.forEach { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.size(64.dp, 38.dp), RoundedCornerShape(12.dp), 14)
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x55FFFFFF))
                        .clickable { menu = true }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) { Text("☰", color = Color.White) }

                if (useDpad) {
                    DPad(
                        size = 150.dp,
                        onKey = onKey,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
                    )
                }
                if (useStick) {
                    AnalogStick(
                        size = 150.dp,
                        onMove = { x, y -> onMotion(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y) },
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
                    )
                }

                val faceSize = if (listOfNotNull(layout.left, layout.top).isEmpty()) 68.dp else 56.dp
                Box(Modifier.align(Alignment.BottomEnd).size(160.dp).padding(bottom = 4.dp)) {
                    layout.bottom?.let { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.align(Alignment.BottomCenter).size(faceSize))
                    }
                    layout.right?.let { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.align(Alignment.CenterEnd).size(faceSize))
                    }
                    layout.left?.let { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.align(Alignment.CenterStart).size(faceSize))
                    }
                    layout.top?.let { b ->
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.align(Alignment.TopCenter).size(faceSize))
                    }
                }

                if (layout.cButtons) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 176.dp, bottom = 12.dp).size(124.dp)) {
                        val cSize = Modifier.size(42.dp)
                        HoldButton("▲", { setC('U', it) }, cSize.align(Alignment.TopCenter), CircleShape, 14)
                        HoldButton("▼", { setC('D', it) }, cSize.align(Alignment.BottomCenter), CircleShape, 14)
                        HoldButton("◀", { setC('L', it) }, cSize.align(Alignment.CenterStart), CircleShape, 14)
                        HoldButton("▶", { setC('R', it) }, cSize.align(Alignment.CenterEnd), CircleShape, 14)
                    }
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (layout.select) {
                        HoldButton(
                            "SELECT", { onKey(android.view.KeyEvent.KEYCODE_BUTTON_SELECT, it) },
                            Modifier.size(76.dp, 30.dp), RoundedCornerShape(15.dp), 11,
                        )
                    }
                    HoldButton(
                        "START", { onKey(android.view.KeyEvent.KEYCODE_BUTTON_START, it) },
                        Modifier.size(76.dp, 30.dp), RoundedCornerShape(15.dp), 11,
                    )
                }
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Menu") },
            text = {
                Column {
                    TextButton(onClick = { menu = false }) { Text("Continuar") }
                    TextButton(onClick = { showControls = !showControls; menu = false }) {
                        Text(if (showControls) "Ocultar controles na tela" else "Mostrar controles na tela")
                    }
                    if (layout.canToggleStick) {
                        TextButton(onClick = { stickMode = !stickMode; menu = false }) {
                            Text(if (stickMode) "Usar direcional (D-pad)" else "Usar analógico")
                        }
                    }
                    TextButton(onClick = { menu = false; onReset() }) { Text("Reiniciar jogo") }
                    TextButton(onClick = { menu = false; onExit() }) { Text("Sair do jogo") }
                }
            },
            confirmButton = {},
        )
    }
}
