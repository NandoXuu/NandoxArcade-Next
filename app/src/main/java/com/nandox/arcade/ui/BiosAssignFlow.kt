package com.nandox.arcade.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nandox.arcade.downloads.DownloadItem
import com.nandox.arcade.downloads.DownloadState
import com.nandox.arcade.downloads.DownloadsRepository
import com.nandox.arcade.emulator.BiosAssigner
import com.nandox.arcade.emulator.BiosGroup
import com.nandox.arcade.emulator.BiosInstaller
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step { SLOT, SOURCE, DOWNLOADS }

/**
 * Passo a passo para definir a BIOS de um console:
 * (se preciso) qual arquivo -> de onde pegar (aparelho ou downloads do NDX) -> pronto.
 * O resultado fica salvo e vale para todos os jogos do console.
 */
@Composable
fun BiosAssignFlow(group: BiosGroup, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (BiosAssigner.needsSlotChoice(group)) Step.SLOT else Step.SOURCE) }
    var slot by remember { mutableStateOf<String?>(null) }
    var downloads by remember { mutableStateOf<List<DownloadItem>?>(null) }

    fun finish(block: () -> String) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { "BIOS definida: " + block().substringAfterLast('/') }
                    .getOrElse { "Não foi possível usar este arquivo como BIOS" }
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            onDone()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) onDismiss() else finish { BiosInstaller.assignUri(context, group, slot, uri) }
    }

    LaunchedEffect(step) {
        if (step == Step.DOWNLOADS) {
            downloads = withContext(Dispatchers.IO) {
                runCatching { DownloadsRepository(context).list() }.getOrDefault(emptyList())
                    .filter { it.state == DownloadState.DONE }
            }
        }
    }

    when (step) {
        Step.SLOT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Qual arquivo da ${group.title}?") },
            text = {
                Column {
                    group.files.forEach { f ->
                        TextButton(onClick = { slot = f; step = Step.SOURCE }) { Text(f.substringAfterLast('/')) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )

        Step.SOURCE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Definir BIOS: ${group.title}") },
            text = {
                Column {
                    Text(
                        (slot?.let { "Arquivo: ${it.substringAfterLast('/')}. " } ?: "") +
                            "Escolha de onde pegar. O arquivo escolhido passa a ser usado em todos os jogos.",
                    )
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Arquivos do dispositivo") }
                    TextButton(onClick = { step = Step.DOWNLOADS }) { Text("Downloads do NDX") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )

        Step.DOWNLOADS -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Downloads do NDX") },
            text = {
                val list = downloads
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    when {
                        list == null -> Text("Carregando…")
                        list.isEmpty() -> Text("Nenhum download concluído. Baixe pelo navegador do app.")
                        else -> list.forEach { d ->
                            TextButton(onClick = {
                                val path = d.localUri?.let { Uri.parse(it).path }
                                if (path == null) {
                                    Toast.makeText(context, "Arquivo não encontrado", Toast.LENGTH_LONG).show()
                                } else {
                                    finish { BiosInstaller.assignFile(context, group, slot, File(path)) }
                                }
                            }) { Text(d.title) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { step = Step.SOURCE }) { Text("Voltar") } },
        )
    }
}
