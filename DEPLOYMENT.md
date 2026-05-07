# 眼动仪训练系统部署指南

## 环境要求

- JDK 8 或更高版本
- Maven 3.6 或更高版本
- MySQL 5.7 或更高版本（仅生产环境需要）

## 安装步骤

### 1. 克隆项目

```bash
git clone https://github.com/mf858796-dev/bishe.git
cd bishe
```

### 2. 编译项目

```bash
.\apache-maven-3.9.5\bin\mvn.cmd clean package
```

### 3. 配置数据库

#### 开发环境（H2数据库）

开发环境使用内嵌 H2 数据库，无需额外配置。H2 控制台地址：

```text
http://localhost:8080/eye-tracking/h2-console
```

连接地址：

```text
jdbc:h2:mem:eye_tracking_system
```

#### 生产环境（MySQL数据库）

1. 创建数据库：

```sql
CREATE DATABASE eye_tracking_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 推荐通过环境变量配置生产数据库：

```bash
set DB_URL=jdbc:mysql://localhost:3306/eye_tracking_system?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai
set DB_USERNAME=your_username
set DB_PASSWORD=your_password
set APP_ADMIN_PASSWORD=change_me
```

## 运行系统

### 开发环境运行

```bash
.\apache-maven-3.9.5\bin\mvn.cmd spring-boot:run
```

### 生产环境运行

```bash
java -jar -Dspring.profiles.active=production target/eye-tracking-system-1.0.0.jar
```

## 访问系统

系统启动后，可以通过以下地址访问：

- **首页**：http://localhost:8080/eye-tracking/
- **登录页面**：http://localhost:8080/eye-tracking/user/login
- **注册页面**：http://localhost:8080/eye-tracking/user/register
- **仪表盘**：http://localhost:8080/eye-tracking/dashboard
- **代码示例**：http://localhost:8080/eye-tracking/training/code-examples

## 默认演示用户

- 用户名：`admin`
- 密码：`admin`

首次启动时系统会自动创建该账号。生产环境请通过 `APP_ADMIN_PASSWORD` 修改默认密码。

## 系统功能

1. **用户管理**：注册、登录、个人资料管理
2. **训练管理**：创建训练会话、开始训练、结束训练
3. **代码示例**：Java、Python、JavaScript三种语言的代码示例
4. **眼动数据采集**：模拟采集用户的眼动数据
5. **注意力分析**：分析用户的注意力模式和专注程度
6. **报告生成**：生成详细的注意力分析报告

## 系统架构

- **后端**：Spring Boot 2.7.15、Spring MVC、Spring Security、Spring Data JPA
- **前端**：Thymeleaf、Bootstrap 5
- **数据库**：H2（开发环境）、MySQL（生产环境）
- **构建工具**：Maven

## 目录结构

```
eye-tracking-system/
├── src/
│   ├── main/
│   │   ├── java/com/example/eyetracking/
│   │   │   ├── config/          # 配置类
│   │   │   ├── controller/      # 控制器
│   │   │   ├── model/           # 实体类
│   │   │   ├── repository/      # 数据访问接口
│   │   │   ├── service/         # 业务逻辑服务
│   │   │   └── EyeTrackingApplication.java  # 主应用类
│   │   └── resources/
│   │       ├── application.properties        # 开发环境配置
│   │       ├── application-production.properties  # 生产环境配置
│   │       └── templates/       # Thymeleaf模板
│   └── test/                    # 测试代码
├── target/                      # 编译输出目录
├── DEPLOYMENT.md                # 部署指南
└── pom.xml                      # Maven项目配置文件
```

## 常见问题

### 1. 端口冲突

如果端口8080已被占用，可以在 `application.properties` 文件中修改端口：

```properties
server.port=8081
```

### 2. 数据库连接失败

- 检查数据库服务是否启动
- 检查数据库配置是否正确
- 检查网络连接是否正常

### 3. 系统启动失败

- 检查JDK是否正确安装
- 检查Maven依赖是否正确下载
- 检查系统配置是否正确

## 技术支持

如果遇到问题，请联系系统管理员或开发团队。
