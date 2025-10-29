# Dubbo 源码分析：动态代理机制

---

## 为什么要了解 Dubbo 代理机制？

在使用 Dubbo 进行 RPC 调用时，我们的代码看起来就像在调用本地方法：

```java
DemoService demoService = ...;
String result = demoService.sayHello("dubbo");  // 看似本地调用
```

但实际上，这行代码背后发生了复杂的远程调用过程：

```
本地方法调用 → 代理拦截 → 序列化 → 网络传输 → 反序列化 → 远程执行 → 结果返回
```

**核心问题：**

1. `demoService` 这个对象到底是什么？
2. 它是如何被创建出来的？
3. 它如何将方法调用转换为远程调用？

**目标：**

- 🎯 理解 Dubbo 调用链路的起点
- 🎯 掌握动态代理在 RPC 框架中的应用

---

## 代理类在调用链中的位置

理解代理类在整个调用链中的位置非常重要：

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Consumer 调用链路                           │
├─────────────┬──────────────┬──────────────┬──────────────┬──────────┤
│  用户代码    │   代理对象    │   集群容错    │   负载均衡    │  网络通信 │
│ (Interface) │   (Proxy)    │  (Cluster)   │  (LoadBalance)│ (Protocol)│
│             │              │              │              │          │
│ sayHello()  │─>invoke()    │─>select()    │─>choose()    │─>send()  │
└─────────────┴──────────────┴──────────────┴──────────────┴──────────┘
                     ↑
                  本文关注点
```

**代理层的职责：**

- **透明化调用**：让远程调用看起来像本地调用
- **方法拦截**：拦截所有接口方法的调用
- **参数收集**：收集方法名、参数类型、参数值
- **调用转发**：将调用转发给 Dubbo 的 Invoker 链

---

## 源码分析

### 代理对象的获取

回到最简单的消费者 API 调用：

```java
public class Application {
    private static final DubboBootstrap bootstrap = DubboBootstrap.getInstance();
    private static final ReferenceConfig<DemoService> reference = new ReferenceConfig<>();

    private static void runWithBootstrap() {
        reference.setInterface(DemoService.class);

        ConsumerConfig consumerConfig = new ConsumerConfig();
        consumerConfig.setCheck(false);
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
            .registry(new RegistryConfig(REGISTRY_URL))
            .protocol(new ProtocolConfig(CommonConstants.TRIPLE, -1))
            .reference(reference)
            .consumer(consumerConfig)
            .start();
    }

    private static void execRemoteGenericCall() {
        DemoService demoService = bootstrap.getCache().get(reference);
        // normal invoke
        String invokeResult = demoService.sayHello("dubbo");
        logger.info(invokeResult);

        // generic invoke
        /*
        reference.setGeneric("true");
        GenericService genericService = (GenericService) demoService;
        Object genericInvokeResult = genericService.$invoke(
                "sayHello", new String[] {String.class.getName()}, new Object[] {"dubbo generic invoke"});
        logger.info(genericInvokeResult.toString());
        */
    }
}
```

**在 `String message = demoService.sayHello("dubbo");` 这一行打上断点**，运行后可以看到 `demoService` 对象的实际类型是：

```
DemoServiceDubboProxy0
```

这是 Dubbo 在运行时动态生成的代理类！

### 代理类的创建时机

在 Dubbo 源码中全文搜索 "DubboProxy"，可以找到它出现在：

```java
org.apache.dubbo.common.bytecode.Proxy.buildProxyClass
```

在这个方法上打断点，查看完整的调用栈：

```
buildProxyClass:174, Proxy (org.apache.dubbo.common.bytecode)
  ↑
getProxy:87, Proxy (org.apache.dubbo.common.bytecode)
  ↑
getProxy:46, JavassistProxyFactory (org.apache.dubbo.rpc.proxy.javassist)
  ↑
