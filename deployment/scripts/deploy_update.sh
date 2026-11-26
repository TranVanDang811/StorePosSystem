#!/usr/bin/env bash
# =====================================================================
# ĐẲNG CẤP COFFEE POS - ZERO-DOWNTIME ROLLING DEPLOYMENT & ROLLBACK SCRIPT
# Usage: bash scripts/deploy_update.sh
# =====================================================================

set -eo pipefail

COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env.production"
BACKEND_SERVICE="backend"
FRONTEND_SERVICE="frontend"

echo "====================================================================="
echo "  ĐẲNG CẤP COFFEE POS - ROLLING UPDATE SCRIPT"
echo "====================================================================="

if [ ! -f "${ENV_FILE}" ]; then
    echo "[ERROR] Không tìm thấy tệp ${ENV_FILE}! Hãy tạo từ ${ENV_FILE}.example trước."
    exit 1
fi

# 1. Đánh tag phiên bản hiện tại làm bản sao lưu Rollback
echo "[1/5] Đánh dấu tag bản sao lưu (Rollback Tag)..."
docker tag dangcap-backend:latest dangcap-backend:previous || true

# 2. Kéo mã nguồn mới nhất
echo "[2/5] Kéo mã nguồn mới từ Git..."
git pull origin main

# 3. Build container mới với cache
echo "[3/5] Build các Docker Images mới..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build "${BACKEND_SERVICE}" "${FRONTEND_SERVICE}"

# 4. Cập nhật và khởi chạy lại container
echo "[4/5] Khởi chạy container mới..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps "${BACKEND_SERVICE}" "${FRONTEND_SERVICE}"

# 5. Kiểm tra sức khỏe tự động (Healthcheck Loop)
echo "[5/5] Kiểm tra trạng thái sức khỏe phiên bản mới..."
MAX_ATTEMPTS=15
ATTEMPT=1
IS_HEALTHY=false

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo "  Đang kiểm tra Actuator Health (Lần ${ATTEMPT}/${MAX_ATTEMPTS})..."
    HEALTH_OUTPUT=$(docker exec dangcap-backend wget -qO- http://localhost:8080/backend/actuator/health || true)
    
    if echo "${HEALTH_OUTPUT}" | grep -q '"status":"UP"'; then
        IS_HEALTHY=true
        break
    fi
    sleep 5
    ATTEMPT=$((ATTEMPT + 1))
done

if [ "$IS_HEALTHY" = true ]; then
    echo "====================================================================="
    echo "🚀 [SUCCESS] Triển khai phiên bản mới thành công! Hệ thống đang hoạt động ổn định."
    echo "====================================================================="
else
    echo "====================================================================="
    echo "❌ [FAILURE] Phiên bản mới không vượt qua Health Check!"
    echo "⚠️  Tiến hành Rollback tự động về phiên bản trước đó..."
    echo "====================================================================="
    docker tag dangcap-backend:previous dangcap-backend:latest
    docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps "${BACKEND_SERVICE}"
    echo "✅ [ROLLBACK COMPLETE] Đã phục hồi về phiên bản trước an toàn."
    exit 1
fi
