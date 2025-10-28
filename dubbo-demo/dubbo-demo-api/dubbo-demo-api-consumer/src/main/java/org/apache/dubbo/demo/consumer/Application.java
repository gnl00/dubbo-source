/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.api.demo.DemoService;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final String REGISTRY_URL = "zookeeper://127.0.0.1:2181";

    private static final DubboBootstrap bootstrap = DubboBootstrap.getInstance();

    private static final ReferenceConfig<DemoService> reference = new ReferenceConfig<>();

    public static void main(String[] args) {
        runWithBootstrap();
        System.out.println("### ==> Enter anything to process remote invoke <== ###");
        while (true) {
            Scanner sc = new Scanner(System.in);
            String s = sc.nextLine();
            if ("exit".equals(s)) {
                System.exit(0);
            } else {
                execRemoteGenericCall();
            }
        }
    }

    private static void runWithBootstrap() {
        reference.setInterface(DemoService.class);
        // reference.setGeneric("true");
        reference.setCheck(false);
        reference.setTimeout(20 * 1000);
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
                .registry(new RegistryConfig(REGISTRY_URL))
                .protocol(new ProtocolConfig(CommonConstants.TRIPLE, -1))
                .reference(reference)
                .start();
    }

    private static void execRemoteGenericCall() {
        DemoService demoService = bootstrap.getCache().get(reference);
        // normal invoke
        /*
        String invokeResult = demoService.sayHello("dubbo");
        logger.info(invokeResult);
        */

        // server-stream response based on Triple Protocol
        demoService.serverStream(new StreamObserver<String>() {
            @Override
            public void onNext(String data) {
                System.out.println("server-stream data ==> " + data);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("server-stream done");
            }
        });

        // client-stream based on Triple Protocol
        // NOTE 从 org.apache.dubbo.rpc.protocol.tri.TripleInvoker.doInvoke 源码中可以看到 CLIENT_STREAM 并未实现
        // 同时启动的时候报错 java.lang.IllegalStateException: Bad stream method signature. method(clientStream:) 可以猜测当前 3.3.6 版本未实现 client side stream
        /*
        new Thread(() -> {
            System.out.println("client-stream start~");
            StreamObserver<String> request = demoService.clientStream();
            for (int i = 100; i < 110; i++) {
                request.onNext("client-stream request" + i);
            }
            request.onCompleted();
        }).start();
        */

        // bi-stream based on Triple Protocol
        /*
        StreamObserver<String> request = demoService.biStream(new StreamObserver<String>() {
            @Override
            public void onNext(String data) {
                System.out.println("bi-stream data ==> " + data);
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("bi-stream error" + throwable.getCause());
            }

            @Override
            public void onCompleted() {
                System.out.println("bi-stream done");
            }
        });
        for (int i = 0; i < 10; i++) {
            request.onNext("stream request " + i);
        }
        request.onCompleted();
        */

        // generic invoke
        /*GenericService genericService = (GenericService) demoService;
        Object genericInvokeResult = genericService.$invoke(
                "sayHello", new String[] {String.class.getName()}, new Object[] {"dubbo generic invoke"});
        logger.info(genericInvokeResult.toString());*/
    }

}
