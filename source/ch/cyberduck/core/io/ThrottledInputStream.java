package ch.cyberduck.core.io;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;

/**
 * @version $Id: ThrottledInputStream.java 9399 2012-02-26 17:36:45Z dkocher $
 */
public class ThrottledInputStream extends InputStream {

    /**
     * The delegate.
     */
    private InputStream delegate;

    /**
     * Limits throughput.
     */
    private BandwidthThrottle throttle;

    public ThrottledInputStream(InputStream delegate, BandwidthThrottle throttle) {
        this.delegate = delegate;
        this.throttle = throttle;
    }

    /**
     * Read a single byte from this InputStream.
     *
     * @throws IOException if an I/O error occurs on the InputStream.
     */
    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    /**
     * Read an array of bytes from this InputStream.
     *
     * @param data   the bytes to read.
     * @param offset the index in the array to start at.
     * @param len    the number of bytes to read.
     * @throws IOException if an I/O error occurs on the InputStream.
     */
    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        return delegate.read(data, offset, throttle.request(len));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}