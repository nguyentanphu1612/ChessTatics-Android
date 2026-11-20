# ♟️ Chess Tactics — Cờ Vua Chiến Thuật (Android)


**Chess Tactics (Cờ Vua Chiến Thuật)** là ứng dụng cờ vua trên nền tảng Android được thiết kế theo phong cách **Dark Tactical UI**, tích hợp trí tuệ nhân tạo (AI) nhiều cấp độ, chế độ đấu trực tuyến thời gian thực qua Firebase Realtime Database, hệ thống lưu trữ lịch sử ván đấu bằng SQLite và trình phát lại nước đi (Replay).

---

## ✨ Tính năng nổi bật

### 1. Đa dạng chế độ chơi
* **Đấu với AI (3 cấp độ):**
  * *Dễ (Tập sự)* — Phù hợp cho người mới làm quen.
  * *Khó (Kiện tướng)* — Cân bằng giữa tốc độ phản hồi và chiều sâu chiến thuật.
  * *Cực hạn (Đại kiện tướng)* — Tối ưu hóa cắt tỉa nhánh tìm kiếm và đánh giá thế trận.
* **2 Người chơi (1 máy):** Thi đấu đối kháng trực tiếp trên cùng một thiết bị với tính năng tự động xoay/lật bàn cờ.
* **Đấu Online (Multiplayer):** Tạo phòng với mã ngẫu nhiên **6 chữ số** hoặc nhập mã để tham gia phòng của bạn bè, đồng bộ nước đi và đồng hồ đếm ngược theo thời gian thực.

### 2. Luật cờ vua chuẩn quốc tế & Trợ năng chiến thuật
* Hỗ trợ đầy đủ các luật đặc biệt: **Nhập thành (Castling)**, **Bắt tốt qua đường (En Passant)**, **Phong cấp tốt (Promotion)**, **Chiếu hết (Checkmate)** và các điều kiện **Hòa cờ** (Stalemate, lặp lại thế cờ 3 lần, luật 50 nước đi, không đủ lực lượng chiếu hết).
* **Gợi ý nước đi AI (Hint):** Phân tích thế cờ hiện tại và đề xuất nước đi tối ưu (tối đa 3 lần/trận).
* **Đi lại (Undo):** Hoàn tác nước đi trong các chế độ chơi Offline.
* **Ghi biên bản chuẩn SAN:** Hiển thị thanh lịch sử nước đi theo ký hiệu đại số chuẩn (`Nf3`, `O-O`, `exd5`...) ngay trong trận đấu.
* **Tùy chỉnh đồng hồ thi đấu:** Không giới hạn, 1 phút (Siêu chớp), 5 phút (Cờ chớp), 10 phút (Cờ nhanh), 15 phút (Tiêu chuẩn).

### 3. Lịch sử trận đấu & Trình xem lại (Replay)
* Tự động lưu trữ toàn bộ lịch sử ván đấu vào cơ sở dữ liệu cục bộ **SQLite**.
* Thống kê tổng số trận, số trận thắng, tỷ lệ thắng (%) và bộ lọc kết quả (*Tất cả / Thắng / Thua*).
* **Chế độ Replay:** Xem lại từng bước nước đi của các ván đấu cũ với điều khiển *Trước / Tự động phát / Sau*.

### 4. Cá nhân hóa & Đa ngôn ngữ
* **Cửa hàng Giao diện bàn cờ (Board Themes):** Xem trước trực tiếp và áp dụng 4 bộ màu bàn cờ (*Xanh Giải Đấu, Gỗ Cổ Điển, Xanh Hoàng Gia, Đá Cẩm Thạch*).
* **Song ngữ Việt – Anh (VI / EN):** Chuyển đổi ngôn ngữ tức thì trên toàn bộ giao diện và hộp thoại kết quả.
* **Hiệu ứng âm thanh:** Âm thanh di chuyển, ăn quân, chiếu tướng, kết thúc trận với nút bật/tắt nhanh.

---

## 🧠 Kiến trúc kỹ thuật & Thuật toán

