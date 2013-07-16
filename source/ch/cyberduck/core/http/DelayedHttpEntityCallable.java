package ch.cyberduck.core.http;

/*
 * Copyright (c) 2002-2011 David Kocher. All rights reserved.
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

import org.apache.http.entity.AbstractHttpEntity;

import java.io.IOException;

/**
 * @version $Id: DelayedHttpEntityCallable.java 9435 2012-02-26 18:31:59Z dkocher $
 */
public interface DelayedHttpEntityCallable<T> {
    T call(AbstractHttpEntity entity) throws IOException;

    long getContentLength();
}
