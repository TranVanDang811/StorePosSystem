#!/usr/bin/env bash
# =====================================================================
# ĐẲNG CẤP COFFEE POS - DATABASE RESTORE DRILL & RECOVERY SCRIPT
# Usage: bash scripts/restore_mysql.sh /path/to/backup_file.sql.gz
# =====================================================================

set -eo pipefail

if [ -z "$1" ]; then
    echo "Sử dụng: bash scripts/restore_mysql.sh <đường_dẫn_tệp_sao_lưu.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
CONTAINER_NAME="dangcap-mysql"
DB_NAME="backend"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "[ERROR] Không tìm thấy tệp sao lưu: ${BACKUP_FILE}"
    exit 1
fi

# Kiểm tra container MySQL
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[ERROR] Container '${CONTAINER_NAME}' không chạy! Vui lòng khởi động trước."
    exit 1
fi

echo "====================================================================="
echo "  CẢNH BÁO: Thao tác này sẽ ghi đè toàn bộ dữ liệu database '${DB_NAME}'!"
echo "  Tệp sao lưu nguồn: ${BACKUP_FILE}"
echo "====================================================================="
read -p "Bạn có chắc chắn muốn khôi phục? (Gõ chính xác chữ YES để xác nhận): " CONFIRM
if [ "${CONFIRM}" != "YES" ]; then
    echo "[INFO] Đã hủy thao tác phục hồi dữ liệu."
    exit 0
fi

echo "[INFO] [$(date '+%Y-%m-%d %H:%M:%S')] Đang giải nén và nạp dữ liệu vào MySQL..."
gunzip < "${BACKUP_FILE}" | docker exec -i "${CONTAINER_NAME}" sh -c 'exec mysql -u root -p"$MYSQL_ROOT_PASSWORD" '"${DB_NAME}"''

echo "====================================================================="
echo "✅ [SUCCESS] [$(date '+%Y-%m-%d %H:%M:%S')] Khôi phục cơ sở dữ liệu '${DB_NAME}' hoàn tất thành công!"
echo "====================================================================="
