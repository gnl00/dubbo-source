# dubbo-source

## 前言

前段时间在看 netty 的时候就曾实现过自定义的 RPC 协议，翻看了一下 dubbo 底层依赖，发现也用到了 netty。
于是就想着看看 dubbo 的源码，看看 dubbo 是怎么实现的。

...

> 不得不说，dubbo 这套东西每次升级，按照上一次的逻辑就起不来了。怒了:angry:

...

**环境信息**

* JDK 11

* SpringBoot 2.7.14

* dubbo-spring-starter 2.7.14

* nacos 2.2.0

* nacos-discovery-spring-boot-starter 0.2.12

* com.google.guava 33.0.0-jre

  > dubbo 使用到了 `com.google.common.collect.Maps`

…

---

