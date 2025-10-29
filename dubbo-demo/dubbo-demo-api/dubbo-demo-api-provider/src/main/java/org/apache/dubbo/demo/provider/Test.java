package org.apache.dubbo.demo.provider;

import org.apache.dubbo.api.demo.DemoService;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.RegisterTypeEnum;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ArgumentConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;

import java.util.Collections;

public class Test {

    public static void main(String[] args) {
        startManual();
    }

    private static void multiInstances() {
        ApplicationConfig applicationConfig = new ApplicationConfig("app1");
        applicationConfig.setRegisterMode(CommonConstants.INSTANCE_REGISTER_MODE); // 应用实例级别注册

        ServiceConfig<DemoService> serviceConfig = new ServiceConfig<>();
        serviceConfig.setInterface(DemoService.class);
        serviceConfig.setTimeout(20 * 1000);
        serviceConfig.setRef(new DemoServiceImpl());
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        registryConfig.setProtocol("dubbo");
        serviceConfig.setRegistry(registryConfig);

        DubboBootstrap.newInstance()
                .application(applicationConfig)
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                .protocol(new ProtocolConfig("dubbo", 20808))
                .service(serviceConfig)
                .start();

        ApplicationConfig applicationConfig2 = new ApplicationConfig("app2");
        applicationConfig2.setRegisterMode(CommonConstants.INSTANCE_REGISTER_MODE); // 应用实例级别注册
        DubboBootstrap.newInstance()
                .application(applicationConfig2)
                .registry(new RegistryConfig("zookeeper://127.0.0.1:2181"))
                .protocol(new ProtocolConfig("dubbo", 20808))
                .service(serviceConfig)
                .start();
    }

    public static void startManual() {
        ServiceConfig<DemoService> serviceConfig = new ServiceConfig<>();
        serviceConfig.setInterface(DemoService.class);
        serviceConfig.setTimeout(20 * 1000);
        serviceConfig.setRef(new DemoServiceImpl());
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        registryConfig.setProtocol("dubbo");
        registryConfig.setRegisterMode(CommonConstants.INSTANCE_REGISTER_MODE);
        serviceConfig.setRegistry(registryConfig);

        serviceConfig.export();
    }
}
