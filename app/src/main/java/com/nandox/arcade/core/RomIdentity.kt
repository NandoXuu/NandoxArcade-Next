package com.nandox.arcade.core

sealed interface RomMatch {
    /** Console identificado com segurança. */
    data class Known(val platformId: String) : RomMatch

    /** Ambíguo ou desconhecido: perguntar ao usuário entre [candidates]. */
    data class Ask(val candidates: List<Platform>) : RomMatch
}

/** Descobre o console de um arquivo aberto de fora do app (ex.: "Abrir com" do gerenciador de arquivos). */
object RomIdentity {
    fun match(fileName: String, folderNames: List<String>): RomMatch {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val candidates = Platforms.all.filter { ext in it.extensions }
        if (candidates.isEmpty()) return RomMatch.Ask(Platforms.all)
        val guess = PlatformClassifier.classify(fileName, folderNames)
        return if (guess != null && guess != PlatformClassifier.UNKNOWN) RomMatch.Known(guess) else RomMatch.Ask(candidates)
    }

    /** Nomes das pastas no caminho de uma URI (content:// de DocumentsProvider ou file://), sem o arquivo. */
    fun folderNames(path: String?): List<String> =
        path.orEmpty().split('/', ':').filter { it.isNotBlank() }.dropLast(1)
}
