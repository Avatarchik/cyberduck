package ch.cyberduck.core;

/*
 * Copyright (c) 2012 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.local.FinderLocal;
import ch.cyberduck.core.local.LocalFactory;
import ch.cyberduck.core.serializer.impl.PlistWriter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

/**
 * @version $Id: HistoryCollectionTest.java 10590 2012-11-21 18:18:20Z dkocher $
 */
public class HistoryCollectionTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        FinderLocal.register();
        PlistWriter.register();
    }

    @Test
    public void testAdd() throws Exception {
        final CountDownLatch lock = new CountDownLatch(1);
        final CountDownLatch loaded = new CountDownLatch(1);
        final CountDownLatch exit = new CountDownLatch(1);
        final HistoryCollection c = new HistoryCollection(LocalFactory.createLocal("test/ch/cyberduck/core/history")) {
            @Override
            protected void sort() {
                if(loaded.getCount() == 0) {
                    return;
                }
                loaded.countDown();
                Collections.sort(this, new Comparator<Host>() {
                    @Override
                    public int compare(Host o1, Host o2) {
                        try {
                            lock.await();
                        }
                        catch(InterruptedException e) {
                            fail();
                        }
                        return 0;
                    }
                });
                exit.countDown();
            }

            @Override
            public void collectionItemRemoved(Host bookmark) {
                assertEquals("mirror.switch.ch", bookmark.getHostname());
            }
        };
        new Thread() {
            @Override
            public void run() {
                c.load();
            }
        }.start();
        loaded.await();
        assertEquals(1, c.size());
        final Host host = c.get(0);
        // Add again to history upon connect before history finished loading
        assertTrue(c.add(host));
        lock.countDown();
        exit.await();
    }
}
