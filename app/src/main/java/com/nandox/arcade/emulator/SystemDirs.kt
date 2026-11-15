package com.nandox.arcade.emulator

import android.content.Context
import java.io.File

/** Pastas usadas pelos cores. Compartilhadas entre o app e o processo ":emulator". */
object SystemDirs {
    fun base(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

    /** BIOS e arquivos de sistema: Android/data/<pacote>/files/system */
    fun system(context: Context): File = File(base(context), "system").apply { mkdirs() }

    /** BIOS que foram trocadas por outra escolhida pelo usuário. Ficam aqui em vez de serem apagadas. */
    fun unused(context: Context): File = File(base(context), "bios_unused").apply { mkdirs() }

    fun saves(context: Context): File = File(base(context), "saves").apply { mkdirs() }
}
