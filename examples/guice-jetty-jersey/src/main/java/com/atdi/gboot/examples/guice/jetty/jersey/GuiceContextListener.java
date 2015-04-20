/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atdi.gboot.examples.guice.jetty.jersey;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceServletContextListener;


/**
 * Created by aurel on 4/17/15.
 */
public class GuiceContextListener extends GuiceServletContextListener {

    private static Injector injector;

    Module[] modules;

    public GuiceContextListener(Module... modules) {
        this.modules = modules;
    }

    @Override
    protected Injector getInjector() {
        if (injector == null) {
            injector = Guice.createInjector(modules);
        }
        return injector;
    }

    public static Injector getParentInjector() {
        return injector;
    }
}
