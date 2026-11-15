package com.nandox.arcade.emulator

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Define qual arquivo o emulador vai usar como BIOS, mesmo que o arquivo tenha outro nome.
 *
 * - Grupo "qualquer um" (PlayStation): vira a única BIOS ativa. As outras são movidas para [unused]
 *   (o core escolheria sozinho entre elas, então deixar várias tornaria a escolha imprevisível).
 * - Grupo "todos" (DS, Dreamcast): o arquivo vai para o espaço [slotRel] escolhido.
 *
 * O arquivo fica na pasta "system", então vale para todos os jogos do console e persiste entre sessões.
 */
object BiosAssigner {
    fun assign(
        system: File,
        unused: File,
        group: BiosGroup,
        slotRel: String?,
        name: String,
        input: InputStream,
    ): String {
        var entryName = name
        var stream = input
        if (name.lowercase().endsWith(".zip")) {
            val zip = ZipInputStream(input.buffered())
            var entry = zip.nextEntry
            while (entry != null && entry.isDirectory) entry = zip.nextEntry
            if (entry == null) throw java.io.IOException("zip vazio")
            entryName = entry.name
            stream = zip
        }

        val slot = slotRel ?: defaultSlot(group, entryName)
        require(slot in group.files) { "espaço $slot não pertence a ${group.title}" }

        if (group.anyOf) {
            for (other in group.files.filter { it != slot }) {
                val src = File(system, other)
                if (!src.isFile) continue
                val dst = File(unused, "${group.platformId}/$other")
                dst.parentFile?.mkdirs()
                dst.delete()
                src.renameTo(dst)
            }
        }

        val dest = File(system, slot)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.outputStream().use { stream.copyTo(it, 64 * 1024) }
        dest.delete()
        if (!part.renameTo(dest)) throw java.io.IOException("falha ao gravar $slot")
        return slot
    }

    /** Se o nome for de uma BIOS conhecida do grupo, usa o nome dela; senão, o primeiro espaço do grupo. */
    fun defaultSlot(group: BiosGroup, fileName: String): String =
        BiosCatalog.targetFor(fileName)?.takeIf { it in group.files } ?: group.files.first()

    /** Grupos "todos" com mais de um arquivo precisam que o usuário diga qual espaço está preenchendo. */
    fun needsSlotChoice(group: BiosGroup): Boolean = !group.anyOf && group.files.size > 1
}
