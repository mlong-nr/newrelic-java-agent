/*
 *
 *  * Copyright 2023 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package com.nr.vertx.instrumentation;

import com.newrelic.api.agent.Token;
import com.newrelic.api.agent.Trace;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class AsyncHandlerWrapper<T> implements Handler<AsyncResult<T>> {
    private Handler<AsyncResult<T>> original;

    private Token token;

    public AsyncHandlerWrapper(Handler<AsyncResult<T>> original, Token token) {
        System.out.println("------- Construct AsyncHandlerWrapper   "+ token);
        this.original = original;
        this.token = token;
    }

    @Override
    @Trace(async = true, excludeFromTransactionTrace = true)
    public void handle(AsyncResult<T> event) {
        if (token != null) {
            System.out.println("------- Link&Expire  " + this.token);
            token.linkAndExpire();
            token = null;
        }

        this.original.handle(event);
        this.original = null;
    }
}
