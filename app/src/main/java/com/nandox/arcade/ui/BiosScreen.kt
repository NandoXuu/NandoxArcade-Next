package com.nandox.arcade.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandox.arcade.emulator.BiosCatalog
import com.nandox.arcade.emulator.BiosInstaller
import com.nandox.arcade.emulator.BiosNeed
import com.nandox.arcade.emulator.SystemDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BiosScreen(onBrowser: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val systemDir = remember { SystemDirs.system(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                uris.map { runCatching { BiosInstaller.install(context, it) } }
            }
            val installed = results.mapNotNull { it.getOrNull() }.flatMap { it.installed }
            val failed = results.count { it.isFailure }
            val text = when {
                installed.isNotEmpty() ->
                    "BIOS instalada: " + installed.joinToString { it.substringAfterLast('/') }
                failed > 0 -> "Não foi possível ler o(s) arquivo(s)"
                else -> "Nenhuma BIOS conhecida nos arquivos escolhidos"
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            refresh++
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("BIOS dos emuladores", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Alguns consoles rodam melhor, ou só rodam, com a BIOS do próprio aparelho. " +
                "Use arquivos extraídos do seu console. Aceita arquivo avulso ou .zip.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importar do aparelho") }
            OutlinedButton(onClick = onBrowser) { Text("Abrir navegador") }
        }
        Text(
            "Depois de baixar no navegador, abra Downloads e toque em BIOS.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )

        // lê "refresh" para recompor a lista após instalar
        key(refresh) {
            BiosCatalog.groups.forEach { g ->
                val ok = BiosCatalog.isPresent(g, systemDir)
                val missing = BiosCatalog.missing(g, systemDir)
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        (if (ok) "✓ " else "✗ ") + g.title +
                            if (g.need == BiosNeed.OPTIONAL) "  (opcional)" else "",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(g.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (!ok) {
                        Text(
                            (if (g.anyOf) "Nomes aceitos: " else "Faltando: ") +
                                (if (g.anyOf) g.files else missing).joinToString { it.substringAfterLast('/') },
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Text(BiosCatalog.NO_BIOS_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pasta: ${systemDir.absolutePath}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        )
    }
}
