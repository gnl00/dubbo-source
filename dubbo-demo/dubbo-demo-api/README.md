# dubbo-demo-api

> 一个基于 Dubbo 3.x 的 API 模式 RPC 调用示例项目，从 API 使用到源码原理的完整演示。

## 📖 项目简介

本项目是 Apache Dubbo 的入门示例，展示了如何使用纯 API 方式（非 Spring 集成）进行服务提供和消费。通过本项目，你可以：

- 📚 **学习 Dubbo 基础概念**：服务注册、服务发现、远程调用
- 🔍 **深入理解 Dubbo 运行原理**：通过详细的日志分析了解 Dubbo 内部机制
- 🚀 **快速上手**：内置 EmbeddedZooKeeper，无需额外安装注册中心
- 🎯 **掌握核心特性**：Triple 协议、异步调用、泛化调用等

**适用场景：**
- Dubbo 初学者入门学习
- 理解 RPC 框架的服务治理机制
- Dubbo 源码学习的前置实验
- 微服务架构的技术选型参考

---

## 🏗️ 项目结构

```
dubbo-demo-api/
├── dubbo-demo-api-interface/     # 服务接口定义模块
│   └── src/main/java/org/apache/dubbo/api/demo/
│       └── DemoService.java      # 服务接口
│
├── dubbo-demo-api-provider/      # 服务提供者模块
│   └── src/main/java/org/apache/dubbo/demo/provider/
│       ├── Application.java      # Provider 启动类
│       ├── DemoServiceImpl.java  # 服务实现类
│       └── EmbeddedZooKeeper.java # 内嵌 ZooKeeper
│
├── dubbo-demo-api-consumer/      # 服务消费者模块
│   └── src/main/java/org/apache/dubbo/demo/consumer/
│       └── Application.java      # Consumer 启动类
│
└── pom.xml                       # 父 POM 配置
```

### 模块说明

| 模块 | 说明 | 主要内容 |
|------|------|---------|
| **dubbo-demo-api-interface** | 服务接口定义 | 定义服务契约，Provider 和 Consumer 共同依赖 |
| **dubbo-demo-api-provider** | 服务提供者 | 实现服务接口，注册服务到注册中心 |
| **dubbo-demo-api-consumer** | 服务消费者 | 从注册中心发现服务，发起远程调用 |

---

## ⚙️ 环境要求

- **JDK**: 8+ (推荐 JDK 11 或 17)
- **Maven**: 3.6+
- **ZooKeeper**: 无需额外安装（项目内置 EmbeddedZooKeeper）
- **操作系统**: Windows / macOS / Linux

---

## 🚀 快速开始

### 步骤 1: 启动服务提供者 (Provider)

运行 Provider 主类，它会自动启动内嵌的 ZooKeeper 并注册服务：

```bash
# 方式一：使用 Maven 命令
cd dubbo-demo-api-provider
mvn clean compile exec:java -Dexec.mainClass="org.apache.dubbo.demo.provider.Application"

# 方式二：在 IDE 中直接运行
# 打开 org.apache.dubbo.demo.provider.Application 并运行 main 方法
```

**启动成功标志：**

看到以下日志表示 Provider 启动成功：

```shell
[DUBBO] Dubbo Module[1.1.1] has started.
[INSTANCE_REGISTER] Successfully registered interface application mapping for service org.apache.dubbo.api.demo.DemoService
```

### 步骤 2: 启动服务消费者 (Consumer)

在新的终端窗口启动 Consumer：

```bash
# 方式一：使用 Maven 命令
cd dubbo-demo-api-consumer
mvn clean compile exec:java -Dexec.mainClass="org.apache.dubbo.demo.consumer.Application"

# 方式二：在 IDE 中直接运行
# 打开 org.apache.dubbo.demo.consumer.Application 并运行 main 方法
```

**启动成功标志：**

看到以下提示表示 Consumer 启动成功：

```shell
### ==> Enter anything to process remote invoke <== ###
```

### 步骤 3: 测试远程调用

在 Consumer 控制台输入任意字符（非 "exit"）并回车，即可触发远程调用：

