package ch.cyberduck.core;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
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

import ch.cyberduck.ui.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: LoginControllerFactory.java 10902 2013-04-22 09:29:42Z dkocher $
 */
public abstract class LoginControllerFactory extends Factory<LoginController> {

    protected abstract LoginController create(Controller c);

    protected abstract LoginController create(Session s);

    /**
     * Registered factories
     */
    private static final Map<Platform, LoginControllerFactory> factories
            = new HashMap<Platform, LoginControllerFactory>();

    /**
     * @param s Connection
     * @return Login controller instance for the current platform.
     */
    public static LoginController get(Session s) {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create(s);
    }

    public static void addFactory(Platform p, LoginControllerFactory f) {
        factories.put(p, f);
    }
}
