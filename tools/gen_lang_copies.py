"""The language files that are copies of another, as Nookworks has them (its tools/gen_lang_copies.py): Minecraft falls
back from a regional language only to English, never to a sibling, so a player in Español (México) would see English.

- zh_hk from zh_tw: Hong Kong reads Taiwan's Traditional Chinese.
- es_mx, es_ar, es_cl, es_ec, es_uy, es_ve from es_es: es_es is written in a neutral Spanish for Spain and Latin America.
- pt_pt from pt_br.

Where a regional language needs its own words (2026-10-25: vosotros in the Spanish copies, Brazilian in the Portuguese
one), tools/lang_regional/<name>.json holds just those keys, laid over the copy: es_419 over the Latin American Spanish
ones, pt_pt over pt_pt.

Edit only the source of each; this writes the rest. From the mod's root:
    python tools/gen_lang_copies.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / "src/main/resources/assets/nookofthedead/lang"

# written by hand (en_us aside); the GameTest languages_are_whole checks these and the copies
TRANSLATED = ["ja_jp", "zh_cn", "zh_tw", "ko_kr", "es_es", "ru_ru", "pt_br"]
COPIES = {"zh_hk": "zh_tw", **{code: "es_es" for code in ("es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve")},
          "pt_pt": "pt_br"}


REGIONAL = Path(__file__).resolve().parent / "lang_regional"
OVER = {**{code: "es_419" for code in ("es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve")}, "pt_pt": "pt_pt"}


def main():
    for copy, source in COPIES.items():
        over = REGIONAL / f"{OVER.get(copy, '-')}.json"
        if not over.exists():
            (LANG / f"{copy}.json").write_bytes((LANG / f"{source}.json").read_bytes())
            continue
        text = json.loads((LANG / f"{source}.json").read_text(encoding="utf-8"))
        own = json.loads(over.read_text(encoding="utf-8"))
        missing = [k for k in own if k not in text]
        if missing:
            raise SystemExit(f"{over.name}: keys not in {source}: {missing}")
        text.update(own)
        (LANG / f"{copy}.json").write_text(json.dumps(text, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("wrote " + ", ".join(f"{copy}.json from {source}.json" for copy, source in COPIES.items()))


if __name__ == "__main__":
    main()
