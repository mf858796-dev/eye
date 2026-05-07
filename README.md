# 基于眼动仪的代码阅读专注力训练系统

本项目是一个面向编程初学者的代码阅读专注力训练系统。系统通过 Tobii Pro Glasses 3 或模拟眼动数据采集用户阅读代码时的注视轨迹、瞳孔直径、回视次数、扫视熵等指标，并生成训练报告，用于辅助分析学习者的代码阅读专注状态。

## 主要功能

- 用户注册、登录、个人资料维护
- Java、Python、JavaScript 代码阅读训练
- 训练会话创建、开始、结束、历史查看和删除
- Tobii Pro Glasses 3 连接测试、实时眼动数据展示
- 9 点校准流程和校准历史查看
- 无设备时的模拟眼动数据训练模式
- 注意力分数、有效注视率、回视次数、扫视熵、数据质量等报告指标

## 技术栈

- Spring Boot 2.7.15
- Spring MVC / Spring Security / Spring Data JPA
- Thymeleaf / Bootstrap 5
- H2 开发数据库 / MySQL 生产数据库
- Maven / JUnit 5

## 快速启动

```bash
.\apache-maven-3.9.5\bin\mvn.cmd spring-boot:run
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

## 生产运行

先打包：

```bash
.\apache-maven-3.9.5\bin\mvn.cmd clean package
```

再使用生产配置运行：

```bash
java -jar -Dspring.profiles.active=production target/eye-tracking-system-1.0.0.jar
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
3. 没有眼动仪时点击“使用模拟数据”，移动鼠标模拟注视轨迹。
4. 点击“结束训练”，查看注意力分析报告。
5. 展示“眼动仪测试”“校准”“训练会话历史”等模块，说明系统支持真实 Tobii 数据接入。
