package ch.cyberduck.ui.cocoa.quicklook;

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

import ch.cyberduck.core.Factory;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: QuickLookFactory.java 10529 2012-10-22 11:57:15Z dkocher $
 */
public abstract class QuickLookFactory extends Factory<QuickLook> {

    /**
     * Registered factories
     */
    protected static final Map<Platform, QuickLookFactory> factories = new HashMap<Platform, QuickLookFactory>();

    public static void addFactory(Platform platform, QuickLookFactory f) {
        factories.put(platform, f);
    }

    public static QuickLook get() {
        if(factories.containsKey(VERSION_PLATFORM)) {
            return factories.get(VERSION_PLATFORM).create();
        }
        else {
            return new Disabled();
        }
    }

    private static final class Disabled extends AbstractQuickLook {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void open() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected abstract QuickLook create();
}