```shell
### ==> Enter anything to process remote invoke <== ###
test                          # 输入任意字符并回车
17:43:41.717 |-INFO  [main] org.apache.dubbo.demo.consumer.Application -| Hello dubbo, response from provider: 192.168.145.37:50051
```

**退出程序：**

在 Consumer 控制台输入 `exit` 并回车。

---

## 📝 核心代码解析

### 1. 服务接口定义

服务接口定义了 Provider 和 Consumer 之间的契约：

```java
// dubbo-demo-api-interface/src/main/java/org/apache/dubbo/api/demo/DemoService.java
package org.apache.dubbo.api.demo;

import java.util.concurrent.CompletableFuture;

public interface DemoService {

    /**
     * 同步调用方法
     */
    String sayHello(String name);

    /**
     * 异步调用方法（返回 CompletableFuture）
     * 详细用法参考：https://github.com/apache/dubbo-samples
     */
    default CompletableFuture<String> sayHelloAsync(String name) {
        return CompletableFuture.completedFuture(sayHello(name));
    }
}
```

**关键点：**
- `sayHello`: 同步调用方法，阻塞等待结果返回
- `sayHelloAsync`: 异步调用方法，立即返回 `CompletableFuture`，支持非阻塞调用

### 2. 服务提供者实现

服务实现类提供具体的业务逻辑：

```java
// dubbo-demo-api-provider/src/main/java/org/apache/dubbo/demo/provider/DemoServiceImpl.java
package org.apache.dubbo.demo.provider;

import org.apache.dubbo.api.demo.DemoService;
import org.apache.dubbo.rpc.RpcContext;

public class DemoServiceImpl implements DemoService {

    @Override
    public String sayHello(String name) {
        logger.info("Hello " + name + ", request from consumer: "
                + RpcContext.getServiceContext().getRemoteAddress());
        return "Hello " + name + ", response from provider: "
                + RpcContext.getServiceContext().getLocalAddress();
    }
}
```

**Provider 启动配置：**

```java
// dubbo-demo-api-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
private static void startWithBootstrap() {
    // 1. 配置服务
    ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
    service.setInterface(DemoService.class);        // 设置服务接口
    service.setRef(new DemoServiceImpl());          // 设置服务实现

    // 2. 配置注册中心（ZooKeeper）
    ConfigCenterConfig configCenterConfig = new ConfigCenterConfig();
    configCenterConfig.setAddress("zookeeper://127.0.0.1:2181");

    // 3. 启动 Provider
    DubboBootstrap.getInstance()
        .application(new ApplicationConfig("dubbo-demo-api-provider"))
        .configCenter(configCenterConfig)
        .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
        .metadataReport(new MetadataReportConfig("zookeeper://127.0.0.1:2181"))
        .protocol(new ProtocolConfig(CommonConstants.TRIPLE, -1))  // Triple 协议，随机端口
        .service(service)
        .start()
        .await();
}
```

### 3. 服务消费者调用

Consumer 通过注册中心发现服务并发起调用：

```java
// dubbo-demo-api-consumer/src/main/java/org/apache/dubbo/demo/consumer/Application.java
private static void runWithBootstrap() {
    // 1. 配置服务引用
    ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
    reference.setInterface(DemoService.class);

    // 2. 配置消费者（关闭启动检查）
    ConsumerConfig consumerConfig = new ConsumerConfig();
    consumerConfig.setCheck(false);  // 启动时不检查 Provider 是否存在

    // 3. 启动 Consumer
    DubboBootstrap.getInstance()
        .application(new ApplicationConfig("dubbo-demo-api-consumer"))
        .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
        .protocol(new ProtocolConfig(CommonConstants.TRIPLE, -1))
        .reference(reference)
        .consumer(consumerConfig)
        .start();
}

private static void execRemoteCall() {
    DemoService demoService = bootstrap.getCache().get(reference);

    // 【方式一】普通调用（同步）
    String message = demoService.sayHello("dubbo");
    logger.info(message);

    // 【方式二】泛化调用（无需依赖接口）
    GenericService genericService = (GenericService) demoService;
    Object result = genericService.$invoke(
        "sayHello",
        new String[] {String.class.getName()},
        new Object[] {"dubbo generic invoke"}
    );
    logger.info(result.toString());
}
```

