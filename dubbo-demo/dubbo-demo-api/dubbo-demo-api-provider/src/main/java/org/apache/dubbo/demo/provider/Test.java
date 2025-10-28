package org.apache.dubbo.demo.provider;

import org.apache.dubbo.api.demo.DemoService;
import org.apache.dubbo.common.constants.RegisterTypeEnum;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ArgumentConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ModuleModel;

import java.util.Collections;

public class Test {

    public static void main(String[] args) {
        FrameworkModel frameworkModel = FrameworkModel.defaultModel();
        ApplicationModel applicationModel = frameworkModel.defaultApplication();
        ModuleModel moduleModel = applicationModel.newModule();

        ConfigManager configManager = new ConfigManager(applicationModel);

        ApplicationConfig applicationConfig = new ApplicationConfig("provider-app");
        applicationConfig.setProtocol("dubbo");
        applicationConfig.setName("provider-app");

        // configManager.addConfig(applicationConfig);
        // configManager.addProtocol(new ProtocolConfig("dubbo"));
        // applicationModel.setConfigManager(configManager);

        ServiceConfig<DemoService> serviceConfig = new ServiceConfig<>();
        serviceConfig.setInterface(DemoService.class);
        serviceConfig.setTimeout(20 * 1000);
        serviceConfig.setRef(new DemoServiceImpl());
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        registryConfig.setProtocol("dubbo");
        serviceConfig.setRegistry(registryConfig);
        serviceConfig.setApplication(applicationConfig);

        ArgumentConfig argument = new ArgumentConfig();
        argument.setIndex(0);
        argument.setCallback(false);

        MethodConfig method = new MethodConfig();
        method.setName("echo");
        method.setArguments(Collections.singletonList(argument));

        serviceConfig.setMethods(Collections.singletonList(method));

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setProtocol(new ProtocolConfig("dubbo"));
        providerConfig.setExport(true);

        serviceConfig.setProvider(providerConfig);

        // moduleModel.getConfigManager().addService(serviceConfig);

        serviceConfig.export(RegisterTypeEnum.AUTO_REGISTER);
    }
}
