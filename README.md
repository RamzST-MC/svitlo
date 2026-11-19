# Світло — графіки відключень (Android + сервер + веб-керування)

## Git
    git init && git add . && git commit -m "init"
    git branch -M main
    git remote add origin https://github.com/RamzST-MC/svitlo && git push -u origin main
GitHub Actions збирає APK на кожен push: Actions → Build APK → Artifacts → svitlo-debug-apk.

## Сервер (Ubuntu)
    sudo apt install -y git
    git clone https://github.com/USER/svitlo.git && cd svitlo/server
    sudo REPO=https://github.com/USER/svitlo.git bash install.sh
Скрипт виведе адресу сервера, адресу веб-керування (/admin) та токен.

## Оновлення
Змінили код → `git push`. Далі або кнопка «Оновити сервер (git pull)» у веб-керуванні,
або на сервері: `sudo bash /opt/svitlo/server/update.sh`.

## Застосунок
Android Studio → Open → папка проєкту → Run, або візьміть APK з GitHub Actions.
Адмін (PIN 1234) → адреса сервера та токен.

## Оновлення клієнта
- Застосунок сам оновлює графік: при відкритті, при поверненні в застосунок і кожні 5 хв.
- Нова версія: змінили код у `app/` → `git push` → GitHub Actions збирає APK і створює Release (v101, v102…).
  Застосунок перевіряє Releases і показує зелену картку «Доступна нова версія» з кнопкою «Завантажити».
- APK підписаний постійним ключем `app/svitlo.p12`, тому оновлення ставляться поверх старої версії.

## Автодеплой веб-сервера

Workflow `Deploy web server` запускається після push змін у `server/` до `main`. Він перевіряє синтаксис Node.js, підключається до VPS через SSH, виконує `/opt/svitlo/server/update.sh`, перезапускає сервіс і перевіряє endpoint `/schedule`. Його також можна запустити вручну: GitHub → Actions → Deploy web server → Run workflow.

Перед першим запуском додайте в Settings → Secrets and variables → Actions такі secrets:

- `VPS_HOST` — IP-адреса або домен VPS.
- `VPS_USER` — SSH-користувач для деплою.
- `VPS_SSH_KEY` — приватний SSH-ключ цього користувача.
- `VPS_KNOWN_HOSTS` — перевірений рядок хоста з файла `known_hosts`.
- `VPS_PORT` — SSH-порт; необов’язково, за замовчуванням `22`.

Користувач VPS має мати безпарольне право виконати `sudo -n bash /opt/svitlo/server/update.sh`. Для цього права sudo має бути налаштовано на VPS. Секрети не додавайте в репозиторій.
