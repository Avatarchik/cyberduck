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

import ch.cyberduck.core.Factory;
import ch.cyberduck.core.FactoryException;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: ApplicationLauncherFactory.java 10258 2012-10-15 18:46:31Z dkocher $
 */
public abstract class ApplicationLauncherFactory extends Factory<ApplicationLauncher> {

    /**
     * Registered factories
     */
    private static final Map<Factory.Platform, ApplicationLauncherFactory> factories
            = new HashMap<Factory.Platform, ApplicationLauncherFactory>();

    public static void addFactory(Factory.Platform platform, ApplicationLauncherFactory f) {
        factories.put(platform, f);
    }

    public static ApplicationLauncher get() {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create();
    }
}
