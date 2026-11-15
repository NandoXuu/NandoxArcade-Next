package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class BiosInstallResult(val installed: List<String>, val ignored: Int) {
    val summary: String
        get() = when {
            installed.isNotEmpty() ->
                "BIOS instalada: " + installed.joinToString { it.substringAfterLast('/') } +
                    if (ignored > 0) " ($ignored arquivo(s) ignorado(s))" else ""
            else -> "Nenhuma BIOS conhecida neste arquivo."
        }
}

/**
 * Coloca arquivos de BIOS no lugar certo da pasta "system". Aceita arquivo avulso ou .zip.
 * Só instala nomes que o catálogo conhece, no caminho que o core espera; o resto é ignorado.
 */
object BiosInstaller {
    fun install(context: Context, uri: Uri): BiosInstallResult {
        val name = displayName(context, uri)
        val system = SystemDirs.system(context)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("não foi possível abrir o arquivo")
        return input.use { installStream(system, name, it) }
    }

    fun installFile(context: Context, file: File): BiosInstallResult =
        file.inputStream().use { installStream(SystemDirs.system(context), file.name, it) }

    fun installStream(system: File, name: String, input: InputStream): BiosInstallResult {
        val installed = mutableListOf<String>()
        var ignored = 0
        if (name.lowercase().endsWith(".zip")) {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val rel = writeKnown(system, entry.name, zip)
                    if (rel != null) installed += rel else ignored++
                }
            }
        } else {
            val rel = writeKnown(system, name, input)
            if (rel != null) installed += rel else ignored++
        }
        return BiosInstallResult(installed, ignored)
    }

    /** Não fecha [input] (pode ser um ZipInputStream no meio da leitura). */
    private fun writeKnown(system: File, name: String, input: InputStream): String? {
        val rel = BiosCatalog.targetFor(name) ?: return null
        val dest = File(system, rel)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.outputStream().use { input.copyTo(it, 64 * 1024) }
        dest.delete()
        return if (part.renameTo(dest)) rel else null
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
        }
        return uri.lastPathSegment ?: "arquivo"
    }
}
