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
import ch.cyberduck.core.NullLocal;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * @version $Id: WorkspaceApplicationLauncherTest.java 10276 2012-10-15 19:50:33Z dkocher $
 */
public class WorkspaceApplicationLauncherTest extends AbstractTestCase {

    @BeforeClass
    public static void register() {
        WorkspaceApplicationLauncher.register();
    }

    @Test
    public void testOpen() throws Exception {
        this.repeat(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ApplicationLauncherFactory.get().open(new NullLocal(null, "t"));
                return null;
            }
        }, 5);
        this.repeat(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final NullLocal file = new NullLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                file.touch();
                file.delete(true);
                ApplicationLauncherFactory.get().open(file);
                return null;
            }
        }, 5);
    }

    @Test
    public void testBounce() throws Exception {
        this.repeat(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ApplicationLauncherFactory.get().bounce(new NullLocal(null, "t"));
                return null;
            }
        }, 5);
        this.repeat(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                final NullLocal file = new NullLocal(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                file.touch();
                file.delete(true);
                ApplicationLauncherFactory.get().bounce(file);
                return null;
            }
        }, 5);
    }
}
