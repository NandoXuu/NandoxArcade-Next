package com.nandox.arcade.emulator

import android.content.Context

/** Guarda, para exibição, o nome original do arquivo que o usuário definiu como BIOS de cada espaço. */
class BiosSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bios", Context.MODE_PRIVATE)

    fun inUse(group: BiosGroup, slotRel: String): String? = prefs.getString(key(group, slotRel), null)

    fun setInUse(group: BiosGroup, slotRel: String, originalName: String) {
        prefs.edit().apply {
            // grupo "qualquer um": só existe uma BIOS ativa, então limpa as anotações antigas
            if (group.anyOf) group.files.forEach { remove(key(group, it)) }
            putString(key(group, slotRel), originalName)
        }.apply()
    }

    private fun key(group: BiosGroup, slotRel: String) = "inuse.${group.platformId}.$slotRel"
}
