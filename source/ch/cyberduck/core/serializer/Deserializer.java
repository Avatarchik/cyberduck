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

import java.util.List;

/**
 * @version $Id: Deserializer.java 9211 2011-11-14 09:02:33Z dkocher $
 */
public interface Deserializer<T> {

    /**
     * @param key Key name
     * @return String value for key
     */
    String stringForKey(String key);

    /**
     * @param key Key name
     * @return Object value for key
     */
    T objectForKey(String key);

    /**
     * @param key Key name
     * @return List values for key
     */
    List<T> listForKey(String key);
}