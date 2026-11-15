package com.nandox.arcade.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nandox.arcade.core.Platform
import com.nandox.arcade.core.RomIdentity
import com.nandox.arcade.core.RomMatch
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.ui.ArcadeTheme

/**
 * Recebe arquivos de ROM abertos por outros apps ("Abrir com" do gerenciador de arquivos),
 * descobre o console pela extensão/pasta e abre direto no emulador. Se for ambíguo (ex.: .iso), pergunta.
 */
class OpenGameActivity : ComponentActivity() {
    private var choices by mutableStateOf<List<Platform>?>(null)
    private lateinit var gameUri: Uri
    private var title = "Jogo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data == null) {
            finish()
            return
        }
        gameUri = data
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            runCatching { contentResolver.takePersistableUriPermission(data, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val name = displayName(data)
        title = name.substringBeforeLast('.')

        when (val match = RomIdentity.match(name, RomIdentity.folderNames(data.path))) {
            is RomMatch.Known -> start(match.platformId)
            is RomMatch.Ask -> choices = match.candidates
        }

        setContent {
            ArcadeTheme {
                choices?.let { list ->
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Qual é o console?") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(name)
                                list.forEach { p ->
                                    TextButton(onClick = { choices = null; start(p.id) }) { Text(p.name) }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { finish() }) { Text("Cancelar") } },
                    )
                }
            }
        }
    }

    private fun start(platformId: String) {
        val registry = PluginRegistry.load(this)
        val game = GameEntity(title = title, platformId = platformId, uri = gameUri.toString())
        val result = GameLauncher.launch(this, registry, game)
        GameLauncher.message(result)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        finish()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.takeIf { n -> n.isNotBlank() }?.let { n -> return n }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "jogo"
    }
}
