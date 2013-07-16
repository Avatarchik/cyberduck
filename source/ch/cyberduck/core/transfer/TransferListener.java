package ch.cyberduck.core.transfer;

import ch.cyberduck.core.Path;
import ch.cyberduck.core.io.BandwidthThrottle;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
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

/**
 * @version $Id: TransferListener.java 10263 2012-10-15 19:01:18Z dkocher $
 */
public interface TransferListener {

    /**
     * The transfers are about to start transfering
     */
    void transferWillStart();

    /**
     * The transfer is paused and waits for other transfers to finish first
     */
    void transferQueued();

    /**
     * The transfer has a slot in the queue allocated
     */
    void transferResumed();

    /**
     * All transfers did end
     */
    void transferDidEnd();

    /**
     * The path part of this transfer will be transferred
     *
     * @param path File
     */
    void willTransferPath(Path path);

    /**
     * The path part of this transfer has been transferred
     *
     * @param path File
     */
    void didTransferPath(Path path);

    /**
     * Bandwidth throttle changed in controller
     *
     * @param bandwidth Settings
     */
    void bandwidthChanged(BandwidthThrottle bandwidth);
}
