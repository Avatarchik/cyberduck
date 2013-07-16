package ch.cyberduck.core.serializer.impl;

/*
 * Copyright (c) 2009 David Kocher. All rights reserved.
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

import ch.cyberduck.core.serializer.Deserializer;
import ch.cyberduck.core.serializer.DeserializerFactory;
import ch.cyberduck.ui.cocoa.foundation.NSArray;
import ch.cyberduck.ui.cocoa.foundation.NSDictionary;
import ch.cyberduck.ui.cocoa.foundation.NSEnumerator;
import ch.cyberduck.ui.cocoa.foundation.NSMutableDictionary;
import ch.cyberduck.ui.cocoa.foundation.NSObject;

import org.rococoa.Rococoa;

import java.util.ArrayList;
import java.util.List;

/**
 * @version $Id: PlistDeserializer.java 10226 2012-10-15 17:30:25Z dkocher $
 */
public class PlistDeserializer implements Deserializer<NSDictionary> {

    public static void register() {
        DeserializerFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends DeserializerFactory<NSDictionary> {
        @Override
        protected Deserializer<NSDictionary> create() {
            return new PlistDeserializer(NSMutableDictionary.dictionary());
        }

        @Override
        protected Deserializer<NSDictionary> create(NSDictionary dict) {
            return new PlistDeserializer(dict);
        }
    }

    final NSDictionary dict;

    public PlistDeserializer(NSDictionary dict) {
        this.dict = dict;
    }

    @Override
    public String stringForKey(String key) {
        final NSObject value = dict.objectForKey(key);
        if(null == value) {
            return null;
        }
        return value.toString();
    }

    @Override
    public NSDictionary objectForKey(String key) {
        final NSObject value = dict.objectForKey(key);
        if(null == value) {
            return null;
        }
        return Rococoa.cast(value, NSDictionary.class);
    }

    @Override
    public List<NSDictionary> listForKey(String key) {
        final NSObject value = dict.objectForKey(key);
        if(null == value) {
            return null;
        }
        final List<NSDictionary> list = new ArrayList<NSDictionary>();
        final NSArray array = Rococoa.cast(value, NSArray.class);
        final NSEnumerator enumerator = array.objectEnumerator();
        NSObject next;
        while((next = enumerator.nextObject()) != null) {
            list.add(Rococoa.cast(next, NSDictionary.class));
        }
        return list;
    }
}