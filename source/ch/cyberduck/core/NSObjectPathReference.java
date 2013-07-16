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

import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.cocoa.foundation.NSString;

import org.apache.commons.collections.map.LRUMap;

import java.util.Collections;
import java.util.Map;

/**
 * Mapper between path references returned from the outline view model and its internal
 * string representation.
 *
 * @version $Id: NSObjectPathReference.java 10082 2012-10-12 13:45:31Z dkocher $
 */
public class NSObjectPathReference extends PathReference<NSObject> {

    private static class Factory extends PathReferenceFactory {
        @Override
        protected PathReference create() {
            throw new UnsupportedOperationException("Please provide a parameter");
        }

        @Override
        protected <T> PathReference<T> create(AbstractPath param) {
            return (PathReference<T>) new NSObjectPathReference(param);
        }
    }

    public static void register() {
        PathReferenceFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private NSObject reference;

    private int hashcode;

    private static Map<String, NSString> cache = Collections.synchronizedMap(new LRUMap(
            Preferences.instance().getInteger("browser.model.cache.size")
    ));


    private NSObjectPathReference(final AbstractPath path) {
        // Unique name
        final String name = path.unique();
        if(!cache.containsKey(name)) {
            cache.put(name, NSString.stringWithString(name));
        }
        this.reference = cache.get(name);
        this.hashcode = name.hashCode();
    }

    public NSObjectPathReference(NSObject reference) {
        this.reference = reference;
        this.hashcode = reference.toString().hashCode();
    }

    @Override
    public NSObject unique() {
        return reference;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public boolean equals(final Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        final NSObjectPathReference that = (NSObjectPathReference) o;
        if(hashcode != that.hashcode) {
            return false;
        }
        return true;
    }
}