### 4. 普通调用 vs 泛化调用

| 调用方式 | 特点 | 适用场景 |
|---------|------|---------|
| **普通调用** | 需要依赖服务接口 JAR 包 | 编译时已知接口定义的场景 |
| **泛化调用** | 无需依赖服务接口，通过反射调用 | 网关、测试平台、服务管理后台等 |

**泛化调用示例：**

```java
// 启用泛化调用
reference.setGeneric("true");

// 使用 GenericService 调用
GenericService genericService = (GenericService) demoService;
Object result = genericService.$invoke(
    "sayHello",                              // 方法名
    new String[] {String.class.getName()},   // 参数类型
    new Object[] {"dubbo"}                   // 参数值
);
```

---

## 🔧 配置说明

### 核心配置项

| 配置项 | 说明 | 示例值 |
|-------|------|-------|
| **application** | 应用名称，用于服务治理 | `dubbo-demo-api-provider` |
| **registry** | 注册中心地址 | `zookeeper://127.0.0.1:2181` |
| **protocol** | RPC 协议 | `triple`（Dubbo 3.x 默认协议） |
| **metadataReport** | 元数据中心地址 | `zookeeper://127.0.0.1:2181` |
| **configCenter** | 配置中心地址 | `zookeeper://127.0.0.1:2181` |
| **check** | 启动时检查 Provider 是否存在 | `false`（关闭检查） |

### Triple 协议说明

Triple 是 Dubbo 3.x 的默认协议，具有以下优势：

- ✅ **基于 HTTP/2**：支持流式调用、多路复用
- ✅ **跨语言互通**：兼容 gRPC，支持多语言客户端
- ✅ **高性能**：二进制序列化，性能优于 HTTP/1.1
- ✅ **向后兼容**：兼容 Dubbo 2.x 协议

---

## 📊 运行原理深度解析

### 服务启动流程概览

```
Provider 启动流程                        Consumer 启动流程
     │                                        │
     ├─ 1. 连接注册中心 (ZooKeeper)          ├─ 1. 连接注册中心
     ├─ 2. 加载配置                          ├─ 2. 订阅 Provider 服务
     ├─ 3. 暴露服务 (Triple 协议)            ├─ 3. 等待 Provider 上线通知
     ├─ 4. 注册服务到注册中心                ├─ 4. 接收服务实例列表
     ├─ 5. 订阅配置变更                      ├─ 5. 建立服务引用
     └─ 6. 等待 Consumer 调用                └─ 6. 准备就绪，等待调用
```

---

### 一、Consumer 启动流程详解

#### 阶段 1: 连接注册中心

Consumer 首先连接 ZooKeeper 注册中心：

<details>
<summary>📋 点击展开：连接注册中心日志</summary>

```shell
17:43:41.450 |-INFO  [main] CuratorFrameworkImpl -| Starting
17:43:41.451 |-INFO  [main] org.apache.zookeeper.ZooKeeper -| Initiating client connection, connectString=127.0.0.1:2181 sessionTimeout=60000
17:43:41.453 |-INFO  [main-SendThread(127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn -| Opening socket connection to server localhost/127.0.0.1:2181
17:43:41.454 |-INFO  [main-SendThread(127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn -| Socket connection established, initiating session
17:43:41.458 |-INFO  [main-SendThread(127.0.0.1:2181)] org.apache.zookeeper.ClientCnxn -| Session establishment complete on server localhost/127.0.0.1:2181, session id = 0x10000c1be520007
```

</details>

**关键日志解读：**

- `Initiating client connection`: 开始连接 ZooKeeper
- `Session establishment complete`: 会话建立成功，获得 session id

#### 阶段 2: 订阅 Provider 服务

Consumer 向注册中心订阅 `DemoService` 服务：

<details>
<summary>📋 点击展开：订阅服务日志</summary>

