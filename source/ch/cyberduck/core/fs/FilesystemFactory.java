package ch.cyberduck.core.fs;

/*
 * Copyright (c) 2002-2011 David Kocher. All rights reserved.
 *
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

import ch.cyberduck.core.Factory;
import ch.cyberduck.core.FactoryException;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: FilesystemFactory.java 10191 2012-10-15 16:48:21Z dkocher $
 */
public abstract class FilesystemFactory extends Factory<Filesystem> {

    /**
     * Registered factories
     */
    private static final Map<Factory.Platform, FilesystemFactory> factories
            = new HashMap<Factory.Platform, FilesystemFactory>();

    public static void addFactory(Factory.Platform platform, FilesystemFactory f) {
        factories.put(platform, f);
    }

    public static Filesystem get() {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create();
    }
}