getProxy:99, AbstractProxyFactory (org.apache.dubbo.rpc.proxy)
  ↑
getProxy:65, StubProxyFactoryWrapper (org.apache.dubbo.rpc.proxy.wrapper)
  ↑
getProxy:-1, ProxyFactory$Adaptive (org.apache.dubbo.rpc)
  ↑
createProxy:522, ReferenceConfig (org.apache.dubbo.config)
  ↑
init:383, ReferenceConfig (org.apache.dubbo.config)
  ↑
get:244, ReferenceConfig (org.apache.dubbo.config)
  ↑
get:140, SimpleReferenceCache (org.apache.dubbo.config.utils)
  ↑
lambda$referServices$6:567, DefaultModuleDeployer (org.apache.dubbo.config.deploy)
  ↑
referServices:539, DefaultModuleDeployer (org.apache.dubbo.config.deploy)
  ↑
startSync:186, DefaultModuleDeployer (org.apache.dubbo.config.deploy)
  ↑
start:159, DefaultModuleDeployer (org.apache.dubbo.config.deploy)
  ↑
startModules:771, DefaultApplicationDeployer (org.apache.dubbo.config.deploy)
  ↑
start:708, DefaultApplicationDeployer (org.apache.dubbo.config.deploy)
  ↑
start:230, DubboBootstrap (org.apache.dubbo.config.bootstrap)
  ↑
runWithBootstrap:67, Application (org.apache.dubbo.demo.consumer)
  ↑
main:44, Application (org.apache.dubbo.demo.consumer)
```

**关键发现：**

1. Dubbo 使用 **Javassist** 来创建代理类（而非 JDK 动态代理）
2. 代理类在 `ReferenceConfig.init()` 时创建（即调用 `bootstrap.start()` 时）
3. 代理类的创建发生在 Consumer 启动阶段，而非每次调用时

---

**相关类图**

```
┌─────────────────────────────────────────────┐
│            ProxyFactory (SPI)               │
│  + getProxy(Invoker<T>): T                  │
└───────────────┬─────────────────────────────┘
                │
        ┌───────┴────────┐
        │                │
┌───────▼──────────┐  ┌──▼──────────────────┐
│ JdkProxyFactory  │  │ JavassistProxyFactory│
└──────────────────┘  └──────────────────────┘
                               │
                      ┌────────▼─────────┐
                      │      Proxy       │
                      │ + buildProxyClass│
                      └──────────────────┘
```

---

**时序图：**

```sequence
    participant User as 用户代码
    participant Bootstrap as DubboBootstrap
    participant RefConfig as ReferenceConfig
    participant ProxyFactory as ProxyFactory
    participant Proxy as Proxy (Javassist)

    User->>Bootstrap: start()
    Bootstrap->>RefConfig: init()
    RefConfig->>ProxyFactory: getProxy()
    ProxyFactory->>Proxy: buildProxyClass()
    Proxy->>Proxy: 使用 Javassist 生成字节码
    Proxy-->>RefConfig: DemoServiceDubboProxy0
    RefConfig-->>User: 代理对象已创建
