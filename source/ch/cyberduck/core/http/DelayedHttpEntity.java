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

import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.log4j.Logger;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * @version $Id: DelayedHttpEntity.java 10717 2012-12-26 11:52:51Z dkocher $
 */
public abstract class DelayedHttpEntity extends AbstractHttpEntity {
    private static Logger log = Logger.getLogger(DelayedHttpEntity.class);

    private final CountDownLatch entry;
    private final CountDownLatch exit = new CountDownLatch(1);

    /**
     * @param entry Signal when stream is ready
     */
    public DelayedHttpEntity(CountDownLatch entry) {
        this.entry = entry;
    }

    /**
     * HTTP stream to write to
     */
    private OutputStream stream;
    /**
     *
     */
    private boolean consumed = false;

    public boolean isRepeatable() {
        return false;
    }

    public abstract long getContentLength();

    public InputStream getContent() throws IOException, IllegalStateException {
        throw new UnsupportedOperationException("No content here");
    }

    /**
     * @return The stream to write to after the entry signal was received.
     */
    public OutputStream getStream() {
        if(null == stream) {
            // Nothing to write
            return NullOutputStream.NULL_OUTPUT_STREAM;
        }
        return stream;
    }

    public void writeTo(final OutputStream out) throws IOException {
        try {
            stream = new FilterOutputStream(out) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    }
                    finally {
                        // Signal finished writing to stream
                        exit.countDown();
                    }
                }
            };
        }
        finally {
            entry.countDown();
        }
        // Wait for signal when content has been written to the pipe
        try {
            exit.await();
        }
        catch(InterruptedException e) {
            log.error("Error waiting for exit signal:" + e.getMessage());
        }
        // Entity written to server
        consumed = true;
    }

    public boolean isStreaming() {
        return !consumed;
    }
}
