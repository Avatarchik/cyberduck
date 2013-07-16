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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @version $Id: ResponseOutputStream.java 10717 2012-12-26 11:52:51Z dkocher $
 */
public abstract class ResponseOutputStream<T> extends FilterOutputStream {

    public ResponseOutputStream(OutputStream d) {
        super(d);
    }

    /**
     * Return the response after closing the stream. Must close the stream first to prevent deadlock.
     *
     * @return A specific response header
     */
    public abstract T getResponse() throws IOException;
}