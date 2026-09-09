# SIA Backend

Backend service của hệ thống **Student Information Assistant**.

Backend được xây dựng bằng **Java + Spring Boot**, chịu trách nhiệm xử lý API,
nghiệp vụ hệ thống, xác thực/phân quyền, quản lý dữ liệu và giao tiếp với AI Service.

## Technology Stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- MongoDB
- Redis
- WebSocket
- Docker

## Project Structure

```text
sia-backend/
├── .mvn/                       # Maven Wrapper
├── docker/                     # Cấu hình Docker cho Backend
├── docs/                       # Tài liệu API, WebSocket và convention
├── scripts/                    # Script build, migration, development
│
├── src/
│   ├── main/
│   │   ├── java/vn/hcmute/edu/sia/
│   │   │   ├── client/         # Client giao tiếp với AI service
│   │   │   ├── config/         # Cấu hình Spring, CORS, Redis, MongoDB,...
│   │   │   ├── controller/     # REST API Controller
│   │   │   ├── dto/            # Request/Response DTO
│   │   │   ├── entity/         # Entity PostgreSQL và MongoDB
│   │   │   ├── enums/          # Enum dùng trong hệ thống
│   │   │   ├── event/          # Domain/Application event
│   │   │   ├── exception/      # Exception và Global Exception Handler
│   │   │   ├── listener/       # Xử lý các event
│   │   │   ├── repository/     # Truy cập dữ liệu
│   │   │   ├── scheduler/      # Tác vụ định kỳ
│   │   │   ├── security/       # Authentication, Authorization, JWT,...
│   │   │   ├── service/        # Business logic
│   │   │   ├── util/           # Helper dùng chung
│   │   │   ├── validation/     # Validation phía server
│   │   │   ├── websocket/      # WebSocket/STOMP
│   │   │   └── SiaBackendApplication.java
│   │   │
│   │   └── resources/
│   │       ├── db/             # Migration/SQL
│   │       ├── static/         # Tài nguyên tĩnh
│   │       ├── templates/      # Email/OTP template
│   │       └── application.properties
│   │
│   └── test/                   # Unit test và integration test
│
├── .dockerignore
├── .gitignore
├── mvnw
├── mvnw.cmd
├── pom.xml
└── README.md
```

## Main Modules

Các module nghiệp vụ sẽ được tổ chức bên trong `service/` khi được triển khai.

Ví dụ:

```text
service/
├── auth/               # Đăng ký, đăng nhập, OTP, mật khẩu
├── account/            # Hồ sơ người dùng
├── chatbot/            # Điều phối yêu cầu Chatbot AI
├── conversation/       # Lịch sử hội thoại AI
├── advisorchat/        # Chat Sinh viên - Tư vấn viên
├── document/           # Quản lý tài liệu
├── documentrequest/    # Yêu cầu thêm/sửa/xóa tài liệu
├── category/           # Danh mục đào tạo
├── usermanagement/     # Quản lý người dùng
├── notification/       # Thông báo
├── storage/            # Lưu trữ file
└── redis/              # OTP, session, rate limit, presence,...
```

Các business service chính sử dụng mô hình:

```text
Controller
    ↓
Service Interface
    ↓
Service Implementation
    ↓
Repository
```

Ví dụ:

```text
service/auth/
├── AuthService.java
└── AuthServiceImpl.java
```

## Build

Compile project:

```bash
./mvnw compile
```

Windows:

```powershell
.\mvnw.cmd compile
```

Build JAR:

```bash
./mvnw clean package
```

File build được tạo trong:

```text
target/
```

## Run

Chạy bằng Maven:

```bash
./mvnw spring-boot:run
```

Hoặc chạy file JAR:

```bash
java -jar target/sia-backend-0.0.1-SNAPSHOT.jar
```

## Docker

Dockerfile nằm tại:

```text
docker/Dockerfile
```

Build Docker image:

```bash
docker build -f docker/Dockerfile -t sia-backend .
```

Run container:

```bash
docker run --name sia-backend-container -p 8080:8080 sia-backend
```
