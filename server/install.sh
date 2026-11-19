#!/usr/bin/env bash
# Установка на Ubuntu 22.04/24.04.
#   З git:   sudo REPO=https://github.com/USER/svitlo.git bash install.sh
#   Без git: sudo bash install.sh   (копіює проєкт локально; оновлення через git будуть недоступні)
set -e
[ "$EUID" -eq 0 ] || { echo "Запустіть через sudo"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"

apt-get update -y && apt-get install -y curl ca-certificates git
if ! command -v node >/dev/null || [ "$(node -v | cut -d. -f1 | tr -d v)" -lt 18 ]; then
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

id svitlo >/dev/null 2>&1 || useradd --system --home /opt/svitlo --shell /usr/sbin/nologin svitlo
mkdir -p /var/lib/svitlo
[ -f /opt/svitlo/schedule.json ] && [ ! -f /var/lib/svitlo/schedule.json ] && cp /opt/svitlo/schedule.json /var/lib/svitlo/schedule.json   # міграція зі старої версії

if [ -n "$REPO" ]; then
  rm -rf /opt/svitlo && git clone "$REPO" /opt/svitlo
elif [ ! -d /opt/svitlo/server ]; then
  mkdir -p /opt/svitlo && cp -a "$HERE/../." /opt/svitlo/
fi
chown -R svitlo:svitlo /opt/svitlo /var/lib/svitlo

if [ ! -f /etc/svitlo.env ]; then
  TOKEN=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
  printf 'ADMIN_TOKEN=%s\nPORT=3000\n' "$TOKEN" > /etc/svitlo.env
fi
grep -q '^DATA_FILE=' /etc/svitlo.env || echo 'DATA_FILE=/var/lib/svitlo/schedule.json' >> /etc/svitlo.env
chmod 600 /etc/svitlo.env

cp /opt/svitlo/server/svitlo.service /etc/systemd/system/svitlo.service
systemctl daemon-reload
systemctl enable svitlo && systemctl restart svitlo
command -v ufw >/dev/null && ufw status | grep -q active && ufw allow 3000/tcp || true

sleep 1; systemctl --no-pager --lines=3 status svitlo || true
IP=$(hostname -I | awk '{print $1}')
echo; echo "Готово."
echo "Адреса для застосунку: http://$IP:3000"
echo "Веб-керування:         http://$IP:3000/admin"
echo "Адмін-токен:           $(grep ADMIN_TOKEN /etc/svitlo.env | cut -d= -f2)"
