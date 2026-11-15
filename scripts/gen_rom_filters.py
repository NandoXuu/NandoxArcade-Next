#!/usr/bin/env python3
"""Gera os intent-filters de "Abrir com" (ROMs) no AndroidManifest a partir de core/Platform.kt.

Rode depois de adicionar uma extensão/plataforma:   python3 scripts/gen_rom_filters.py

Por que tantos pathPattern: o Android compara o padrão com o caminho da URI e é sensível a maiúsculas;
além disso ".*\\.z64" não casa nomes com mais de um ponto ("Super Game v1.2.z64"). Por isso há
variações de profundidade e de caixa. Um segundo filtro, sem mimeType, cobre apps que abrem o arquivo
sem informar o tipo.
"""
import re
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
PLATFORM = ROOT / "app/src/main/java/com/nandox/arcade/core/Platform.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
START = "<!-- ROM-FILTERS-START (gerado por scripts/gen_rom_filters.py) -->"
END = "<!-- ROM-FILTERS-END -->"
DEPTHS = 4

exts = []
for body in re.findall(r'Platform\("[^"]*",\s*"[^"]*",\s*"[^"]*",\s*setOf\(([^)]*)\)\)', PLATFORM.read_text()):
    for e in re.findall(r'"([^"]+)"', body):
        if e not in exts:
            exts.append(e)
assert exts, "nenhuma extensão encontrada em Platform.kt"


def patterns():
    for e in exts:
        for variant in (e.lower(), e.upper()):
            for depth in range(DEPTHS):
                yield ".*" + "".join("\\\\..*" for _ in range(depth)) + "\\\\." + variant


def data_lines(with_mime):
    pad = " " * 16
    out = [f'{pad}<data android:scheme="content" />', f'{pad}<data android:scheme="file" />',
           f'{pad}<data android:host="*" />']
    if with_mime:
        out.append(f'{pad}<data android:mimeType="*/*" />')
    out += [f'{pad}<data android:pathPattern="{p}" />' for p in patterns()]
    return "\n".join(out)


def filt(with_mime):
    return f"""            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
{data_lines(with_mime)}
            </intent-filter>"""


block = f"""        {START}
        <!-- Aparece em "Abrir com" do gerenciador de arquivos para: {', '.join(exts)} -->
        <activity
            android:name=".launcher.OpenGameActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.NandoxArcade.Transparent">
{filt(True)}
{filt(False)}
        </activity>
        {END}"""

text = MANIFEST.read_text()
new = re.sub(re.escape("        " + START) + r".*?" + re.escape(END), lambda m: block, text, flags=re.S)
assert new != text or block in text, "marcadores não encontrados no manifesto"
MANIFEST.write_text(new)
print(f"{len(exts)} extensões, {sum(1 for _ in patterns())} padrões por filtro")