```shell
17:43:41.600 |-INFO  [main] ServiceDiscoveryRegistry -| [DUBBO] Trying to subscribe from apps dubbo-demo-api-provider for service key org.apache.dubbo.api.demo.DemoService
17:43:41.610 |-INFO  [main-EventThread] DefaultMappingListener -| [DUBBO] Received mapping notification from meta server, {serviceKey: org.apache.dubbo.api.demo.DemoService, apps: [dubbo-demo-api-provider]}
```

</details>

**关键日志解读：**

- `Trying to subscribe`: Consumer 尝试订阅 `DemoService`
- `Received mapping notification`: 接收到服务映射通知，知道哪些应用提供了该服务

#### 阶段 3: 等待 Provider 上线

如果此时 Provider 还未启动，Consumer 会等待：

<details>
<summary>📋 点击展开：等待 Provider 日志</summary>

```shell
17:43:41.664 |-INFO  [main] ServiceInstancesChangedListener -| [DUBBO] Received instance notification, serviceName: dubbo-demo-api-provider, instances: 0
17:43:41.685 |-WARN  [main] ServiceDiscoveryRegistryDirectory -| [DUBBO] Received url with EMPTY protocol, will clear all available addresses.
17:43:41.702 |-INFO  [main] MigrationRuleHandler -| [DUBBO] Succeed Migrated to APPLICATION_FIRST mode. Service Name: org.apache.dubbo.api.demo.DemoService
```

</details>

**关键日志解读：**

- `instances: 0`: 当前没有可用的 Provider 实例
- `EMPTY protocol`: 收到空地址通知，清空可用地址列表
- `APPLICATION_FIRST mode`: 迁移到应用级服务发现模式（Dubbo 3.x 新特性）

#### 阶段 4: Consumer 启动完成

```shell
17:43:41.753 |-INFO  [main] DefaultModuleDeployer -| [DUBBO] Dubbo Module[1.1.1] has started.
```

此时 Consumer 已启动完成，但处于等待状态，等待 Provider 上线。

---

### 二、Provider 启动流程详解

#### 阶段 1: 连接注册中心并加载配置

Provider 连接 ZooKeeper 后，首先尝试从配置中心拉取配置：

<details>
<summary>📋 点击展开：连接和配置加载日志</summary>

```shell
18:19:01.599 |-INFO  [Curator-ConnectionStateManager-0] Curator5ZookeeperClient -| [DUBBO] Curator zookeeper client instance initiated successfully, session id is 10000c1be52000e
18:19:01.622 |-INFO  [main] ConfigurationUtils -| [DUBBO] Config center was specified, but no config item found.
```

</details>

**关键日志解读：**

- `session id is 10000c1be52000e`: ZooKeeper 会话建立成功
- `no config item found`: 配置中心无配置项，使用本地配置启动

#### 阶段 2: 注册服务到注册中心

Provider 将服务 URL 注册到注册中心：

<details>
<summary>📋 点击展开：服务注册日志</summary>

```shell
18:23:42.697 |-INFO  [main] org.apache.dubbo.config.ServiceConfig -| [INSTANCE_REGISTER] Register dubbo service org.apache.dubbo.api.demo.DemoService url tri://192.168.145.37:50051/org.apache.dubbo.api.demo.DemoService?application=dubbo-demo-api-provider&methods=sayHello,sayHelloAsync to registry 127.0.0.1:2181
```

</details>

**服务 URL 解析：**

```
tri://192.168.145.37:50051/org.apache.dubbo.api.demo.DemoService?
    application=dubbo-demo-api-provider    # 应用名
    &methods=sayHello,sayHelloAsync        # 可用方法列表
    &dubbo=2.0.2                           # Dubbo 版本
    &interface=org.apache.dubbo.api.demo.DemoService  # 服务接口
    &side=provider                         # 角色：提供者
```

#### 阶段 3: 订阅配置变更

Provider 订阅注册中心的配置变更通知（用于动态配置更新）：

<details>
<summary>📋 点击展开：订阅配置变更日志</summary>

