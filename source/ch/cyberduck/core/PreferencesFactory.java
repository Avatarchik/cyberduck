package ch.cyberduck.core;

/*
 *  Copyright (c) 2009 David Kocher. All rights reserved.
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
 * @version $Id: PreferencesFactory.java 10218 2012-10-15 17:23:31Z dkocher $
 */
public abstract class PreferencesFactory extends Factory<Preferences> {

    /**
     * Registered factories
     */
    private static final Map<Platform, PreferencesFactory> factories = new HashMap<Platform, PreferencesFactory>();

    public static void addFactory(Platform platform, PreferencesFactory f) {
        factories.put(platform, f);
    }

    private static Preferences l;

    public static Preferences createPreferences() {
        if(null == l) {
            if(!factories.containsKey(NATIVE_PLATFORM)) {
                throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
            }
            l = factories.get(NATIVE_PLATFORM).create();
        }
        return l;
    }
}