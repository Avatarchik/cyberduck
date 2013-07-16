package ch.cyberduck.core.analytics;

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

import ch.cyberduck.core.Credentials;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.Scheme;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

/**
 * @version $Id: QloudstatAnalyticsProvider.java 10603 2012-11-23 18:07:42Z dkocher $
 */
public class QloudstatAnalyticsProvider implements AnalyticsProvider {
    private static Logger log = Logger.getLogger(QloudstatAnalyticsProvider.class);

    private static final String uri
            = Preferences.instance().getProperty("analytics.provider.qloudstat.setup");

    @Override
    public String getName() {
        return URI.create(uri).getHost();
    }

    @Override
    public String getSetup(final Protocol protocol, final Scheme method, final String container,
                           final Credentials credentials) {
        if(!credentials.validate(protocol)) {
            log.warn(String.format("No valid credentials for analytics setup in %s", container));
            return null;
        }
        final String setup = String.format("provider=%s,protocol=%s,endpoint=%s,key=%s,secret=%s",
                protocol.getDefaultHostname(),
                method.name(),
                container,
                credentials.getUsername(),
                credentials.getPassword());
        final String encoded;
        try {
            encoded = this.encode(new String(Base64.encodeBase64(setup.getBytes("UTF-8")), "UTF-8"));
        }
        catch(UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return String.format("%s?setup=%s", uri, encoded);
    }

    private String encode(final String p) {
        try {
            StringBuilder b = new StringBuilder();
            b.append(URLEncoder.encode(p, "UTF-8"));
            // Becuase URLEncoder uses <code>application/x-www-form-urlencoded</code> we have to replace these
            // for proper URI percented encoding.
            return b.toString().replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
        }
        catch(UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}