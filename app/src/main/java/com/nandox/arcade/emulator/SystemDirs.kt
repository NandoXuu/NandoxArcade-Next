package com.nandox.arcade.emulator

import android.content.Context
import java.io.File

/** Pastas usadas pelos cores. Compartilhadas entre o app e o processo ":emulator". */
object SystemDirs {
    fun base(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

    /** BIOS e arquivos de sistema: Android/data/<pacote>/files/system */
    fun system(context: Context): File = File(base(context), "system").apply { mkdirs() }

    fun saves(context: Context): File = File(base(context), "saves").apply { mkdirs() }
}