```shell
18:23:42.720 |-INFO  [main] ZookeeperRegistry -| [DUBBO] Subscribe: provider://192.168.145.37:50051/org.apache.dubbo.api.demo.DemoService?category=configurators&check=false
```

</details>

**为什么 Provider 也要订阅？**

- 动态配置：可以从注册中心动态下发配置（如负载均衡策略、超时时间等）
- 服务治理：支持动态禁用服务、调整权重等

#### 阶段 4: 注册元数据

Provider 将服务的详细元数据（方法签名、参数类型等）注册到元数据中心：

<details>
<summary>📋 点击展开：元数据注册日志</summary>

```shell
18:23:44.473 |-INFO  [DubboSaveMetadataReport-thread-1] ZookeeperMetadataReport -| [METADATA_REGISTER] store provider metadata.
Identifier: MetadataIdentifier{application='dubbo-demo-api-provider', serviceInterface='org.apache.dubbo.api.demo.DemoService', side='provider'}
Definition: FullServiceDefinition{
  methods=[
    MethodDefinition [name=sayHello, parameterTypes=[java.lang.String], returnType=java.lang.String],
    MethodDefinition [name=sayHelloAsync, parameterTypes=[java.lang.String], returnType=java.util.concurrent.CompletableFuture]
  ]
}
```

</details>

**元数据的作用：**

- 服务自省：Consumer 可以查询服务的详细定义
- 泛化调用：支持无需接口的泛化调用
- 服务治理：Admin 控制台可以展示服务详情

#### 阶段 5: 注册接口-应用映射

Dubbo 3.x 新增了应用级服务发现，需要维护接口到应用的映射关系：

```shell
18:23:44.501 |-INFO  [main] org.apache.dubbo.config.ServiceConfig -| [METADATA_REGISTER] Successfully registered interface application mapping for service org.apache.dubbo.api.demo.DemoService
```

**接口-应用映射的作用：**

在 Dubbo 3.x 中，注册中心存储的是应用级别的实例信息，而非接口级别。映射关系记录：

```
org.apache.dubbo.api.demo.DemoService -> dubbo-demo-api-provider
```

这样 Consumer 订阅 `DemoService` 时，可以通过映射找到提供该服务的应用 `dubbo-demo-api-provider`。

#### 阶段 6: 暴露元数据服务

Provider 会暴露两个内部服务，用于元数据查询：

```shell
18:23:44.930 |-INFO  [main] ServiceConfig -| [SERVICE_PUBLISH] Export dubbo service org.apache.dubbo.metadata.MetadataService to url tri://192.168.145.37:50051/org.apache.dubbo.metadata.MetadataService
18:23:45.433 |-INFO  [main] ServiceConfig -| [SERVICE_PUBLISH] Export dubbo service org.apache.dubbo.metadata.MetadataServiceV2 to url tri://192.168.145.37:50051/org.apache.dubbo.metadata.MetadataServiceV2
```

**元数据服务的作用：**

- `MetadataService`: 提供服务元数据查询（Dubbo 2.x 兼容）
- `MetadataServiceV2`: 提供增强的元数据查询（Dubbo 3.x）

Consumer 可以通过这些服务查询 Provider 暴露的所有服务信息。

#### 阶段 7: Provider 启动完成

```shell
18:23:45.434 |-INFO  [main] DefaultModuleDeployer -| [DUBBO] Dubbo Module[1.1.1] has started.
```

---

### 三、Provider 启动后的 Consumer 变化

当 Provider 启动完成后，Consumer 会收到服务上线通知：

<details>
<summary>📋 点击展开：Consumer 接收通知日志</summary>

```shell
|-INFO  [main-EventThread] ServiceInstancesChangedListener -| [DUBBO] Received instance notification, serviceName: dubbo-demo-api-provider, instances: 1
|-INFO  [main-EventThread] ServiceDiscoveryRegistryDirectory -| [DUBBO] Received invokers changed event. Service Key: org.apache.dubbo.api.demo.DemoService. Invokers Size: 1. Available Size: 1.
```

</details>

**关键变化：**

