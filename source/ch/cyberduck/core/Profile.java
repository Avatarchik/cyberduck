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
import ch.cyberduck.core.serializer.Deserializer;
import ch.cyberduck.core.serializer.DeserializerFactory;
import ch.cyberduck.core.serializer.Serializer;
import ch.cyberduck.core.serializer.SerializerFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * @version $Id: Profile.java 10845 2013-04-11 14:58:16Z dkocher $
 */
public final class Profile extends Protocol implements Serializable {
    private static final Logger log = Logger.getLogger(Profile.class);

    private Deserializer dict;

    /**
     * The actual protocol implementation registered
     */
    private Protocol parent;

    public <T> Profile(final T serialized) {
        dict = DeserializerFactory.createDeserializer(serialized);
        final String protocol = this.getValue("Protocol");
        if(StringUtils.isBlank(protocol)) {
            log.error("Missing protocol in profile");
            parent = ProtocolFactory.forName(Preferences.instance().getProperty("connection.protocol.default"));
        }
        else {
            parent = ProtocolFactory.forName(protocol);
        }
    }

    @Override
    public <T> T getAsDictionary() {
        final Serializer serializer = SerializerFactory.createSerializer();
        serializer.setStringForKey("Protocol", parent.getIdentifier());
        serializer.setStringForKey("Scheme", this.getScheme().toString());
        serializer.setStringForKey("Vendor", this.getProvider());
        serializer.setStringForKey("Description", this.getDescription());
        serializer.setStringForKey("Default Hostname", this.getDefaultHostname());
        serializer.setStringForKey("Default Port", String.valueOf(this.getDefaultPort()));
        serializer.setStringForKey("Username Placeholder", this.getUsernamePlaceholder());
        serializer.setStringForKey("Password Placeholder", this.getPasswordPlaceholder());
        return serializer.<T>getSerialized();
    }

    public Protocol getProtocol() {
        return parent;
    }

    /**
     * @return False if missing required fields in profile.
     */
    @Override
    public boolean isEnabled() {
        return StringUtils.isNotBlank(this.getValue("Protocol"))
                && StringUtils.isNotBlank(this.getValue("Vendor"));
    }

    private String getValue(final String key) {
        final String value = dict.stringForKey(key);
        if(StringUtils.isBlank(value)) {
            log.debug("No value for key:" + key);
        }
        return value;
    }

    @Override
    public String getIdentifier() {
        return parent.getIdentifier();
    }

    @Override
    public Type getType() {
        return parent.getType();
    }

    @Override
    public String getUsernamePlaceholder() {
        final String v = this.getValue("Username Placeholder");
        if(StringUtils.isBlank(v)) {
            return parent.getUsernamePlaceholder();
        }
        return v;
    }

    @Override
    public String getPasswordPlaceholder() {
        final String v = this.getValue("Password Placeholder");
        if(StringUtils.isBlank(v)) {
            return parent.getPasswordPlaceholder();
        }
        return v;
    }

    @Override
    public String getDefaultHostname() {
        final String v = this.getValue("Default Hostname");
        if(StringUtils.isBlank(v)) {
            return parent.getDefaultHostname();
        }
        return v;
    }

    @Override
    public String getProvider() {
        final String v = this.getValue("Vendor");
        if(StringUtils.isBlank(v)) {
            return parent.getProvider();
        }
        return v;
    }

    @Override
    public String getDescription() {
        final String v = this.getValue("Description");
        if(StringUtils.isBlank(v)) {
            return parent.getDescription();
        }
        return v;
    }

    @Override
    public int getDefaultPort() {
        final String v = this.getValue("Default Port");
        if(StringUtils.isBlank(v)) {
            return parent.getDefaultPort();
        }
        try {
            return Integer.valueOf(v);
        }
        catch(NumberFormatException e) {
            log.warn(String.format("Port %s is not a number", e.getMessage()));
        }
        return parent.getDefaultPort();
    }

    @Override
    public String getName() {
        return parent.getName();
    }

    @Override
    public String favicon() {
        return this.icon();
    }

    @Override
    public String disk() {
        final String temp = this.write(this.getValue("Disk"));
        if(StringUtils.isBlank(temp)) {
            return parent.disk();
        }
        // Temporary file
        return temp;
    }

    /**
     * Write temporary file with data
     *
     * @param icon Base64 encoded image information
     * @return Path to file
     */
    private String write(final String icon) {
        if(StringUtils.isBlank(icon)) {
            return null;
        }
        final byte[] favicon = Base64.decodeBase64(icon);
        final Local file = LocalFactory.createLocal(Preferences.instance().getProperty("tmp.dir"),
                UUID.randomUUID().toString() + ".ico");
        file.delete(true);
        try {
            final OutputStream out = file.getOutputStream(false);
            try {
                IOUtils.write(favicon, out);
            }
            finally {
                IOUtils.closeQuietly(out);
            }
            return file.getAbsolute();
        }
        catch(IOException e) {
            log.error("Error writing temporary file", e);
        }
        return null;
    }

    @Override
    public boolean isHostnameConfigurable() {
        return parent.isHostnameConfigurable();
    }

    @Override
    public boolean isWebUrlConfigurable() {
        return parent.isWebUrlConfigurable();
    }

    @Override
    public Scheme getScheme() {
        final String v = this.getValue("Scheme");
        if(StringUtils.isBlank(v)) {
            return parent.getScheme();
        }
        return Scheme.valueOf(v);
    }

    @Override
    public String getContext() {
        final String v = this.getValue("Context");
        if(StringUtils.isBlank(v)) {
            return parent.getContext();
        }
        return v;
    }

    @Override
    public boolean isPortConfigurable() {
        return parent.isPortConfigurable();
    }

    @Override
    public boolean isEncodingConfigurable() {
        return parent.isEncodingConfigurable();
    }

    @Override
    public boolean isConnectModeConfigurable() {
        return parent.isConnectModeConfigurable();
    }

    @Override
    public boolean isAnonymousConfigurable() {
        return parent.isAnonymousConfigurable();
    }

    @Override
    public boolean isUTCTimezone() {
        return parent.isUTCTimezone();
    }

    @Override
    public boolean validate(Credentials credentials) {
        return parent.validate(credentials);
    }
}