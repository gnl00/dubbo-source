package com.dbo;

import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class ProviderService implements DemoApi{
    @Override
    public String sayHello(String name) {
        return "say hello from provider: " + name;
    }
}
