# dubbo-deployer

使用 API 来启动 Dubbo 服务的时候需要从 `org.apache.dubbo.config.bootstrap.DubboBootstrap` 入手。

在 Debug `DubboBootstrap` 启动流程的过程中，调用了 `org.apache.dubbo.config.bootstrap.DubboBootstrap.start(boolean)`：

```java
private final ApplicationDeployer applicationDeployer;
// ...
public DubboBootstrap start(boolean wait) {
    Future future = applicationDeployer.start();
    // ...
    return this;
}
```

在分析 Dubbo Framework/ApplicationModel/ModuleModel 领域模型的时候也能看到：

```java
public class ApplicationModel extends ScopeModel {
    // 应用程序部署器ApplicationDeployer实例对象deployer
    private volatile ApplicationDeployer deployer;
}

public class ModuleModel extends ScopeModel {
    private volatile ModuleDeployer deployer;
}
```

`DubboBootstrap` 借助 `Deployer` 来启动和发布服务。`Deployer` 的启动过程包括：启动配置中心，元数据中心，注册中心。

跟着 `org.apache.dubbo.config.bootstrap.DubboBootstrap.start(boolean)` 一路 debug 来到 `org.apache.dubbo.config.deploy.DefaultApplicationDeployer.start` 可以看到：

```java
@Override
public Future start() {
    synchronized (startLock) {
        if (isStopping() || isStopped() || isFailed()) {}

        try {
            // ...

            // pending -> starting : first start app
            // started -> starting : re-start app
            onStarting();

            initialize();

            startModules(); // == doStart()
        } catch (Throwable e) {
            onFailed(getIdentifier() + " start failure", e);
            throw e;
        }
        return startFuture;
    }
}
```

主要是做一些启动前的检查，`isStopping() || isStopped() || isFailed()`。

接下来进行初始化操作

```java
    @Override
    public void initialize() {
        if (initialized) return;
        synchronized (startLock) { // Ensure that the initialization is completed when concurrent calls
            if (initialized) return;
            onInitialize();
            // register shutdown hook
            registerShutdownHook();

            startConfigCenter(); // 创建服务本地的配置中心，实际上是一个全局共享的 ConfigManager 来存储全局配置信息
            loadApplicationConfigs(); // 读配置文件，往 ConfigManager 中塞配置

            initModuleDeployers();

            initMetricsReporter();
            initMetricsService();
            // @since 3.2.3
            initObservationRegistry(); // Metrics Observation

            // @since 2.7.8
            startMetadataCenter(); // 启动元数据中心

            initialized = true;
        }
    }
```

可以看到，服务本地的配置中心和元数据中心都在初始化中启动。

其他的一些部分就是初始化状态管理逻辑，接下来看到

`org.apache.dubbo.config.deploy.DefaultApplicationDeployer.startModules`

```java
private void startModules() { // 完成服务的注册与订阅
    // ensure init and start internal module first
    prepareInternalModule();

    // filter and start pending modules, ignore new module during starting, throw exception of module start
    for (ModuleModel moduleModel : applicationModel.getModuleModels()) {
        if (moduleModel.getDeployer().isPending()) {
            moduleModel.getDeployer().start();
        }
    }
}
```

在我们以 `DemoService` 启动的情况下，会有一个 InternalModule，从 `applicationModel.getModuleModels()` 中获取到的 `ModuleModel` 就有两个：

- Dubbo Module[1.1.0] == InternalModule
- Dubbo Module[1.1.1] == DemoServiceModule

接下来就是启动的重点方法 `org.apache.dubbo.config.deploy.DefaultModuleDeployer.startSync`

```java
private synchronized Future startSync() throws IllegalStateException {
    if (isStopping() || isStopped() || isFailed()) {
        throw new IllegalStateException(getIdentifier() + " is stopping or stopped, can not start again");
    }

    try {
        if (isStarting() || isStarted() || isCompletion()) {
            return startFuture;
        }

        onModuleStarting();

        initialize();

        // export services Provider 导出服务，注册到注册中心
        exportServices(); // 服务提供者重点关注这一步，将服务导出

        // prepare application instance
        // exclude internal module to avoid wait itself
        if (moduleModel != moduleModel.getApplicationModel().getInternalModule()) {
            applicationDeployer.prepareInternalModule();
        }

        // refer services Consumer 从注册中心订阅服务引用
        referServices(); // 服务消费者重点关注这一步，从注册中心获取远程调用服务引用

        // if no async export/refer services, just set started
        if (asyncExportingFutures.isEmpty() && asyncReferringFutures.isEmpty()) {
            // publish module started event
            onModuleStarted();
            // register services to registry
            registerServices();
            // check reference config
            checkReferences();
            // publish module completion event
            onModuleCompletion();
            // complete module start future after application state changed
            completeStartFuture(true);
        } else {
            frameworkExecutorRepository.getSharedExecutor().submit(() -> {
                try {
                    // wait for export finish
                    waitExportFinish();
                    // wait for refer finish
                    waitReferFinish();
                    // publish module started event
                    onModuleStarted();
                    // register services to registry
                    registerServices();
                    // check reference config
                    checkReferences();
                    // publish module completion event
                    onModuleCompletion();
                } catch (Throwable e) {
                    // wait for export/refer services occurred an exception
                    onModuleFailed(getIdentifier() + " start failed: " + e, e);
                } finally {
                    // complete module start future after application state changed
                    completeStartFuture(true);
                }
            });
        }
    } catch (Throwable e) {
        onModuleFailed(getIdentifier() + " start failed: " + e, e);
    }
    return startFuture;
}
```
