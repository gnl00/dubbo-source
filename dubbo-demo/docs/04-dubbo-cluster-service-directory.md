# dubbo cluster: service directory

Dubbo 集群容错包含四个部分，分别是服务目录 Directory、服务路由 Router、集群 Cluster 和负载均衡 LoadBalance。 先来了解一下服务目录是什么。

---

## 服务目录

服务目录中存储了一些和服务提供者有关的信息，通过服务目录，服务消费者可获取到服务提供者的信息，比如 ip、端口、服务协议等。通过这些信息，服务消费者就可通过 Netty 等客户端进行远程调用。

在一个服务集群中，服务提供者数量并不是一成不变的，如果集群中新增了一台机器，相应地在服务目录中就要新增一条服务提供者记录。或者，如果服务提供者的配置修改了，服务目录中的记录也要做相应的更新。

如果这样说，服务目录和注册中心的功能不就雷同了吗？确实如此，这里这么说是为了方便大家理解。实际上服务目录在获取注册中心的服务配置信息后，会为每条配置信息生成一个 Invoker 对象，并把这个 Invoker 对象存储起来，这个 Invoker 才是服务目录最终持有的对象。

Invoker 有什么用呢？看名字就知道了，这是一个具有远程调用功能的对象。讲到这大家应该知道了什么是服务目录了，它可以看做是 Invoker 集合，且这个集合中的元素会随注册中心的变化而进行动态调整。

---

### 继承关系

Directory 作为顶层接口，子类继承关系如下：

```
Directory (org.apache.dubbo.rpc.cluster)
++AbstractDirectory (org.apache.dubbo.rpc.cluster.directory)
++++StaticDirectory (org.apache.dubbo.rpc.cluster.directory)
++++DynamicDirectory (org.apache.dubbo.registry.integration)
++++++ServiceDiscoveryRegistryDirectory (org.apache.dubbo.registry.client)
++++++RegistryDirectory (org.apache.dubbo.registry.integration)
```

### AbstractDirectory

AbstractDirectory 封装了 Invoker 列举流程，具体的列举逻辑则由子类实现，这是典型的模板模式。我们只需要关注 `org.apache.dubbo.rpc.cluster.directory.AbstractDirectory.list` 方法

```java
// org.apache.dubbo.rpc.cluster.Directory.list
/**
 * list invokers.
 * filtered by invocation
 *
 * @return invokers
 */
List<Invoker<T>> list(Invocation invocation) throws RpcException;
```

```java
// org.apache.dubbo.rpc.cluster.directory.AbstractDirectory.list
public List<Invoker<T>> list(Invocation invocation) throws RpcException {
    BitList<Invoker<T>> availableInvokers;
    SingleRouterChain<T> singleChain = null;
    try {
        try {
            if (routerChain != null) {
                routerChain.getLock().readLock().lock();
            }
            // use clone to avoid being modified at doList().
            if (invokersInitialized) {
                availableInvokers = validInvokers.clone();
            } else {
                availableInvokers = invokers.clone();
            }
            if (routerChain != null) {
                singleChain = routerChain.getSingleChain(getConsumerUrl(), availableInvokers, invocation);
                singleChain.getLock().readLock().lock();
            }
        } finally {
            if (routerChain != null) {
                routerChain.getLock().readLock().unlock();
            }
        }
        // 调用 doList 方法列举 Invoker，doList 是模板方法，由子类实现
        List<Invoker<T>> routedResult = doList(singleChain, availableInvokers, invocation);
        if (routedResult.isEmpty()) {
            // 2-2 - No provider available.
        }
        return Collections.unmodifiableList(routedResult);
    } finally {
        if (singleChain != null) {
            singleChain.getLock().readLock().unlock();
        }
    }
}
```

这个方法对 Invoker 进行了一系列封装，doList 是模板方法，需由子类实现。接下来看子类 `org.apache.dubbo.registry.integration.RegistryDirectory`

---

### RegistryDirectory

RegistryDirectory 是一种动态服务目录，实现了 NotifyListener 接口。当注册中心服务配置发生变化后，RegistryDirectory 可收到与当前服务相关的变化。
收到变更通知后，RegistryDirectory 可根据配置变更信息刷新 Invoker 列表。

RegistryDirectory 中有几个比较重要的逻辑：
- Invoker 列表的刷新逻辑
- 接收服务配置变更的逻辑

`
org.apache.dubbo.registry.integration.RegistryDirectory.refreshInvoker
`是保证 RegistryDirectory 随注册中心变化而变化的关键所在。

