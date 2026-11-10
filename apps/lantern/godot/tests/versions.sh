#!/usr/bin/env bash
# 1.5.1: «зайти с любой версии» — сервер Paper новейшей версии (+ViaVersion/ViaBackwards/ViaRewind, +Geyser)
# → relay → друг; заходят Java-боты старых версий (mineflayer) и Bedrock-клиент (gophertunnel: RakNet и HTTP-вход).
# tests/versions.sh [папка данных]   (NNCLIENT — тестовый клиент Bedrock, необязательно)
HERE=$(cd "$(dirname "$0")" && pwd)
export MCV=${MCV:-1.20.1,1.12.2,1.8.8}
export LANTERN_TEST_GEYSER_NOAUTH=${LANTERN_TEST_GEYSER_NOAUTH:-1}
exec bash "$HERE/e2e.sh" java "${1:-/tmp/lantern-e2e-versions}"
