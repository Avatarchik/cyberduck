package ch.cyberduck.core.ftp;

/*
 * Copyright (c) 2012 David Kocher. All rights reserved.
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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;

/**
 * @version $Id: LoggingProtocolCommandListener.java 10393 2012-10-18 08:47:15Z dkocher $
 */
public abstract class LoggingProtocolCommandListener implements ProtocolCommandListener {

    @Override
    public void protocolCommandSent(ProtocolCommandEvent event) {
        String message = StringUtils.chomp(event.getMessage());
        if(message.startsWith("PASS")) {
            message = "PASS ********";
        }
        this.log(true, message);
    }

    @Override
    public void protocolReplyReceived(ProtocolCommandEvent event) {
        this.log(false, StringUtils.chomp(event.getMessage()));
    }

    public abstract void log(boolean request, String event);
}