```java
/**
 * Convert the invokerURL list to the Invoker Map. The rules of the conversion are as follows:
 * <ol>
 * <li> If URL has been converted to invoker, it is no longer re-referenced and obtained directly from the cache,
 * and notice that any parameter changes in the URL will be re-referenced.</li>
 * <li>If the incoming invoker list is not empty, it means that it is the latest invoker list.</li>
 * <li>If the list of incoming invokerUrl is empty, It means that the rule is only a override rule or a route
 * rule, which needs to be re-contrasted to decide whether to re-reference.</li>
 * </ol>
 * @param invokerUrls this parameter can't be null
 */
private void refreshInvoker(List<URL> invokerUrls) {
    // invokerUrls 仅有一个元素，且 url 协议头为 empty，此时表示禁用所有服务
    if (invokerUrls.size() == 1 && invokerUrls.get(0) != null && EMPTY_PROTOCOL.equals(invokerUrls.get(0).getProtocol())) {
        refreshRouter(BitList.emptyList(), () -> this.forbidden = true); // Forbid to access
        destroyAllInvokers(); // Close all invokers
    } else {
        this.forbidden = false; // Allow to access
        if (invokerUrls == Collections.<URL>emptyList()) invokerUrls = new ArrayList<>();
        // use local reference to avoid NPE as this.cachedInvokerUrls will be set null by destroyAllInvokers().
        Set<URL> localCachedInvokerUrls = this.cachedInvokerUrls;
        if (invokerUrls.isEmpty()) {
            if (CollectionUtils.isNotEmpty(localCachedInvokerUrls)) {
                // 1-4 Empty address.
                invokerUrls.addAll(localCachedInvokerUrls);
            }
        } else {
            localCachedInvokerUrls = new HashSet<>();
            localCachedInvokerUrls.addAll(invokerUrls); // Cached invoker urls, convenient for comparison
            this.cachedInvokerUrls = localCachedInvokerUrls;
        }
        if (invokerUrls.isEmpty()) return;
        int originSize = invokerUrls.size();
        invokerUrls = invokerUrls.stream().distinct().collect(Collectors.toList());
        if (invokerUrls.size() != originSize) {
            // Received duplicated invoker urls changed event from registry.
        }
        // use local reference to avoid NPE as this.urlInvokerMap will be set null concurrently at
        // destroyAllInvokers().
        Map<URL, Invoker<T>> localUrlInvokerMap = this.urlInvokerMap;
        // can't use local reference as oldUrlInvokerMap's mappings might be removed directly at toInvokers().
        Map<URL, Invoker<T>> oldUrlInvokerMap = null;
        if (localUrlInvokerMap != null) {
            // the initial capacity should be set greater than the maximum number of entries divided by the load
            // factor to avoid resizing.
            oldUrlInvokerMap =
                    new LinkedHashMap<>(Math.round(1 + localUrlInvokerMap.size() / DEFAULT_HASHMAP_LOAD_FACTOR));
            localUrlInvokerMap.forEach(oldUrlInvokerMap::put);
        }
        // 将 url 转成 Invoker
        Map<URL, Invoker<T>> newUrlInvokerMap = toInvokers(oldUrlInvokerMap, invokerUrls); // Translate url list to Invoker map
        /*
         * If the calculation is wrong, it is not processed.
         *
         * 1. The protocol configured by the client is inconsistent with the protocol of the server.
         *    eg: consumer protocol = dubbo, provider only has other protocol services(rest).
         * 2. The registration center is not robust and pushes illegal specification data.
         *
         */
        if (CollectionUtils.isEmptyMap(newUrlInvokerMap)) {
            // 3-1 - Failed to convert the URL address into Invokers.
            // inconsistency between the client protocol and server protocol
            return;
        }
        List<Invoker<T>> newInvokers = Collections.unmodifiableList(new ArrayList<>(newUrlInvokerMap.values()));
        BitList<Invoker<T>> finalInvokers = multiGroup ? new BitList<>(toMergeInvokerList(newInvokers)) : new BitList<>(newInvokers);
        // pre-route and build cache
        refreshRouter(finalInvokers.clone(), () -> this.setInvokers(finalInvokers));
        this.urlInvokerMap = newUrlInvokerMap;
        try {
            // 销毁无用 Invoker
            destroyUnusedInvokers(oldUrlInvokerMap, newUrlInvokerMap); // Close the unused Invoker
        } catch (Exception e) {}
        // notify invokers refreshed
        this.invokersChanged();
    }
}
```

refreshInvoker 方法首先会根据入参 invokerUrls 的数量和协议头判断是否禁用所有的服务，如果禁用，则将 forbidden 设为 true，并销毁所有的 Invoker。

toInvokers 方法一开始会对服务提供者 url 进行检测，若服务消费端的配置不支持服务端的协议，或服务端 url 协议头为 empty 时，toInvokers 均会忽略服务提供方 url。

必要的检测做完后，紧接着是合并 url，然后访问缓存，尝试获取与 url 对应的 invoker。如果缓存命中，直接将 Invoker 存入 newUrlInvokerMap 中即可。如果未命中，则需新建 Invoker。

当新的 Invoker 列表生成后，还要一个重要的工作要做，就是销毁无用的 Invoker，避免服务消费者调用已下线的服务的服务。


## 参考

[Dubbo 服务目录](https://cn.dubbo.apache.org/zh-cn/docsv2.7/dev/source/directory/)
