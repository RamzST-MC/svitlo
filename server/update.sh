#!/usr/bin/env bash
# Оновлення сервера з git (працює навіть якщо історія розійшлась): sudo bash /opt/svitlo/server/update.sh
set -e
cd /opt/svitlo
sudo -u svitlo git fetch origin
sudo -u svitlo git reset --hard '@{u}'
systemctl restart svitlo
systemctl --no-pager --lines=3 status svitlo || true
