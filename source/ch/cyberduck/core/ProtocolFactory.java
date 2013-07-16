package ch.cyberduck.core;

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

import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.LocalFactory;
import ch.cyberduck.core.serializer.ProfileReaderFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @version $Id: ProtocolFactory.java 10785 2013-04-03 16:39:10Z dkocher $
 */
public final class ProtocolFactory {
    private static final Logger log = Logger.getLogger(ProtocolFactory.class);

    /**
     * Ordered list of supported protocols.
     */
    private static final Set<Protocol> protocols
            = new LinkedHashSet<Protocol>();

    private ProtocolFactory() {
        //
    }

    public static void register() {
        // Order determines list in connection dropdown
        register(Protocol.FTP);
        register(Protocol.FTP_TLS);
        register(Protocol.SFTP);
        register(Protocol.WEBDAV);
        register(Protocol.WEBDAV_SSL);
        register(Protocol.S3_SSL);
        register(Protocol.GOOGLESTORAGE_SSL);
        register(Protocol.CLOUDFILES);
        register(Protocol.SWIFT);

        // Load thirdparty protocols
        final Local profiles = LocalFactory.createLocal(Preferences.instance().getProperty("application.support.path"), "Profiles");
        if(profiles.exists()) {
            for(Local profile : profiles.children(new PathFilter<Local>() {
                @Override
                public boolean accept(Local file) {
                    return "cyberduckprofile".equals(FilenameUtils.getExtension(file.getName()));
                }
            })) {
                final Profile protocol = ProfileReaderFactory.get().read(profile);
                if(null == protocol) {
                    continue;
                }
                if(log.isInfoEnabled()) {
                    log.info(String.format("Adding thirdparty protocol %s", protocol));
                }
                // Replace previous possibly disable protocol in Preferences
                protocol.register();
            }
        }
    }

    public static void register(Protocol p) {
        protocols.add(p);
    }

    /**
     * @return List of enabled protocols
     */
    public static List<Protocol> getKnownProtocols() {
        return getKnownProtocols(true);
    }

    /**
     * @param filter Filter disabled protocols
     * @return List of protocols
     */
    public static List<Protocol> getKnownProtocols(final boolean filter) {
        List<Protocol> list = new ArrayList<Protocol>(protocols);
        if(filter) {
            // Remove protocols not enabled
            for(Iterator<Protocol> iter = list.iterator(); iter.hasNext(); ) {
                final Protocol protocol = iter.next();
                if(!protocol.isEnabled()) {
                    iter.remove();
                }
            }
        }
        if(list.isEmpty()) {
            throw new FactoryException("No protocols configured");
        }
        return list;
    }

    /**
     * @param port Default port
     * @return The standard protocol for this port number
     */
    public static Protocol getDefaultProtocol(final int port) {
        for(Protocol protocol : getKnownProtocols(false)) {
            if(protocol.getDefaultPort() == port) {
                return protocol;
            }
        }
        log.warn(String.format("Cannot find default protocol for port %d", port));
        return forName(
                Preferences.instance().getProperty("connection.protocol.default"));
    }

    /**
     * @param identifier Provider name or hash code of protocol
     * @return Matching protocol or default if no match
     */
    public static Protocol forName(final String identifier) {
        for(Protocol protocol : getKnownProtocols(false)) {
            if(protocol.getProvider().equals(identifier)) {
                return protocol;
            }
        }
        for(Protocol protocol : getKnownProtocols(false)) {
            if(String.valueOf(protocol.hashCode()).equals(identifier)) {
                return protocol;
            }
        }
        log.fatal(String.format("Unknown protocol with identifier %s", identifier));
        return forName(
                Preferences.instance().getProperty("connection.protocol.default"));
    }

    /**
     * @param scheme Protocol scheme
     * @return Standard protocol for this scheme. This is ambigous
     */
    public static Protocol forScheme(final String scheme) {
        for(Protocol protocol : getKnownProtocols(false)) {
            for(int k = 0; k < protocol.getSchemes().length; k++) {
                if(protocol.getSchemes()[k].equals(scheme)) {
                    return protocol;
                }
            }
        }
        log.fatal("Unknown scheme:" + scheme);
        return forName(
                Preferences.instance().getProperty("connection.protocol.default"));
    }

    /**
     * @param str Determine if URL can be handleed by a registered protocol
     * @return True if known URL
     */
    public static boolean isURL(final String str) {
        if(StringUtils.isNotBlank(str)) {
            for(Protocol protocol : getKnownProtocols(false)) {
                String[] schemes = protocol.getSchemes();
                for(String scheme : schemes) {
                    if(str.startsWith(scheme + "://")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}