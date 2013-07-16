package ch.cyberduck.core.transfer;

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

import ch.cyberduck.core.Preferences;

/**
 * @version $Id: TransferOptions.java 10513 2012-10-21 16:37:31Z dkocher $
 */
public final class TransferOptions {

    /**
     * Resume requested using user interface
     */
    public boolean resumeRequested = false;

    /**
     * Reload requested using user interface
     */
    public boolean reloadRequested = false;

    /**
     * Close session after transfer
     */
    public boolean closeSession = true;

    /**
     * Add quarantine flag to downloaded file
     */
    public boolean quarantine =
            Preferences.instance().getBoolean("queue.download.quarantine");

    public boolean open =
            Preferences.instance().getBoolean("queue.postProcessItemWhenComplete");

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TransferOptions");
        sb.append("{resumeRequested=").append(resumeRequested);
        sb.append(", reloadRequested=").append(reloadRequested);
        sb.append(", closeSession=").append(closeSession);
        sb.append(", quarantine=").append(quarantine);
        sb.append('}');
        return sb.toString();
    }
}
