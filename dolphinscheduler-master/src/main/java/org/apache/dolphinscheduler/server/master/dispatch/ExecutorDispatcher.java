/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.server.master.dispatch;

import org.apache.dolphinscheduler.remote.utils.Host;
import org.apache.dolphinscheduler.server.master.dispatch.context.ExecutionContext;
import org.apache.dolphinscheduler.server.master.dispatch.enums.ExecutorType;
import org.apache.dolphinscheduler.server.master.dispatch.exceptions.ExecuteException;
import org.apache.dolphinscheduler.server.master.dispatch.executor.ExecutorManager;
import org.apache.dolphinscheduler.server.master.dispatch.executor.NettyExecutorManager;
import org.apache.dolphinscheduler.server.master.dispatch.host.HostManager;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * executor dispatcher
 */
@Service
@Slf4j
public class ExecutorDispatcher implements InitializingBean {

    /**
     * netty executor manager
     */
    @Autowired
    private NettyExecutorManager nettyExecutorManager;

    /**
     * round robin host manager
     */
    @Autowired
    private HostManager hostManager;

    /**
     * executor manager
     */
    private final ConcurrentHashMap<ExecutorType, ExecutorManager<Boolean>> executorManagers;

    /**
     * constructor
     */
    public ExecutorDispatcher() {
        this.executorManagers = new ConcurrentHashMap<>();
    }

    /**
     * task dispatch
     *
     * @param context context
     * @return result
     * @throws ExecuteException if error throws ExecuteException
     */
    public void dispatch(final ExecutionContext context) throws ExecuteException {
        // get executor manager
        ExecutorManager<Boolean> executorManager = this.executorManagers.get(context.getExecutorType());
        if (executorManager == null) {
            throw new ExecuteException("no ExecutorManager for type : " + context.getExecutorType());
        }

        // host select
        Host host = hostManager.select(context);
        if (StringUtils.isEmpty(host.getAddress())) {
            log.warn("fail to execute : {} due to no suitable worker, current task needs worker group {} to execute",
                    context.getMessage(), context.getWorkerGroup());
            throw new ExecuteException("no suitable worker");
        }
        context.setHost(host);
        executorManager.beforeExecute(context);
        try {
            // task execute
            executorManager.execute(context);
        } finally {
            executorManager.afterExecute(context);
        }
    }

    /**
     * register init
     * @throws Exception if error throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        register(ExecutorType.WORKER, nettyExecutorManager);
        register(ExecutorType.CLIENT, nettyExecutorManager);
    }

    /**
     *  register
     * @param type executor type
     * @param executorManager executorManager
     */
    public void register(ExecutorType type, ExecutorManager executorManager) {
        executorManagers.put(type, executorManager);
    }
}
