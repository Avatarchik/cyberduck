package ch.cyberduck.core;

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
 * @version $Id: ConnectionListener.java 9433 2012-02-26 18:31:17Z dkocher $
 */
public interface ConnectionListener {

    /**
     * The remote connection will be opened next
     */
    void connectionWillOpen();

    /**
     * The remote connection has been opened successfully
     */
    void connectionDidOpen();

    /**
     * The remote connection is about to be closed
     */
    void connectionWillClose();

    /**
     * The remote connection has been closed
     */
    void connectionDidClose();
}
