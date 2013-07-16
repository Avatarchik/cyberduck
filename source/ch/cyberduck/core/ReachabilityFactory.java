package ch.cyberduck.core;

/*
 * Copyright (c) 2002-2009 David Kocher. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: ReachabilityFactory.java 10209 2012-10-15 17:10:40Z dkocher $
 */
public abstract class ReachabilityFactory extends Factory<Reachability> {

    /**
     * Registered factories
     */
    private static final Map<Platform, ReachabilityFactory> factories = new HashMap<Platform, ReachabilityFactory>();

    public static void addFactory(Platform platform, ReachabilityFactory f) {
        factories.put(platform, f);
    }

    public static Reachability get() {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create();
    }
}
