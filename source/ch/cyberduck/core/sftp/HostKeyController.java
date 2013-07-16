package ch.cyberduck.core.sftp;


/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
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

import ch.cyberduck.core.ConnectionCanceledException;

import ch.ethz.ssh2.ServerHostKeyVerifier;

/**
 * @version $Id: HostKeyController.java 9504 2012-04-04 09:34:58Z dkocher $
 */
public abstract class HostKeyController implements ServerHostKeyVerifier {

    /**
     * @param hostname               Hostname
     * @param port                   Port number
     * @param serverHostKeyAlgorithm Algorithm
     * @param serverHostKey          Key blob
     * @return True if accepted.
     * @throws ch.cyberduck.core.ConnectionCanceledException
     *          Canceled by user
     */
    protected abstract boolean isUnknownKeyAccepted(final String hostname, final int port, final String serverHostKeyAlgorithm,
                                                    final byte[] serverHostKey) throws ConnectionCanceledException;

    /**
     * @param hostname               Hostname
     * @param port                   Port number
     * @param serverHostKeyAlgorithm Algorithm
     * @param serverHostKey          Key blob
     * @return True if accepted.
     * @throws ch.cyberduck.core.ConnectionCanceledException
     *          Canceled by user
     */
    protected abstract boolean isChangedKeyAccepted(final String hostname, final int port, final String serverHostKeyAlgorithm,
                                                    final byte[] serverHostKey) throws ConnectionCanceledException;
}