### Thuật toán AI (`ai/ChessAI.java` & `ai/BoardEvaluator.java`)
* **Minimax kết hợp cắt tỉa Alpha-Beta (Alpha-Beta Pruning):** Giảm thiểu đáng kể số lượng nhánh phải duyệt trong cây trò chơi, giúp AI phản hồi nhanh ngay cả ở độ sâu tìm kiếm lớn.
* **Hàm lượng giá thế trận (Heuristic Evaluation):** Kết hợp giá trị vật chất của từng quân cờ với bảng điểm vị trí (**Piece-Square Tables**) để khuyến khích AI kiểm soát trung tâm, phát triển quân hợp lý và bảo vệ Vua.
* **Xử lý đa luồng (`ExecutorService`):** Toàn bộ quá trình tính toán của AI chạy trên luồng nền (Background Thread), đảm bảo giao diện 60fps mượt mà không bị khựng (ANR).

### Đồng bộ trực tuyến (`online/FirebaseChessManager.java`)
* Quản lý vòng đời phòng đấu (`WAITING` $\rightarrow$ `PLAYING` $\rightarrow$ `ENDED`) qua **Firebase Realtime Database**.
* Sử dụng `onDisconnect()` của Firebase để tự động xử lý và thông báo cho đối thủ nếu một người chơi mất kết nối hoặc thoát ứng dụng đột ngột.

---

## 📂 Cấu trúc thư mục dự án

```text
app/src/main/java/com/example/chesstactics/
├── activities/          # Các màn hình chính (Splash, Main, Game, OnlineLobby, History, Replay, SkinShop)
├── adapters/            # RecyclerView Adapters cho lịch sử trận đấu, skin và ký hiệu nước đi SAN
├── ai/                  # Thuật toán Minimax Alpha-Beta (ChessAI) & hàm đánh giá bàn cờ (BoardEvaluator)
├── database/            # Quản lý lưu trữ cục bộ SQLite (ChessDatabaseHelper)
├── engine/              # Lõi xử lý luật cờ (BoardState, MoveGenerator, GameRuleEvaluator, NotationConverter, Piece)
├── models/              # Các lớp dữ liệu (ChessMove, MatchRecord, BoardTheme)
├── online/              # Quản lý phòng đấu & đồng bộ nước đi Firebase (FirebaseChessManager)
├── utils/               # Tiện ích âm thanh (SoundManager), đa ngôn ngữ (LocaleHelper), SharedPreferences
└── views/               # Custom View vẽ bàn cờ 8x8 tương tác (ChessBoardView) & hộp thoại phong cấp
```

---

## 📸 Hình ảnh minh họa

| Màn hình chính & Cấu hình | Bàn cờ chiến thuật & Gợi ý AI | Lịch sử & Xem lại (Replay) |
| :---: | :---: | :---: |
| *(Thêm ảnh chụp màn hình tại đây)* | *(Thêm ảnh chụp màn hình tại đây)* | *(Thêm ảnh chụp màn hình tại đây)* |

---

## 🚀 Hướng dẫn cài đặt & Chạy dự án

1. **Clone kho lưu trữ:**
   ```bash
   git clone [https://github.com/](https://github.com/)<TEN_GITHUB_CUA_BAN>/ChessTactics-Android.git
   ```
2. **Mở dự án bằng Android Studio:**
   * Chọn **File** $\rightarrow$ **Open** và trỏ tới thư mục dự án vừa clone.
   * Chờ Gradle đồng bộ các thư viện phụ thuộc.
3. **Cấu hình Firebase (cho chế độ Đấu Online):**
   * Tạo dự án mới trên [Firebase Console](https://console.firebase.google.com/) và bật **Realtime Database**.
   * Tải tệp `google-services.json` và đặt vào thư mục `app/`.
   * Cập nhật đường dẫn `DATABASE_URL` trong tệp `FirebaseChessManager.java` theo URL Realtime Database của bạn.
4. **Biên dịch & Chạy:**
   * Kết nối thiết bị Android thật hoặc khởi động máy ảo (AVD) và nhấn **Run (`▶`)**.
