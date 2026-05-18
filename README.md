# 基于眼动仪的代码阅读专注力训练系统

本项目是一个面向编程初学者的代码阅读专注力训练系统。系统通过 Tobii Pro Glasses 3、笔记本屏幕眼动仪桥接接口或演示模拟数据采集用户阅读代码时的注视轨迹、瞳孔直径、回视次数、扫视熵等指标，并生成训练报告，用于辅助分析学习者的代码阅读专注状态。

## 主要功能

- 用户注册、登录、个人资料维护
- Java、Python、JavaScript 代码阅读训练
- 训练会话创建、开始、结束、历史查看和删除
- Tobii Pro Glasses 3 / 笔记本屏幕眼动仪连接测试、实时眼动数据展示
- 5 点或 9 点手动确认校准流程、校准历史查看
- 无设备时的演示模拟数据模式
- 注意力分数、有效注视率、回视次数、扫视熵、数据质量等报告指标
- 按“从上到下读完整段代码”计算阅读完成度和目标完成度

## 技术栈

- Spring Boot 2.7.15
- Spring MVC / Spring Security / Spring Data JPA
- Thymeleaf / Bootstrap 5
- H2 开发数据库 / MySQL 生产数据库
- Maven / JUnit 5

## 快速启动

推荐使用项目脚本启动。脚本会检查端口、轮转日志、写入 PID，并使用适合眼动数据采集的 JVM 参数：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Build -JavaHome "C:\Program Files\Java\jdk1.8.0_181"
```

启动后访问：

- 首页：http://localhost:8080/eye-tracking/
- 登录：http://localhost:8080/eye-tracking/user/login
- 仪表盘：http://localhost:8080/eye-tracking/dashboard
- 代码训练：http://localhost:8080/eye-tracking/training/code-examples
- H2 控制台：http://localhost:8080/eye-tracking/h2-console

默认演示账号：

- 用户名：`admin`
- 密码：`admin`

开发环境默认使用 H2 内存数据库，不需要提前安装 MySQL。

查看运行状态和停止服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\status.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1
```

如果已经打包过，可以使用 `-NoBuild` 跳过构建。直接运行 `mvn spring-boot:run` 前，请先确认 Maven 已加入 PATH；本项目不建议长期依赖 IntelliJ 内置 Maven。

## 生产运行

先打包：

```bash
.\apache-maven-3.9.5\bin\mvn.cmd clean package
```

再使用生产配置运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -NoBuild -Profile production -XmsMB 1024 -XmxMB 2048
```

生产环境可通过环境变量覆盖配置：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_ADMIN_USERNAME`
- `APP_ADMIN_PASSWORD`
- `APP_ADMIN_NAME`
- `APP_ADMIN_EMAIL`
- `TOBII_GLASSES_BASE_URL`

## 测试

```bash
.\apache-maven-3.9.5\bin\mvn.cmd test
```

当前测试覆盖训练控制器基础页面、未登录跳转，以及训练会话创建、结束、查询流程。

## 毕设演示建议

1. 使用 `admin/admin` 登录系统。
2. 进入“代码示例”，选择一个示例开始训练。
3. 在“系统设置”中选择眼动设备类型：Tobii 眼镜或笔记本屏幕眼动仪。
4. 进入“眼动仪测试”和“校准”，按 5 点或 9 点流程逐点手动确认。
5. 完整从上到下阅读代码后结束训练，查看注意力分析报告。
