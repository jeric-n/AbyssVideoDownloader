## Lựa Chọn Ngôn Ngữ
- [Phiên bản Tiếng Anh](README.md)

# AbyssVideoDownloader (Trình tải xuống video Abyss)

**AbyssVideoDownloader** là một ứng dụng dòng lệnh viết bằng Kotlin, cho phép người dùng tải video từ Abyss.to. Nó hỗ trợ tải video ở nhiều độ phân giải khác nhau.

## Yêu cầu trước khi sử dụng

Đảm bảo bạn đã cài đặt **Java Development Kit (JDK 21)** trước khi sử dụng AbyssVideoDownloader.

### Hướng dẫn cài đặt JDK 21

<details>
<summary>Linux</summary>

Để cài đặt JDK 21 trên Linux qua dòng lệnh:

1. **Mở Terminal**.
2. **Cập nhật danh sách gói**:
   ```bash
   sudo apt update
   ```
3. **Cài đặt OpenJDK 21** (nếu có sẵn trong trình quản lý gói của bạn):
   ```bash
   sudo apt install openjdk-21-jdk
   ```
   Nếu JDK 21 không có sẵn, bạn có thể tải và cài đặt thủ công:
    - **Tải JDK 21** từ [trang web của Oracle](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html).
    - **Giải nén tệp đã tải**:
      ```bash
      tar -xvzf jdk-21_linux-x64_bin.tar.gz
      ```
    - **Di chuyển thư mục JDK** vào `/opt`:
      ```bash
      sudo mv jdk-21 /opt/
      ```
    - **Cài đặt biến môi trường**:
      ```bash
      sudo update-alternatives --install /usr/bin/java java /opt/jdk-21/bin/java 1
      sudo update-alternatives --install /usr/bin/javac javac /opt/jdk-21/bin/javac 1
      ```
    - **Kiểm tra cài đặt**:
      ```bash
      java -version
      ```

</details>

<details>
<summary>Windows</summary>

Để cài đặt JDK 21 trên Windows:

1. **Tải JDK 21** từ [trang web của Oracle](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html).
2. **Chạy trình cài đặt** và làm theo hướng dẫn.
3. Trong quá trình cài đặt, chọn tùy chọn **thêm Java vào PATH của hệ thống**.
4. **Kiểm tra cài đặt**:
    - Mở **Command Prompt** (`cmd`).
    - Nhập lệnh sau:
      ```cmd
      java -version
      ```

Nếu lệnh trả về phiên bản (ví dụ: `java 21`), cài đặt đã thành công.

</details>

## Tải tệp JAR

Bạn có thể lấy tệp `abyss-dl.jar` theo hai cách:

1. **Tải tệp JAR mới nhất**:
    - Truy cập [Releases](https://github.com/abdlhay/AbyssVideoDownloader/releases) để tải tệp `abyss-dl.jar` mới nhất.

2. **Tự xây dựng tệp JAR**:
    - Nếu muốn xây dựng dự án hoặc cần bản cập nhật mới nhất, hãy làm theo hướng dẫn trong phần [**Xây dựng dự án**](#xây-dựng-dự-án).

## Sử dụng

### Tải video với AbyssVideoDownloader

Chạy lệnh sau:

```bash
java -jar abyss-dl.jar <id_or_url_with_resolution> [-H <header>] [--header <header>] [-o <output_file_path>] [-c <number_of_connections>]
```

- **ID hoặc URL video**:  
  Thêm ID video hoặc URL vào đầu lệnh, với độ phân giải tùy chọn (`h`, `m`, `l`).  
  Ví dụ:
    - `K8R6OOjS7 h` (độ phân giải cao)
    - `https://abysscdn.com/?v=K8R6OOjS7 l` (độ phân giải thấp)
    - `K8R6OOjS7 m` (độ phân giải trung bình)
    - `id1 h,id2 m` (nhiều video với độ phân giải)

- **Thêm HTTP Headers**:  
  Sử dụng `-H` hoặc `--header` để thêm headers (VD: `-H "Authorization: Bearer TOKEN"`). Lặp lại để thêm nhiều header.

- **Tệp đầu ra**:  
  Sử dụng `-o <path>` để chỉ định nơi lưu video (VD: `-o /path/video.mp4`). Mặc định là thư mục hiện tại.

- **Kết nối đồng thời**:  
  Sử dụng `-c <1-10>` để đặt số kết nối đồng thời (mặc định: 4).

- **Chế độ chi tiết**:  
  Thêm `--verbose` để bật chế độ chi tiết.

### Ví dụ

1. **Tải video và lưu với tên cụ thể trong thư mục đã chọn**:
   ```bash
   java -jar abyss-dl.jar K8R6OOjS7 -o /path/to/directory/my_video.mp4
   ```

2. **Tải video và lưu với tên mặc định trong thư mục hiện tại**:
   ```bash
   java -jar K8R6OOjS7 abyss-dl.jar
   ```

3. **Tải video sử dụng header tùy chỉnh**:
   ```bash
   java -jar abyss-dl.jar K8R6OOjS7 -H "Authorization: Bearer TOKEN" -o my_video.mp4
   ```

4. **Tải video với nhiều kết nối**:
   ```bash
   java -jar abyss-dl.jar -c 7 -o my_video.mp4 K8R6OOjS7
   ```

5. **Tải video với header tùy chỉnh và nhiều kết nối**:
   ```bash
   java -jar abyss-dl.jar K8R6OOjS7 -H "Authorization: Bearer TOKEN" -c 3 -o /path/to/my_video.mp4
   ```

6. **Tải nhiều video với độ phân giải từ tệp hoặc trực tiếp**:  
   **Từ tệp**:
    - Nội dung tệp:
      ```text
      id1
      id2 h
      https://example.com/video l
      ```
    - Lệnh:
      ```bash
      java -jar abyss-dl.jar videos.txt
      ```
   **Trực tiếp với danh sách phân tách bằng dấu phẩy**:
   ```bash
   java -jar abyss-dl.jar id1 h,id2 m,https://example.com/video l
   ```

## Xây dựng dự án

Làm theo các bước sau để tự xây dựng AbyssVideoDownloader:

1. **Fork repository**:
    - Nhấn nút **"Fork"**.

2. **Mở tab Actions**:
    - Trong repository của bạn, chuyển đến tab **"Actions"**.

3. **Chạy workflow**:
    - Tìm workflow `build AbyssVideoDownloader` và nhấn **"Run workflow"**.

4. **Truy cập phần Releases**:
    - Sau khi build xong, vào **Releases** để tải tệp JAR.

## TODOs

Danh sách các nhiệm vụ và tính năng sẽ được cập nhật trong tương lai:

- [x] Clean Code.
- [x] Hỗ trợ tải đồng thời nhiều video.
- [x] Tích hợp dependency injection.
- [x] Cải thiện xử lý lỗi và thêm thông báo mô tả.
- [x] Tiếp tục tải các tệp bị lỗi.
- [x] Thêm logic retry.
- [x] Cải thiện logging.
- [ ] Hỗ trợ proxy.
- [ ] Thêm giao diện đồ họa hoặc ứng dụng Android.

Hãy góp ý và đóng góp cho dự án nếu bạn có ý tưởng mới!
