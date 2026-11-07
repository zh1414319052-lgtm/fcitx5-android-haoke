"""Generate key legends from the exact bundled engine sources (no guessed layouts).

Generated data retains the corresponding upstream licenses: libhangul LGPL-2.1,
libchewing LGPL-2.1, fcitx5-anthy and fcitx5-sayura GPL-2.0-or-later.
"""
import json
import re
import tarfile
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
profiles = {}

def add(name, labels, rows=None):
    profiles[name] = {"labels": labels, "rows": rows or ["qwertyuiop", "asdfghjkl", "zxcvbnm"]}

# Compatibility letters are the familiar standalone Korean key legends.
compat = {}
for cp in range(0x3131, 0x318F):
    ch = chr(cp)
    compat[unicodedata.normalize("NFKD", ch)] = ch
    name = unicodedata.name(ch, "").replace("HANGUL LETTER ", "")
    for prefix in ("HANGUL CHOSEONG ", "HANGUL JUNGSEONG ", "HANGUL JONGSEONG "):
        try:
            compat[unicodedata.lookup(prefix + name)] = ch
        except KeyError:
            pass

archive = ROOT / "third-party/corresponding-source/prebuilder-eb156443-complete-source.tar.gz"
with tarfile.open(archive) as source:
    def read(path):
        return source.extractfile("prebuilder/" + path).read().decode("utf-8")

    names = ["Dubeolsik", "Dubeolsik Yetgeul", "Sebeolsik 390", "Sebeolsik Final",
             "Sebeolsik Noshift", "Sebeolsik Yetgeul", "Sebeolsik Dubeol Layout", "Romaja", "Ahnmatae"]
    ids = ["2", "2y", "39", "3f", "3s", "3y", "32", "ro", "ahn"]
    for name, keyboard in zip(names, ids):
        if keyboard == "ro":  # Transliteration intentionally uses Roman letters.
            continue
        xml = ET.fromstring(read(f"libhangul/data/keyboards/hangul-keyboard-{keyboard}.xml.template"))
        labels = {}
        for item in xml.findall("./map[@id='0']/item"):
            key, value = int(item.attrib["key"], 16), int(item.attrib["value"], 16)
            if value and 33 <= key <= 126:
                labels[chr(key)] = compat.get(chr(value), chr(value))
        extended = any(not k.isalpha() and v != k for k, v in labels.items())
        rows = ["1234567890-=", "qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./\\"] if extended else None
        add("hangul/" + name, labels, rows)

    symbols = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ"
    names = "B P M F D T N L G K H J Q X ZH CH SH R Z C S I U IU A O E EH AI EI AU OU AN EN ANG ENG ER".split()
    bopomofo = dict(zip(names, symbols)) | {"TONE1": "ˉ", "TONE2": "ˊ", "TONE3": "ˇ", "TONE4": "ˋ", "TONE5": "˙"}
    physical = {"KEY_" + c.upper(): c for c in "1234567890qwertyuiopasdfghjklzxcvbnm"}
    physical.update(dict(KEY_MINUS="-", KEY_EQUAL="=", KEY_LEFTBRACE="[", KEY_RIGHTBRACE="]",
                         KEY_SEMICOLON=";", KEY_APOSTROPHE="'", KEY_COMMA=",", KEY_DOT=".", KEY_SLASH="/"))
    for name, file in [("Default Keyboard", "standard"), ("IBM Keyboard", "ibm"),
                       ("Gin-Yieh Keyboard", "ginyieh"), ("ETen Keyboard", "et")]:
        data = read(f"libchewing/src/editor/zhuyin_layout/{file}.rs")
        labels = {physical[k]: bopomofo[v] for k, v in re.findall(r"(KEY_\w+) => Bopomofo::(\w+)", data) if k in physical}
        # Chewing uses shifted letters for Latin input, so do not label those Zhuyin.
        add("chewing/" + name, labels, ["1234567890-=", "qwertyuiop[", "asdfghjkl;'", "zxcvbnm,./"])

anthy = (ROOT / "plugin/anthy/src/main/cpp/fcitx5-anthy/src/default_tables.cpp").read_text(encoding="utf-8")
block = anthy.split("fcitx_anthy_kana_typing_rule = {", 1)[1].split("\n};", 1)[0]
labels = {}
for a, b, c in re.findall(r'\{("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")\}', block):
    key, result, pending = map(json.loads, (a, b, c))
    labels[key] = result + pending
# Anthy's KanaConvertor handles kana keysyms in every typing method, including
# Romaji. This gives the touch keyboard native Kana without mutating hardware
# typing preferences or committing around the conversion/preedit engine.
block = anthy.split("fcitx_anthy_kana_table = {", 1)[1].split("\n};", 1)[0]
symbol_names = {kana: name for name, kana in re.findall(r'\{FcitxKey_(\w+), "([^"]+)"\}', block)}
keysym_source = (ROOT / "lib/fcitx5/src/main/cpp/fcitx5/src/lib/fcitx-utils/keysymgen.h").read_text(encoding="utf-8")
symbol_values = {name: int(value, 16) for name, value in re.findall(r"FcitxKey_(\w+)\s*=\s*(0x[0-9a-fA-F]+)", keysym_source)}
for key, value in list(labels.items()):
    if value not in symbol_names:
        labels[key] = labels.get(key.lower(), value)
labels["*"] = labels[":"]
for key in list(labels):
    if labels[key] not in symbol_names:
        del labels[key]
add("anthy/Kana", labels, ["1234567890-^", "qwertyuiop@[", "asdfghjkl;:]", "zxcvbnm,./\\_"])
profiles["anthy/Kana"]["symbols"] = {key: symbol_values[symbol_names[value]] for key, value in labels.items()}
block = anthy.split("fcitx_anthy_hiragana_katakana_table = {", 1)[1].split("\n};", 1)[0]
variants = {a: (b, c) for a, b, c in re.findall(r'\{"([^"]+)", "([^"]+)", "([^"]+)"\}', block)}
for name, index in [("Katakana", 0), ("HalfKatakana", 1)]:
    profiles["anthy/" + name] = profiles["anthy/Kana"] | {
        "labels": {key: variants.get(value, (value, value))[index] for key, value in labels.items()}}

sayura = (ROOT / "plugin/sayura/src/main/cpp/fcitx5-sayura/src/sayura.cpp").read_text(encoding="utf-8")
labels = {}
for row in re.findall(r"\{(0x[0-9a-f]+),([^{}]*?)FcitxKey_([A-Za-z])\}", sayura):
    labels.setdefault(row[2], chr(int(row[0], 16)))
labels["W"] = "ZWNJ"  # Visible name for the engine's standalone U+200C key.
add("sayura", labels)

out = ROOT / "app/src/main/assets/keyboard-labels.json"
out.write_text(json.dumps(profiles, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(profiles)} source-derived keyboard profiles: {out}")
