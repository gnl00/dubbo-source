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
package org.apache.dubbo.demo.provider;

import org.apache.dubbo.api.demo.DemoService;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.rpc.RpcContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DemoServiceImpl implements DemoService {
    private static final Logger logger = LoggerFactory.getLogger(DemoServiceImpl.class);

    @Override
    public int echo(int i) {
        return i;
    }

    @Override
    public String sayHello(String name) {
        logger.info("Hello " + name + ", request from consumer: "
                + RpcContext.getServiceContext().getRemoteAddress());
        return "Hello " + name + ", response from provider: "
                + RpcContext.getServiceContext().getLocalAddress();
    }

    @Override
    public void serverStream(StreamObserver<String> response) {
        for (int i = 0; i < 10; i++) {
            response.onNext("server stream " + i);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        response.onCompleted();
    }

    /*@Override
    public StreamObserver<String> clientStream() {
        return new StreamObserver<String>() {
            @Override
            public void onNext(String data) {
                System.out.println("client-stream data: " + data);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("client-stream done");
            }
        };
    }*/

    @Override
    public StreamObserver<String> biStream(StreamObserver<String> response) {
        return new StreamObserver<String>() {
            @Override
            public void onNext(String data) {
                System.out.println("bi-stream data: " + data);
                response.onNext("hello," + data);
            }

            @Override
            public void onError(Throwable throwable) {
                response.onError(throwable);
            }

            @Override
            public void onCompleted() {
                response.onCompleted();
            }
        };
    }
}