- `instances: 1`: 检测到 1 个可用的 Provider 实例
- `Invokers Size: 1`: 创建了 1 个调用器（Invoker）
- Consumer 现在可以发起远程调用了

---

### 四、服务调用流程

当用户在 Consumer 中触发调用时：

```java
String message = demoService.sayHello("dubbo");
```

**调用流程：**

```
Consumer                            Provider
   │                                   │
   ├─ 1. 代理拦截方法调用              │
   ├─ 2. 负载均衡选择一个 Provider     │
   ├─ 3. 序列化请求参数                │
   ├─ 4. 发送 RPC 请求 ───────────────>│
   │                                   ├─ 5. 反序列化请求
   │                                   ├─ 6. 调用实现类方法
   │                                   ├─ 7. 序列化响应结果
   │<──────────────────── 8. 返回结果 ─┤
   ├─ 9. 反序列化响应                  │
   └─ 10. 返回给调用者                 │
```

**调用日志（Provider 端）：**

```shell
|-INFO  [Dubbo-protocol-tri-thread-1] DemoServiceImpl -| Hello dubbo, request from consumer: 192.168.145.37:57365
```

**调用日志（Consumer 端）：**

```shell
|-INFO  [main] Application -| Hello dubbo, response from provider: 192.168.145.37:50051
```

---

### 五、核心机制总结

#### 1. 服务注册与发现

```
Provider                    Registry (ZooKeeper)              Consumer
   │                              │                              │
   ├── register ────────────────> │                              │
   │   (tri://ip:port/Service)    │                              │
   │                              │ <──── subscribe ─────────────┤
   │                              │   (Service)                  │
   │                              │                              │
   │                              ├── notify ───────────────────>│
   │                              │   (Provider list)            │
```

**关键点：**

- Provider 启动时注册服务 URL 到注册中心
- Consumer 启动时订阅服务，注册中心推送 Provider 列表
- Provider 上下线时，注册中心实时通知 Consumer

#### 2. 应用级服务发现（Dubbo 3.x）

Dubbo 3.x 引入了应用级服务发现，减少注册中心存储压力：

**Dubbo 2.x（接口级）：**
```
注册中心存储：
  /dubbo/org.apache.dubbo.api.demo.DemoService/providers
    ├─ tri://192.168.1.1:20880/DemoService
    └─ tri://192.168.1.2:20880/DemoService
```

**Dubbo 3.x（应用级）：**
```
注册中心存储：
  /services/dubbo-demo-api-provider
    ├─ 192.168.1.1:20880
    └─ 192.168.1.2:20880

映射关系（元数据中心）：
  DemoService -> dubbo-demo-api-provider
```

**优势：**

- ✅ 注册中心数据量减少 90% 以上
- ✅ 与 Spring Cloud、Kubernetes 等服务发现互通
- ✅ 支持大规模微服务场景

#### 3. 元数据管理

元数据包含服务的详细信息（方法签名、参数类型等），存储在元数据中心，避免通过注册中心传输：

```
Consumer 获取元数据的两种方式：
1. 从元数据中心查询（ZooKeeper）
2. 直接调用 Provider 的 MetadataService 获取
```

#### 4. 配置中心

Provider 和 Consumer 都会尝试从配置中心加载配置，支持动态配置下发：

```
配置优先级（从高到低）：
1. JVM 系统属性 (-D 参数)
2. 外部化配置（配置中心）
3. API/XML 配置
4. 默认值
```

---

## ⚠️ 异常处理

### 场景 1: Provider 正常下线

当 Provider 正常关闭（调用 shutdown 或 Ctrl+C）时：

1. Provider 从注册中心注销服务
2. 注册中心通知 Consumer 服务下线
3. Consumer 收到通知后移除该 Provider 实例

**Consumer 日志：**

```shell
|-INFO  [main-EventThread] ServiceInstancesChangedListener -| [DUBBO] Received instance notification, instances: 0
|-INFO  [main-EventThread] ServiceDiscoveryRegistryDirectory -| [DUBBO] Received invokers changed event. Invokers Size: 0. Available Size: 0.
```

**此时调用会报错：**

