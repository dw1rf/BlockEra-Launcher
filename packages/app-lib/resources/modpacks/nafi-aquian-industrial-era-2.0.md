# Индустриальная эра 2.0 — публикационная ревизия BlockEra

Автор сборки: Nafi Aquian. Официальный канал: <https://discord.gg/YhA2BbpDBj>.
BlockEra не является создателем сборки; интеграция неофициальная.

Разрешение автора получено в Discord 17 июля 2026 года сообщением
«Да конечно закидывай». Скриншот не хранится в репозитории из-за персональных
данных.

## Исходник

- Файл: `Индастриал 2.0.mrpack` (не изменялся).
- Размер: 728 596 619 байт.
- SHA-256: `d5d9c246bf9c20a2effe6a73b9a9ff63d23660fe7a74bfe044edac36a15497f3`.
- Minecraft 1.19.2, Forge 43.5.2, версия сборки 2.0.

## Публикационная копия

- Файл: `nafi-aquian-industrial-era-2.0-blockera.1.mrpack`.
- Размер: 104 764 896 байт.
- SHA-256: `146fa14f449cc69ab1089576cc1094a008ee3770d39b61e3ef3cdf1d578cc448`.
- Manifest: 114 файлов, `versionId` `2.0+blockera.1`.
- Release: <https://github.com/dw1rf/BlockEra-Launcher/releases/tag/modpack-nafi-industrial-era-2.0-blockera.1>.

Из архива удалены `mods.rar.disabled`, `.archive-unpack`, WorldEdit session,
TLSkinCape, MCEF, WebDisplays, Cinema native libraries и связанные конфиги.
Windows Defender 17 июля 2026 года не обнаружил угроз. Все 198 ZIP-entry были
повторно прочитаны; EXE/DLL и запрещённые служебные пути отсутствуют.

Все 12 ранее встроенных сторонних JAR перенесены в `files[]` и загружаются по
SHA-1/SHA-512 с официальных Modrinth или CurseForge CDN. В частности,
Create Deco (All Rights Reserved) и Simple Voice Chat (All Rights Reserved) не
распространяются внутри архива. Kotlin for Forge загружается с Modrinth.

MCEF/WebDisplays и нативные библиотеки исключены, поскольку для исходных
бинарников не удалось одновременно подтвердить официальный SHA-256, цифровые
подписи и полную цепочку CEF/Chromium license notices.
