package ch.cyberduck.core;

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

import ch.cyberduck.core.sftp.OpenSSHHostnameConfigurator;

/**
 * @version $Id: HostnameConfiguratorFactory.java 10555 2012-10-22 17:12:10Z dkocher $
 */
public final class HostnameConfiguratorFactory {

    private HostnameConfiguratorFactory() {
        //
    }

    /**
     * @param protocol Protocol
     * @return Configurator for default settings
     */
    public static ch.cyberduck.core.HostnameConfigurator get(final Protocol protocol) {
        if(protocol.equals(Protocol.SFTP)) {
            return new OpenSSHHostnameConfigurator();
        }
        return new NullHostnameConfigurator();
    }

    private static final class NullHostnameConfigurator implements HostnameConfigurator {
        @Override
        public String lookup(String alias) {
            return alias;
        }
    }
}
