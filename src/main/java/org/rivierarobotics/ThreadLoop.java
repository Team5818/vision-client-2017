/*
 * This file is part of vision-client-2017, licensed under the MIT License (MIT).
 *
 * Copyright (c) Team5818 <https://github.com/Team5818>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.rivierarobotics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadLoop {

    @FunctionalInterface
    public interface RunnableEx {

        void run() throws Exception;

    }

    private final Logger logger;
    private final String name;
    private final RunnableEx loop;
    private final long millisPeriod;

    public ThreadLoop(String name, RunnableEx loop, long millisPeriod) {
        this.name = name;
        this.loop = loop;
        this.millisPeriod = millisPeriod;
        this.logger = LoggerFactory.getLogger(name);
    }

    public Thread start() {
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    loop.run();
                    Thread.sleep(millisPeriod);
                } catch (Exception e) {
                    logger.warn("Error from loop", e);
                }
            }
        });
        thread.setName(name + " Loop");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

}
