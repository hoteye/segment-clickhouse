# dubbo-provider-app

## 项目简介
`dubbo-provider-app` 是一个基于 Spring Boot 的 Dubbo 服务提供方示例程序。该项目实现了一个简单的远程调用服务，提供 `sayHello` 方法，允许客户端通过 Dubbo 进行调用。

## 功能
- 提供一个名为 `sayHello` 的服务方法，接受一个字符串参数并返回问候信息。
- 不使用注册中心，直接在本地启动服务。
- 服务端口配置为 8745。

## 项目结构
```
dubbo-provider-app
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           └── provider
│   │   │               ├── DubboProviderApp.java
│   │   │               └── service
│   │   │                   └── HelloService.java
│   │   └── resources
│   │       ├── application.yml
│   │       └── logback-spring.xml
├── pom.xml
└── README.md
```

## 使用方法
1. 确保已安装 Java 1.8 及 Maven。
2. 克隆项目到本地。
3. 在项目根目录下运行以下命令构建项目：
   ```
   mvn clean install
   ```
4. 启动应用程序：
   ```
   mvn spring-boot:run
   ```
5. 服务启动后，您可以通过 Dubbo 客户端调用 `sayHello` 方法。

## 依赖
- Spring Boot
- Apache Dubbo

## 日志配置
日志配置文件 `logback-spring.xml` 定义了日志的输出格式和级别，确保在运行时能够记录相关信息。

## 注意事项
- 本项目不包含 Dubbo 消费方的实现，仅作为服务提供方示例。