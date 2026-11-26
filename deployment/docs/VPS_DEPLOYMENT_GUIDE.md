# HƯỚNG DẪN THỰC THI TRIỂN KHAI PRODUCTION TRÊN VPS UBUNTU
## ĐẲNG CẤP COFFEE POS SYSTEM

Tài liệu này cung cấp các bước chính xác để quản trị viên triển khai hệ thống **Đẳng Cấp Coffee POS** từ máy chủ VPS Ubuntu 22.04 / 24.04 LTS hoàn toàn mới.

---

### BƯỚC 1: BẢO MẬT & CHUẨN BỊ MÁY CHỦ UBUNTU

1. **Cập nhật hệ điều hành & cài đặt gói cần thiết:**
   ```bash
   sudo apt update && sudo apt upgrade -y
   sudo apt install -y curl git ufw fail2ban certbot
   ```

2. **Cấu hình Swapfile 2GB (Chống OOM Killer trên VPS 2GB-4GB RAM):**
   ```bash
   sudo fallocate -l 2G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   sudo sysctl vm.swappiness=10
   echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
   ```

3. **Cấu hình Tường lửa UFW (Chặn 100% cổng DB và Backend):**
   ```bash
   sudo ufw default deny incoming
   sudo ufw default allow outgoing
   sudo ufw allow 22/tcp comment 'SSH'
   sudo ufw allow 80/tcp comment 'HTTP'
   sudo ufw allow 443/tcp comment 'HTTPS'
   sudo ufw --force enable
   sudo ufw status verbose
   ```

4. **Cài đặt Docker Engine & Docker Compose Plugin:**
   ```bash
   curl -fsSL https://get.docker.com -o get-docker.sh
   sudo sh get-docker.sh
   sudo usermod -aG docker $USER
   sudo systemctl enable docker
   sudo systemctl start docker
   ```

---

### BƯỚC 2: CẤP CHỨNG CHỈ SSL VỚI CERTBOT

1. Đảm bảo tên miền `pos.dangcapcoffee.vn` đã trỏ bản ghi **A Record** về địa chỉ IPv4 của VPS.
2. Dừng tạm thời cổng 80 (nếu có service khác) và cấp chứng chỉ:
   ```bash
   sudo certbot certonly --standalone -d pos.dangcapcoffee.vn --agree-tos --email admin@dangcapcoffee.vn -n
   ```
3. Tạo thư mục chứa chứng chỉ đồng bộ với Docker Compose:
   ```bash
   sudo mkdir -p /opt/dangcap-pos/certbot/conf
   sudo cp -rL /etc/letsencrypt/* /opt/dangcap-pos/certbot/conf/
   ```

---

### BƯỚC 3: ĐƯA MÃ NGUỒN VÀ CẤU HÌNH LÊN VPS

1. **Clone mã nguồn:**
   ```bash
   git clone <REPO_URL> /opt/dangcap-pos
   cd /opt/dangcap-pos
   ```

2. **Thiết lập file biến môi trường Production:**
   ```bash
   cp .env.production.example .env.production
   chmod 600 .env.production
   nano .env.production
   ```
   *Điền chính xác các mật khẩu MySQL, Redis, JWT Signer Key và Cloudinary API Key thật.*

3. **Cấp quyền thực thi cho các script vận hành:**
   ```bash
   chmod +x scripts/*.sh
   ```

---

### BƯỚC 4: KHỞI CHẠY HỆ THỐNG PRODUCTION

```bash
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

Kiểm tra trạng thái các container:
```bash
docker compose -f docker-compose.prod.yml ps
```
*Tất cả 4 container: `dangcap-mysql`, `dangcap-redis`, `dangcap-backend`, `dangcap-frontend` phải ở trạng thái `healthy` hoặc `Up`.*

---

### BƯỚC 5: THIẾT LẬP CRONJOB SAO LƯU TỰ ĐỘNG

Mở crontab hệ thống:
```bash
sudo crontab -e
```
Thêm dòng sau vào cuối file để tự động sao lưu lúc **02:00 sáng mỗi ngày**:
```cron
0 2 * * * /opt/dangcap-pos/scripts/backup_mysql.sh >> /var/log/pos_backup.log 2>&1
```

---

### BƯỚC 6: QUY TRÌNH CẬP NHẬT PHIÊN BẢN (UPDATE & ROLLBACK)

* **Cập nhật phiên bản mới (Không gián đoạn người dùng):**
  ```bash
  cd /opt/dangcap-pos
  bash scripts/deploy_update.sh
  ```
* **Khôi phục dữ liệu khẩn cấp:**
  ```bash
  bash scripts/restore_mysql.sh /var/backups/pos_dangcap/pos_backup_YYYYMMDD_HHMMSS.sql.gz
  ```
