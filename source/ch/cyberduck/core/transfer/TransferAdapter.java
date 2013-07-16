package ch.cyberduck.core.transfer;

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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.io.BandwidthThrottle;

/**
 * @version $Id: TransferAdapter.java 10263 2012-10-15 19:01:18Z dkocher $
 */
public class TransferAdapter implements TransferListener {

    @Override
    public void transferWillStart() {
        //
    }

    @Override
    public void transferQueued() {
        //
    }

    @Override
    public void transferResumed() {
        //
    }

    @Override
    public void transferDidEnd() {
        //
    }

    @Override
    public void willTransferPath(final Path path) {
        //
    }

    @Override
    public void didTransferPath(final Path path) {
        //
    }

    @Override
    public void bandwidthChanged(BandwidthThrottle bandwidth) {
        //
    }
}