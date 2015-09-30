/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheServerNotFoundException;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.GridDebug;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.marshaller.optimized.OptimizedMarshaller;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

/**
 *
 */
public class IgniteCacheCreatePutMultiNodeSelfTest2 extends GridCommonAbstractTest {
    /** Grid count. */
    private static final int GRID_CNT = 3;

    /** */
    private static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setPeerClassLoadingEnabled(false);

        TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();
        discoSpi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(discoSpi);

        OptimizedMarshaller marsh = new OptimizedMarshaller();
        marsh.setRequireSerializable(false);

        cfg.setMarshaller(marsh);

        CacheConfiguration ccfg = new CacheConfiguration();

        ccfg.setName("cache*");
        ccfg.setCacheMode(CacheMode.PARTITIONED);
        ccfg.setBackups(1);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected long getTestTimeout() {
        return 6 * 60 * 1000L;
    }

    /**
     * @throws Exception If failed.
     */
    public void testStartNodes() throws Exception {
        GridDebug.reset();

        long stopTime = System.currentTimeMillis() + 3 * 60_000;

        try {
            int iter = 0;

            while (System.currentTimeMillis() < stopTime) {
                log.info("Iteration: " + iter++);

                try {
                    final AtomicInteger idx = new AtomicInteger();

                    GridTestUtils.runMultiThreaded(new Callable<Void>() {
                        @Override public Void call() throws Exception {
                            int node = idx.getAndIncrement();

                            Ignite ignite = startGrid(node);

                            U.sleep();

                            IgniteCache cache = ignite.getOrCreateCache("cache1");

                            try {
                                for (int i = 0; i < 100; i++)
                                    cache.put(i, i);
                            }
                            catch (CacheServerNotFoundException e) {
                                synchronized (getClass()) {
                                    e.printStackTrace();

                                    GridDebug.debug("Error: " + e);

                                    GridDebug.dumpWithReset();

                                    System.exit(11);
                                }

                                throw e;
                            }

                            return null;
                        }
                    }, GRID_CNT, "start");

                    GridDebug.reset();
                }
                finally {
                    stopAllGrids();
                }
            }
        }
        finally {
            stopAllGrids();
        }
    }
}