```shell
org.apache.dubbo.rpc.RpcException: Failed to invoke the method sayHello in the service org.apache.dubbo.api.demo.DemoService. No provider available for the service
```

---

### 场景 2: Provider 异常宕机

当 Provider 异常宕机（进程被 kill -9 或机器断电）时：

1. Provider 无法主动注销服务
2. ZooKeeper 等待 session 超时（默认 60 秒）后自动删除临时节点
3. 注册中心通知 Consumer 服务下线

**时间线：**

```
T+0s    Provider 宕机
T+0-60s Consumer 仍能看到 Provider，但调用失败（网络超时）
T+60s   ZooKeeper session 超时，删除节点
T+60s   Consumer 收到通知，移除 Provider
```

**调用异常：**

- 宕机后 60 秒内：网络连接超时（具体时间取决于 `timeout` 配置）
- 60 秒后：`No provider available for the service`

---

### 场景 3: 注册中心不可用

如果 ZooKeeper 宕机：

- **Provider**: 无法注册新服务，但已启动的服务继续提供
- **Consumer**: 无法订阅新服务，但已缓存的 Provider 列表仍可用

**Dubbo 的容错机制：**

```java
// Consumer 会将 Provider 列表缓存到本地文件
// 路径：~/.dubbo/dubbo-registry-{应用名}-{注册中心地址}.cache
```

即使注册中心宕机，Consumer 仍可使用缓存的 Provider 列表发起调用。

---

### 错误码说明

| 错误码 | 说明 | 解决方案 |
|-------|------|---------|
| `4-1` | 没有可用的服务提供者 | 检查 Provider 是否启动，网络是否连通 |
| `1-1` | 服务调用超时 | 增加 `timeout` 配置或优化 Provider 性能 |
| `2-1` | 序列化异常 | 检查参数类型是否匹配，是否实现 Serializable |
| `5-1` | 网络异常 | 检查网络连接，防火墙设置 |

**查看完整错误码列表：** https://dubbo.apache.org/faq/

---

## ❓ 常见问题

### Q1: Consumer 启动时报 "No provider available"

**原因：** 启动时检查 Provider 是否存在，但 Provider 还未启动。

**解决方案：**

```java
ConsumerConfig consumerConfig = new ConsumerConfig();
consumerConfig.setCheck(false);  // 关闭启动检查
```

或者先启动 Provider，再启动 Consumer。

---

### Q2: 如何指定固定端口？

**默认配置：** `-1` 表示随机端口

**指定固定端口：**

```java
// Provider 端
new ProtocolConfig(CommonConstants.TRIPLE, 20880);  // 固定端口 20880
```

---

### Q3: 如何启用泛化调用？

**方法 1: 启用 generic 模式**

```java
reference.setGeneric("true");
```

**方法 2: 直接转换为 GenericService**

```java
GenericService genericService = (GenericService) demoService;
Object result = genericService.$invoke("sayHello",
    new String[] {String.class.getName()},
    new Object[] {"dubbo"});
```

---

### Q4: 为什么使用 Triple 协议而不是 Dubbo 协议？

**Triple 协议的优势：**

- 基于 HTTP/2，穿透性更好（防火墙、网关友好）
- 兼容 gRPC，支持跨语言互通
- 支持流式调用（Server Stream、Client Stream、Bidirectional Stream）
- Dubbo 3.x 官方推荐的默认协议

**切换回 Dubbo 协议：**

```java
new ProtocolConfig(CommonConstants.DUBBO, 20880);
```

---

### Q5: Consumer 和 Provider 如何共享接口定义？

**方案 1: Maven 依赖（推荐）**

将接口定义打包成独立的 JAR，Provider 和 Consumer 都依赖该 JAR：

```xml
<!-- Provider 和 Consumer 的 pom.xml -->
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-demo-api-interface</artifactId>
    <version>${revision}</version>
</dependency>
```

**方案 2: 泛化调用**

Consumer 不依赖接口 JAR，使用 `GenericService` 调用（适用于网关、测试平台）。

---

### Q6: 如何查看注册中心的服务列表？

