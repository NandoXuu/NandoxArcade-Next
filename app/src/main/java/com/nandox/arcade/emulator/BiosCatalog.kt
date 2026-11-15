package com.nandox.arcade.emulator

import java.io.File

enum class BiosNeed { RECOMMENDED, OPTIONAL }

/**
 * Grupo de arquivos de BIOS de uma plataforma. [files] são caminhos relativos à pasta "system" do core.
 * Se [anyOf] for verdadeiro, basta um dos arquivos; senão todos são necessários.
 */
data class BiosGroup(
    val platformId: String,
    val title: String,
    val files: List<String>,
    val anyOf: Boolean,
    val need: BiosNeed,
    val note: String,
)

object BiosCatalog {
    val groups: List<BiosGroup> = listOf(
        BiosGroup(
            "ps1", "PlayStation",
            listOf("scph5501.bin", "scph5500.bin", "scph5502.bin", "scph1001.bin", "scph7001.bin"),
            anyOf = true, need = BiosNeed.RECOMMENDED,
            note = "Basta um destes. Sem BIOS o core usa um modo substituto: parte dos jogos roda, outros falham.",
        ),
        BiosGroup(
            "nds", "Nintendo DS",
            listOf("bios7.bin", "bios9.bin", "firmware.bin"),
            anyOf = false, need = BiosNeed.RECOMMENDED,
            note = "Sem eles o melonDS usa uma BIOS substituta, com menos compatibilidade.",
        ),
        BiosGroup(
            "dreamcast", "Dreamcast",
            listOf("dc/dc_boot.bin", "dc/dc_flash.bin"),
            anyOf = false, need = BiosNeed.RECOMMENDED,
            note = "O Flycast tem um modo substituto, mas a BIOS melhora a compatibilidade.",
        ),
        BiosGroup(
            "gba", "Game Boy Advance",
            listOf("gba_bios.bin"),
            anyOf = false, need = BiosNeed.OPTIONAL,
            note = "Opcional: o mGBA já traz uma BIOS própria.",
        ),
    )

    /** Nintendo 64 e PSP não usam BIOS. */
    const val NO_BIOS_NOTE = "Nintendo 64 e PSP não precisam de BIOS."

    fun isPresent(group: BiosGroup, systemDir: File): Boolean {
        fun has(rel: String) = File(systemDir, rel).isFile
        return if (group.anyOf) group.files.any(::has) else group.files.all(::has)
    }

    fun missing(group: BiosGroup, systemDir: File): List<String> =
        group.files.filter { !File(systemDir, it).isFile }

    /** Caminho relativo onde um arquivo com este nome deve ficar, ou null se não for uma BIOS conhecida. */
    fun targetFor(fileName: String): String? {
        val wanted = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return groups.flatMap { it.files }.firstOrNull { it.substringAfterLast('/') == wanted }
    }

    fun looksLikeBiosCandidate(fileName: String): Boolean {
        val n = fileName.lowercase()
        return targetFor(n) != null || n.endsWith(".zip") || n.endsWith(".bin") || n.endsWith(".rom")
    }
}
