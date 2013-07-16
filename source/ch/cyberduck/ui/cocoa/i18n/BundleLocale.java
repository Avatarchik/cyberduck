package ch.cyberduck.ui.cocoa.i18n;

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

import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.i18n.LocaleFactory;
import ch.cyberduck.ui.cocoa.foundation.NSBundle;

import org.apache.commons.collections.map.LRUMap;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;

/**
 * @version $Id: BundleLocale.java 9652 2012-08-22 12:32:28Z dkocher $
 */
public class BundleLocale extends Locale {
    private static Logger log = Logger.getLogger(BundleLocale.class);

    public static void register() {
        LocaleFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends LocaleFactory {
        @Override
        protected Locale create() {
            return new BundleLocale();
        }
    }

    private static Map<String, String> cache = Collections.<String, String>synchronizedMap(new LRUMap() {
        @Override
        protected boolean removeLRU(LinkEntry entry) {
            log.debug("Removing from cache:" + entry);
            return true;
        }
    });

    @Override
    public String get(final String key, final String table) {
        String identifier = String.format("%s.%s", table, key);
        if(!cache.containsKey(identifier)) {
            cache.put(identifier, NSBundle.localizedString(key, table));
        }
        return cache.get(identifier);
    }
}