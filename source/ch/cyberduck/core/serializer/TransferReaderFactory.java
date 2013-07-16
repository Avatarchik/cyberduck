package ch.cyberduck.core.serializer;

/*
 * Copyright (c) 2009 David Kocher. All rights reserved.
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
import ch.cyberduck.core.transfer.Transfer;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: TransferReaderFactory.java 10205 2012-10-15 17:07:33Z dkocher $
 */
public abstract class TransferReaderFactory extends Factory<Reader<Transfer>> {

    /**
     * Registered factories
     */
    private static final Map<Platform, TransferReaderFactory> factories
            = new HashMap<Platform, TransferReaderFactory>();

    public static void addFactory(Factory.Platform platform, TransferReaderFactory f) {
        factories.put(platform, f);
    }

    public static Reader<Transfer> get() {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create();
    }
}
