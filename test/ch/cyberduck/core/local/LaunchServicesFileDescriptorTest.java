package ch.cyberduck.core.local;

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

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Id: LaunchServicesFileDescriptorTest.java 10705 2012-12-22 19:04:53Z dkocher $
 */
public class LaunchServicesFileDescriptorTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        LaunchServicesFileDescriptor.register();
        FinderLocal.register();
    }

    @Test
    public void testGetKind() throws Exception {
        assertTrue(FileDescriptorFactory.get().getKind("/tmp/t.txt").startsWith("Plain"));
    }

    @Test
    public void testGetKindWithoutExtension() throws Exception {
        assertTrue(FileDescriptorFactory.get().getKind("txt").startsWith("Plain"));
    }
}
