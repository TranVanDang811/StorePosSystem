#!/usr/bin/env bash
# =====================================================================
# ĐẲNG CẤP COFFEE POS - AUTOMATED DATABASE BACKUP SCRIPT
# Recommended Cron: 0 2 * * * /opt/dangcap-pos/scripts/backup_mysql.sh
# =====================================================================

set -eo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/pos_dangcap}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/pos_backup_${TIMESTAMP}.sql.gz"
CONTAINER_NAME="dangcap-mysql"
DB_NAME="backend"
RETENTION_DAYS=14

mkdir -p "${BACKUP_DIR}"

echo "[INFO] [$(date '+%Y-%m-%d %H:%M:%S')] Bắt đầu sao lưu cơ sở dữ liệu ${DB_NAME} từ container ${CONTAINER_NAME}..."

# Kiểm tra container có đang chạy không
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[ERROR] [$(date '+%Y-%m-%d %H:%M:%S')] Container '${CONTAINER_NAME}' không hoạt động! Hủy sao lưu."
    exit 1
fi

# Thực thi mysqldump với tùy chọn an toàn cho InnoDB (không khóa bảng, nén gzip)
docker exec "${CONTAINER_NAME}" sh -c 'exec mysqldump --single-transaction --quick --routines --triggers --hex-blob -u root -p"$MYSQL_ROOT_PASSWORD" '"${DB_NAME}"'' | gzip -9 > "${BACKUP_FILE}"

# Kiểm tra tính toàn vẹn của tệp sao lưu (kích thước tối thiểu > 10KB)
FILE_SIZE=$(wc -c <"${BACKUP_FILE}")
if [ "${FILE_SIZE}" -lt 10240 ]; then
    echo "[ERROR] [$(date '+%Y-%m-%d %H:%M:%S')] File sao lưu bất thường (${FILE_SIZE} bytes). Có thể bị lỗi kết nối!"
    exit 1
fi

echo "[SUCCESS] [$(date '+%Y-%m-%d %H:%M:%S')] Đã tạo bản sao lưu thành công: ${BACKUP_FILE} (Dung lượng: ${FILE_SIZE} bytes)"

# Tự động xóa các bản sao lưu cũ hơn RETENTION_DAYS
echo "[INFO] [$(date '+%Y-%m-%d %H:%M:%S')] Dọn dẹp các bản sao lưu cũ hơn ${RETENTION_DAYS} ngày..."
find "${BACKUP_DIR}" -type f -name "pos_backup_*.sql.gz" -mtime +${RETENTION_DAYS} -delete
echo "[SUCCESS] [$(date '+%Y-%m-%d %H:%M:%S')] Quy trình sao lưu hoàn tất an toàn."
