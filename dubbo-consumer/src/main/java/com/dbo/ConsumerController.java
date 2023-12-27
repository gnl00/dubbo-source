package com.dbo;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    @DubboReference
    private DemoApi dubboApi;

    @GetMapping("/hello")
    public String sayHello(@RequestParam String name) {
        return dubboApi.sayHello(name);
    }

}
