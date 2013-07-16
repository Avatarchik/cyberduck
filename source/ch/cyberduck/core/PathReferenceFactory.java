package ch.cyberduck.core;

/*
 *  Copyright (c) 2010 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: PathReferenceFactory.java 10219 2012-10-15 17:23:37Z dkocher $
 */
public abstract class PathReferenceFactory extends Factory<PathReference> {

    /**
     * Registered factories
     */
    private static final Map<Platform, PathReferenceFactory> factories
            = new HashMap<Platform, PathReferenceFactory>();

    public static void addFactory(Platform platform, PathReferenceFactory f) {
        factories.put(platform, f);
    }

    public static <T> PathReference<T> createPathReference(AbstractPath param) {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create(param);
    }

    protected abstract <T> PathReference<T> create(AbstractPath param);
}
