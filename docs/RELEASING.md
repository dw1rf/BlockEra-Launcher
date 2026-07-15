# Выпуск BlockEra Launcher

## Как устроены обновления

BlockEra использует подписанный Tauri Updater и GitHub Releases как статическое хранилище. Собственный backend не требуется.

В каждом релизе должны находиться:

- NSIS installer `setup.exe`;
- подпись installer-файла `.sig`;
- `latest.json` с версией, URL и подписью.

## GitHub Secrets

Workflow ожидает два repository secret:

- `TAURI_PRIVATE_KEY` — приватный ключ Tauri;
- `TAURI_KEY_PASSWORD` — пароль ключа.

Приватный ключ нельзя добавлять в Git или прикладывать к релизу. Публичный ключ хранится в `apps/app/tauri.conf.json`.

## Создание релиза

1. Изменить `version` в `apps/app/tauri.conf.json`.
2. Закоммитить изменения.
3. Создать и отправить тег с той же версией:

```bash
git tag v0.10.2702
git push origin v0.10.2702
```

Workflow `Release BlockEra Launcher` соберёт Windows installer, создаст GitHub Release и сформирует `latest.json`.

## Ручная сборка

```powershell
$env:TAURI_SIGNING_PRIVATE_KEY="C:\secure\blockera.key"
$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD="password"
pnpm --filter=@modrinth/app run tauri build --bundles nsis --features updater
```

Результат появится в `target/release/bundle/nsis/`.

## Важное ограничение Windows

Подпись Tauri защищает канал автообновлений, но не заменяет Authenticode-сертификат. Для удаления предупреждения SmartScreen потребуется отдельный сертификат подписи Windows-приложений.
