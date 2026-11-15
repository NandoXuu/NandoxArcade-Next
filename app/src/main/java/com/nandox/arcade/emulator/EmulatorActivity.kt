package com.nandox.arcade.emulator

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nandox.arcade.ui.ArcadeTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Roda um core libretro (empacotado em jniLibs) dentro do app, via LibretroDroid.
 *
 * Roda em processo separado (":emulator") para que uma falha nativa de um core não derrube o app,
 * e o processo é encerrado ao sair do jogo, porque cores libretro guardam estado estático e não
 * costumam sobreviver bem a serem carregados duas vezes no mesmo processo.
 */
class EmulatorActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private var sramFile: File? = null
    @Volatile private var frameReady = false

    private var view by mutableStateOf<GLRetroView?>(null)
    private var status by mutableStateOf<String?>("Preparando jogo…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()

        val coreLib = intent.getStringExtra(EXTRA_CORE_LIB)
        val gameUri = intent.getStringExtra(EXTRA_GAME_URI)?.let(Uri::parse)
        if (coreLib == null || gameUri == null) {
            finish()
            return
        }
        val layout = PadLayout.forPlatform(intent.getStringExtra(EXTRA_PLATFORM).orEmpty())

        setContent {
            ArcadeTheme {
                EmulatorScreen(
                    view = view,
                    status = status,
                    layout = layout,
                    onKey = { code, down -> sendKey(code, down) },
                    onMotion = { source, x, y -> retroView?.sendMotionEvent(source, x, y) },
                    onReset = { retroView?.reset() },
                    onExit = { finish() },
                )
            }
        }

        lifecycleScope.launch {
            val rom = try {
                withContext(Dispatchers.IO) {
                    SystemAssets.install(applicationContext, systemDir())
                    RomStager.stage(applicationContext, gameUri)
                }
            } catch (e: Exception) {
                fail("Não foi possível abrir o jogo: ${e.message ?: "erro de leitura"}")
                return@launch
            }
            startCore(coreLib, rom, RomStager.keyFor(gameUri))
        }
    }

    private fun startCore(coreLib: String, rom: File, gameKey: String) {
        val lib = File(applicationInfo.nativeLibraryDir, coreLib)
        if (!lib.exists()) {
            fail("Core não encontrado no APK: $coreLib")
            return
        }
        val saves = SystemDirs.saves(this)
        val sram = File(saves, "$gameKey.srm").also { sramFile = it }

        val data = GLRetroViewData(this).apply {
            coreFilePath = lib.absolutePath
            gameFilePath = rom.absolutePath
            systemDirectory = systemDir().absolutePath
            savesDirectory = saves.absolutePath
            if (sram.isFile) saveRAMState = runCatching { sram.readBytes() }.getOrNull()
        }
        val v = GLRetroView(this, data)
        lifecycle.addObserver(v)
        lifecycleScope.launch { v.getGLRetroErrors().collect { fail(errorText(it)) } }
        lifecycleScope.launch {
            v.getGLRetroEvents().collect { if (it is GLRetroView.GLRetroEvents.FrameRendered) frameReady = true }
        }
        retroView = v
        view = v
        status = null
    }

    private fun errorText(code: Int): String = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY -> "Falha ao carregar o core do emulador"
        GLRetroView.ERROR_LOAD_GAME -> "O core não conseguiu abrir este jogo. Se for um console que usa BIOS, instale em Configurações > BIOS"
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "A GPU deste aparelho não é compatível com este core"
        else -> "Erro no emulador"
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun systemDir(): File = SystemDirs.system(this)

    private fun sendKey(code: Int, down: Boolean) {
        retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)
    }

    private fun saveSram() {
        val v = retroView ?: return
        val file = sramFile ?: return
        if (!frameReady) return
        runCatching {
            val bytes = v.serializeSRAM()
            if (bytes.isEmpty()) return@runCatching
            val part = File(file.parentFile, file.name + ".part")
            part.writeBytes(bytes)
            file.delete()
            part.renameTo(file)
        }
    }

    override fun onPause() {
        // antes do super: depois dele a thread GL já foi pausada e a leitura do SRAM não rodaria
        saveSram()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ---- controle físico (Bluetooth/USB) ----

    private fun isPadSource(source: Int): Boolean =
        source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD) != 0

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val padKey = KeyEvent.isGamepadButton(code) ||
            code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
        if (retroView != null && padKey && isPadSource(event.source)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) sendKey(code, true)
                KeyEvent.ACTION_UP -> sendKey(code, false)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val v = retroView
        if (v != null && ev.action == MotionEvent.ACTION_MOVE && ev.source and InputDevice.SOURCE_JOYSTICK != 0) {
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_DPAD,
                ev.getAxisValue(MotionEvent.AXIS_HAT_X), ev.getAxisValue(MotionEvent.AXIS_HAT_Y),
            )
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                ev.getAxisValue(MotionEvent.AXIS_X), ev.getAxisValue(MotionEvent.AXIS_Y),
            )
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RZ),
            )
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    companion object {
        private const val EXTRA_CORE_LIB = "coreLib"
        private const val EXTRA_GAME_URI = "gameUri"
        private const val EXTRA_PLATFORM = "platformId"

        fun intent(context: Context, coreLib: String, gameUri: Uri, platformId: String): Intent =
            Intent(context, EmulatorActivity::class.java).apply {
                putExtra(EXTRA_CORE_LIB, coreLib)
                putExtra(EXTRA_GAME_URI, gameUri.toString())
                putExtra(EXTRA_PLATFORM, platformId)
                // O arquivo pode vir de outro app ("Abrir com"): repassa a permissão temporária de leitura
                clipData = ClipData.newRawUri("jogo", gameUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
