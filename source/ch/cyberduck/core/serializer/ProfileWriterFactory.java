package ch.cyberduck.core.serializer;

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
import ch.cyberduck.core.Profile;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: ProfileWriterFactory.java 10432 2012-10-18 15:34:04Z dkocher $
 */
public abstract class ProfileWriterFactory extends Factory<Writer<Profile>> {

    /**
     * Registered factories
     */
    private static final Map<Platform, ProfileWriterFactory> factories = new HashMap<Platform, ProfileWriterFactory>();

    public static void addFactory(Platform platform, ProfileWriterFactory f) {
        factories.put(platform, f);
    }

    public static Writer<Profile> get() {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create();
    }
}