**方法 1: [使用 Dubbo Admin](https://github.com/apache/dubbo-admin)**


**方法 2: 直接查看 ZooKeeper**

```bash
# 连接 ZooKeeper
zkCli.sh -server 127.0.0.1:2181

# 查看服务列表
ls /services
ls /dubbo
```

---

### Q7: 如何调试 Dubbo 源码？

1. Clone Dubbo 源码：`git clone https://github.com/apache/dubbo.git`
2. 在 IDEA 中导入 Dubbo 项目
3. 在 `dubbo-demo-api-consumer` 或 `dubbo-demo-api-provider` 中打断点
4. 使用 Debug 模式启动，可以跟踪 Dubbo 内部调用流程

**关键调试点：**

- 服务引用创建：`ReferenceConfig#get()`
- 服务调用：`InvokerInvocationHandler#invoke()`
- 负载均衡：`AbstractClusterInvoker#invoke()`
- 网络通信：`NettyClient#doConnect()`

---

## 🎓 进阶学习：源码深度解析

完成快速开始后，如果你想深入理解 Dubbo 的内部机制，我们准备了一系列源码分析文档：

### 📚 源码分析系列文档

| 篇目 | 主题 | 难度 | 阅读时间 |
|------|------|------|---------|
| **[第一篇](./docs/01-dynamic-proxy.md)** | 动态代理机制 | ⭐⭐⭐⭐ | 30 分钟 |
| **[第二篇](./docs/02-reference-config.md)** | 服务引用配置原理 | ⭐⭐⭐⭐⭐ | 40 分钟 |
| **[第三篇](./docs/03-cluster-invoke.md)** | 集群容错与负载均衡 | ⭐⭐⭐⭐⭐ | 45 分钟 |
| **[第四篇](./docs/04-protocol-analysis.md)** | Triple 协议深度解析 | ⭐⭐⭐⭐⭐ | 50 分钟 |

**🔗 [查看完整文档导航 →](./docs/README.md)**

### 你将学到什么？

通过阅读源码分析系列，你将深入理解：

- 🎭 **代理机制**：Dubbo 如何使用 Javassist 生成代理类
- 🔍 **服务发现**：从注册中心订阅到 Invoker 创建的完整流程
- 🛡️ **容错策略**：六种集群容错策略和四种负载均衡算法
- 🌐 **Triple 协议**：基于 HTTP/2 的 RPC 协议设计与实现

### 快速导航

**想了解特定主题？**

- 💡 代理对象是如何创建的？→ [第一篇：动态代理机制](./docs/01-dynamic-proxy.md)
- 💡 服务引用的完整流程？→ [第二篇：服务引用配置原理](./docs/02-reference-config.md)
- 💡 如何选择容错和负载均衡策略？→ [第三篇：集群容错与负载均衡](./docs/03-cluster-invoke.md)
- 💡 Triple 协议如何工作？→ [第四篇：Triple 协议深度解析](./docs/04-protocol-analysis.md)

---

## 🔗 相关资源

### 官方文档

- [Apache Dubbo 官网](https://dubbo.apache.org/)
- [Dubbo 3.x 官方文档](https://dubbo.apache.org/zh-cn/overview/what/overview/)
- [Dubbo GitHub 仓库](https://github.com/apache/dubbo)
- [Dubbo Samples 示例集](https://github.com/apache/dubbo-samples)

### 进阶学习

- [Dubbo 源码解析](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/reference-manual/architecture/)
- [Triple 协议详解](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/reference-manual/protocol/triple/)
- [应用级服务发现](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/upgrades-and-compatibility/service-discovery/)
- [Dubbo Admin 管理控制台](https://github.com/apache/dubbo-admin)

### 社区交流

- [Dubbo 钉钉群](https://dubbo.apache.org/zh-cn/contact/)
- [GitHub Issues](https://github.com/apache/dubbo/issues)
- [Stack Overflow (dubbo 标签)](https://stackoverflow.com/questions/tagged/dubbo)

---

## 📄 许可证

本项目基于 [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0) 开源协议。