```

---

### Javassist 字节码生成详解

让我们深入查看 `org.apache.dubbo.common.bytecode.Proxy.buildProxyClass` 方法的核心逻辑：

```java
private static Class<?> buildProxyClass(ClassLoader cl, Class<?>[] ics, ProtectionDomain domain) {
    ClassGenerator ccp = null;
    try {
        // 1. 创建类生成器
        ccp = ClassGenerator.newInstance(cl);

        Set<String> worked = new HashSet<>();
        List<Method> methods = new ArrayList<>();

        // 2. 获取接口的包名和类名
        String pkg = ics[0].getPackage().getName();
        Class<?> neighbor = ics[0];

        // 3. 遍历所有接口，添加到代理类
        for (Class<?> ic : ics) {
            String npkg = ic.getPackage().getName();
            if (!Modifier.isPublic(ic.getModifiers())) {
                if (!pkg.equals(npkg)) {
                    throw new IllegalArgumentException("non-public interfaces from different packages");
                }
            }

            // 让代理类实现该接口
            ccp.addInterface(ic);

            // 4. 遍历接口的所有方法
            for (Method method : ic.getMethods()) {
                String desc = ReflectUtils.getDesc(method);

                // 跳过已处理的方法和静态方法
                if (worked.contains(desc) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                worked.add(desc);

                int ix = methods.size();
                Class<?> rt = method.getReturnType();
                Class<?>[] pts = method.getParameterTypes();

                // 5. 生成方法体代码
                StringBuilder code = new StringBuilder("Object[] args = new Object[")
                        .append(pts.length)
                        .append("];");

                // 将方法参数放入 args 数组
                for (int j = 0; j < pts.length; j++) {
                    code.append(" args[")
                            .append(j)
                            .append("] = ($w)$")  // $w 表示包装类型，$1, $2 表示方法参数
                            .append(j + 1)
                            .append(';');
                }

                // 调用 InvocationHandler
                code.append(" Object ret = handler.invoke(this, methods[")
                        .append(ix)
                        .append("], args);");

                // 处理返回值
                if (!Void.TYPE.equals(rt)) {
                    code.append(" return ").append(asArgument(rt, "ret")).append(';');
                }

                methods.add(method);

                // 6. 添加方法到代理类
                ccp.addMethod(
                        method.getName(),
                        method.getModifiers(),
                        rt,
                        pts,
                        method.getExceptionTypes(),
                        code.toString());
            }
        }

        // 7. 创建 ProxyInstance 类
        // 上面看到的 `DemoServiceDubboProxy0` 代理类名，就是在这里组装的
        String pcn = neighbor.getName() + "DubboProxy" + PROXY_CLASS_COUNTER.getAndIncrement();
        ccp.setClassName(pcn);

        // 添加字段：methods 数组
        ccp.addField("public static java.lang.reflect.Method[] methods;");

        // 添加字段：InvocationHandler
        ccp.addField("private " + InvocationHandler.class.getName() + " handler;");

        // 添加构造函数
        ccp.addConstructor(
                Modifier.PUBLIC,
                new Class<?>[] {InvocationHandler.class},
                new Class<?>[0],
                "handler=$1;");
        ccp.addDefaultConstructor();

        // 8. 生成字节码并加载类
        Class<?> clazz = ccp.toClass(neighbor, cl, domain);
        clazz.getField("methods").set(null, methods.toArray(new Method[0]));
        return clazz;
    } finally {
        // release ClassGenerator
        if (ccp != null) {
            ccp.release();
        }
    }
}
```

**代码解析：**

| 步骤 | 说明 | 示例 |
|------|------|------|
| **步骤 1-2** | 创建类生成器，获取接口信息 | `ClassGenerator.newInstance()` |
| **步骤 3** | 让代理类实现目标接口 | `implements DemoService` |
| **步骤 4** | 遍历接口的所有方法 | `sayHello()`, `sayHelloAsync()` |
| **步骤 5** | 生成方法体（核心！） | 将参数打包，调用 `handler.invoke()` |
| **步骤 6** | 将方法添加到代理类 | `addMethod()` |
| **步骤 7** | 设置类名、字段、构造函数 | `DemoServiceDubboProxy0` |
| **步骤 8** | 生成字节码并加载 | `ccp.toClass()` |

### 生成的代理类结构

最终生成的代理类结构大概如下：

```java
public class DemoServiceDubboProxy0 implements DemoService {

    // 静态字段：存储所有方法的 Method 对象
    public static Method[] methods;

    // 实例字段：InvocationHandler（真正处理调用的对象）
    private InvocationHandler handler;

    // 构造函数
    public DemoServiceDubboProxy0(InvocationHandler handler) {
        this.handler = handler;
    }

    // 实现接口方法：sayHello
    @Override
    public String sayHello(String name) {
        // 1. 将参数打包成 Object 数组
        Object[] args = new Object[1];
        args[0] = name;

        // 2. 调用 InvocationHandler.invoke()
        Object ret = handler.invoke(this, methods[0], args);

        // 3. 返回结果（需要类型转换）
        return (String) ret;
    }

    // 实现接口方法：sayHelloAsync
    @Override
    public CompletableFuture<String> sayHelloAsync(String name) {
        Object[] args = new Object[1];
        args[0] = name;
        Object ret = handler.invoke(this, methods[1], args);
        return (CompletableFuture<String>) ret;
    }
}
```

---

代理类的模板代码是在 `org.apache.dubbo.common.bytecode.Wrapper.makeWrapper` 方法中组装的。

> 如果想看生成的代码到底是什么，可以借助 [arthas](https://github.com/alibaba/arthas) 工具，将 `DemoServiceDubboProxy0` 类内容 dump 下来，然后使用 `jad` 命令查看字节码（或者直接拖到 Idea 中查看）

---

**关键点：**

- 代理类实现了 `DemoService` 接口
- 所有方法调用都转发给 `InvocationHandler`
- `InvocationHandler` 才是真正处理 RPC 调用的地方

**InvocationHandler 从哪来？**

可以看到 `org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory.getProxy`

```java
public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
    try {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    } catch (Throwable fromJavassist) {
        // try fall back to JDK proxy factory
    }
}
```

在 Dubbo 中，这个 `InvocationHandler` 是 `InvokerInvocationHandler`，它内部持有 `Invoker` 对象，负责：

1. 选择合适的 Provider（负载均衡）
2. 序列化参数
3. 发起网络调用
4. 反序列化结果

---

## 总结

1. **为什么使用动态代理？**
   - 让 RPC 调用对用户透明，像调用本地方法一样简单
   - 避免手动编写大量的网络调用代码

2. **为什么选择 Javassist 而非 JDK Proxy？**

   | 特性 | JDK Proxy | Javassist | Dubbo 选择 |
   |------|-----------|-----------|-----------|
   | **性能** | 较低（反射调用） | 高（直接调用） | Javassist |
   | **灵活性** | 只能代理接口 | 可代理类和接口 | Javassist |
   | **生成速度** | 快 | 稍慢 | 可接受 |
   | **字节码控制** | 无法控制 | 完全控制 | Javassist |

3. **代理类何时创建？**
   - Consumer 启动时（`DubboBootstrap.start()`）
   - 调用 `ReferenceConfig.init()` 时
   - **不是**每次调用时动态创建

4. **代理类的作用？**
   - 拦截接口方法调用
   - 收集方法名、参数类型、参数值
   - 转发给 `InvocationHandler` 处理

5. **调用链路总结：**
   ```
   用户代码调用接口方法
     ↓
   代理对象拦截（DemoServiceDubboProxy0）
     ↓
   InvocationHandler 处理（InvokerInvocationHandler）
     ↓
   Invoker 执行（集群容错、负载均衡等）
     ↓
   网络通信（Protocol、Exchange、Transport）
     ↓
   Provider 执行并返回结果
   ```

---

## 延伸阅读

### 相关技术对比

- **Java 动态代理技术对比**：JDK Proxy vs CGLIB vs Javassist vs ByteBuddy
- **RPC 框架代理实现对比**：Dubbo vs gRPC vs Spring Cloud OpenFeign
- **字节码操作框架**：ASM vs Javassist vs ByteBuddy

### 官方文档

- [Dubbo 服务引用](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/reference-manual/config/api/)
- [Dubbo 代理工厂](https://dubbo.apache.org/zh-cn/overview/mannual/java-sdk/reference-manual/spi/description/proxy-factory/)
- [Javassist 官方文档](https://www.javassist.org/)
