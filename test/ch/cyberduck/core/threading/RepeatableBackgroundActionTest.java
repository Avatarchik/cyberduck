package ch.cyberduck.core.threading;

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

import ch.cyberduck.core.AbstractTestCase;
import ch.cyberduck.core.ConnectionCanceledException;
import ch.cyberduck.core.Host;
import ch.cyberduck.core.Session;

import org.junit.Test;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @version $Id: RepeatableBackgroundActionTest.java 10516 2012-10-21 16:51:17Z dkocher $
 */
public class RepeatableBackgroundActionTest extends AbstractTestCase {

    @Test
    public void testGetExceptions() throws Exception {
        RepeatableBackgroundAction a = new RepeatableBackgroundAction() {

            @Override
            protected List<Session> getSessions() {
                return Collections.emptyList();
            }

            @Override
            public void run() {
                //
            }
        };
        a.error(new BackgroundException(new Host("t"), null, null, new ConnectionCanceledException()));
        assertFalse(a.hasFailed());
        assertEquals(0, a.getExceptions().size());
        a.error(new BackgroundException(new Host("t"), null, null, null));
        assertTrue(a.hasFailed());
        assertEquals(1, a.getExceptions().size());
    }

    @Test
    public void testFilter() throws Exception {
        RepeatableBackgroundAction a = new RepeatableBackgroundAction() {
            @Override
            protected List<Session> getSessions() {
                return Collections.emptyList();
            }

            @Override
            public void run() {
                //
            }
        };
        a.error(new BackgroundException(new Host("t"), null, null, new SocketException("")));
        assertTrue(a.hasFailed());
        assertEquals(1, a.getExceptions().size());
//        a.error(new BackgroundException(new Host("t"), null, null, new SocketException("")));
//        assertTrue(a.hasFailed());
//        assertEquals(1, a.getExceptions().size());
        a.error(new BackgroundException(new Host("t"), null, null, new UnknownHostException("")));
        assertTrue(a.hasFailed());
        assertEquals(2, a.getExceptions().size());
    }
}
