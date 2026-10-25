# Nook of the Dead

A zombie survival add-on for [Nookworks](https://www.curseforge.com/minecraft/mc-mods/nookworks), for Minecraft Java
Edition on NeoForge. Throw the dice at the If Mirror and step through, and the other world is one where it all went
wrong: a civilisation of Minecraft players, fallen long ago - and the dead, everywhere.

- Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/nook-of-the-dead)
- Player guide (English, 日本語, 简体中文, 繁體中文, 한국어, Español, Русский, Português): <https://riftwright.github.io/nook-of-the-dead-guide/>
- Changes: [CHANGELOG.md](CHANGELOG.md)

This repository is the mod's source, updated at each release. Nook of the Dead is a personal project:
bug reports are welcome (Issues, or the comments on CurseForge), but pull requests are not expected.

## Building

Requires a Java 25 JDK. Nookworks, which this is an add-on to, is fetched from CurseForge by the build (Curse Maven).

```
./gradlew build                # the jar in build/libs
./gradlew runGameTestServer    # the in-game test suite (fails on any failure)
./gradlew runClient            # a development client, with Nookworks beside it
```

On Windows, `tools/gradle.ps1 <task>` (or `tools/gradle.sh` from Git Bash or WSL) finds a Java 25 JDK for you.

The textures are drawn by the scripts under `tools/` (Python 3, `pip install -r tools/requirements.txt`):
`tools/paint_*.py`. `tools/gen_lang_copies.py` makes the regional language files from the eight written ones (with
`tools/lang_regional/`); `tools/s2_bare.py`, `tools/roof_camps.py` and `tools/refuge_hull.py` derive data from the
building templates.

## How it is made

Nook of the Dead is made with AI (Claude, by Anthropic) under the author's direction: the author decides what goes in,
plays it and judges it; the AI writes the code, the text and the textures (drawn by the scripts kept here), and drafts
buildings to the author's brief - some of which the author built or reworked by hand.

## Licence

MIT - see [LICENSE](LICENSE). All of the mod's textures are its own; no vanilla images are included.

---

自作のマイクラMod「Nook of the Dead」（Nookworks の追加Mod）のソースコードです（リリースごとに公開しています）。
個人で作っているModなので、不具合の報告は歓迎しますが（Issue か CurseForge のコメントへ）、プルリクエストは受け付けていません。
