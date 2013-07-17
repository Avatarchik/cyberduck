package ch.cyberduck.core;

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

import ch.cyberduck.core.i18n.Locale;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @version $Id: Protocol.java 10935 2013-04-24 15:57:35Z dkocher $
 */
public abstract class Protocol {
    private static final Logger log = Logger.getLogger(Protocol.class);

    /**
     * Must be unique across all available protocols.
     *
     * @return The identifier for this protocol which is the scheme by default
     */
    public abstract String getIdentifier();

    /**
     * Provider identification
     *
     * @return Identifier if no vendor specific profile
     * @see #getIdentifier()
     */
    public String getProvider() {
        return this.getIdentifier();
    }

    public String getName() {
        return this.getScheme().name().toUpperCase(java.util.Locale.ENGLISH);
    }

    public String favicon() {
        return null;
    }

    public boolean isEnabled() {
        return true;
    }

    /**
     * Statically register protocol implementations.
     */
    public void register() {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Register protocol %s", this.getIdentifier()));
        }
        ProtocolFactory.register(this);
    }

    /**
     * @return Human readable description
     */
    public abstract String getDescription();

    /**
     * @return Protocol scheme
     */
    public abstract Scheme getScheme();

    public String[] getSchemes() {
        return new String[]{this.getScheme().name()};
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof Protocol)) {
            return false;
        }
        Protocol protocol = (Protocol) o;
        if(this.getIdentifier() != null ? !this.getIdentifier().equals(protocol.getIdentifier()) : protocol.getIdentifier() != null) {
            return false;
        }
        if(this.getScheme() != null ? !this.getScheme().equals(protocol.getScheme()) : protocol.getScheme() != null) {
            return false;
        }
        if(this.getProvider() != null ? !this.getProvider().equals(protocol.getProvider()) : protocol.getProvider() != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = this.getIdentifier() != null ? this.getIdentifier().hashCode() : 0;
        result = 31 * result + (this.getScheme() != null ? this.getScheme().hashCode() : 0);
        result = 31 * result + (this.getProvider() != null ? this.getProvider().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return this.getProvider();
    }

    /**
     * @return A mounted disk icon to display
     */
    public String disk() {
        return String.format("%s.tiff", this.getIdentifier());
    }

    /**
     * @return A small icon to display
     */
    public String icon() {
        return this.disk();
    }

    /**
     * @return True if the protocol is inherently secure.
     */
    public boolean isSecure() {
        return this.getScheme().isSecure();
    }

    public boolean isHostnameConfigurable() {
        return true;
    }

    /**
     * @return False if the port to connect is static.
     */
    public boolean isPortConfigurable() {
        return true;
    }

    public boolean isWebUrlConfigurable() {
        return true;
    }

    /**
     * @return True if the character set is not defined in the protocol.
     */
    public boolean isEncodingConfigurable() {
        return false;
    }

    /**
     * @return True if there are different connect mode. Only applies to FTP.
     */
    public boolean isConnectModeConfigurable() {
        return false;
    }

    /**
     * @return True if anonymous logins are possible.
     */
    public boolean isAnonymousConfigurable() {
        return true;
    }

    public boolean isUTCTimezone() {
        return true;
    }

    public String getUsernamePlaceholder() {
        return Locale.localizedString("Username", "Credentials");
    }

    public String getPasswordPlaceholder() {
        return Locale.localizedString("Password", "Credentials");
    }

    public String getDefaultHostname() {
        return Preferences.instance().getProperty("connection.hostname.default");
    }

    public Set<String> getLocations() {
        return Collections.emptySet();
    }

    /**
     * Check login credentials for validity for this protocol.
     *
     * @param credentials Login credentials
     * @return True if username is not a blank string and password is not empty ("") and not null.
     */
    public boolean validate(Credentials credentials) {
        return StringUtils.isNotBlank(credentials.getUsername())
                && StringUtils.isNotEmpty(credentials.getPassword());
    }

    /**
     * @return The default port this protocol connects to
     */
    public int getDefaultPort() {
        return this.getScheme().getPort();
    }

    /**
     * @return Authentication path
     */
    public String getContext() {
        return null;
    }

    public Type getType() {
        return Type.valueOf(this.getIdentifier());
    }

    public enum Type {
        ftp,
        sftp,
        s3,
        googlestorage,
        swift,
		swiftkeystone,
		swiftfederatedkeystone,
        dav
    }

    public static final Protocol SFTP = new Protocol() {
        @Override
        public String getIdentifier() {
            return this.getScheme().name();
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("SFTP (SSH File Transfer Protocol)");
        }

        @Override
        public Scheme getScheme() {
            return Scheme.sftp;
        }

        @Override
        public boolean isEncodingConfigurable() {
            return true;
        }

        @Override
        public boolean validate(Credentials credentials) {
            if(credentials.isPublicKeyAuthentication()) {
                return StringUtils.isNotBlank(credentials.getUsername());
            }
            return super.validate(credentials);
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String disk() {
            return FTP_TLS.disk();
        }

        @Override
        public String icon() {
            return FTP_TLS.icon();
        }
    };

    public static final Protocol SCP = new Protocol() {
        @Override
        public String getIdentifier() {
            return "scp";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("SCP (Secure Copy)");
        }

        @Override
        public Scheme getScheme() {
            return Scheme.sftp;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    };

    public static final Protocol FTP = new Protocol() {
        @Override
        public String getIdentifier() {
            return this.getScheme().name();
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("FTP (File Transfer Protocol)");
        }

        @Override
        public Scheme getScheme() {
            return Scheme.ftp;
        }

        @Override
        public boolean isUTCTimezone() {
            return false;
        }

        @Override
        public boolean isEncodingConfigurable() {
            return true;
        }

        @Override
        public boolean isConnectModeConfigurable() {
            return true;
        }

        /**
         * Allows empty string for password.
         * @param credentials Login credentials
         * @return True if username is not blank and password is not null
         */
        @Override
        public boolean validate(Credentials credentials) {
            // Allow empty passwords
            return StringUtils.isNotBlank(credentials.getUsername()) && null != credentials.getPassword();
        }
    };

    public static final Protocol FTP_TLS = new Protocol() {
        @Override
        public String getIdentifier() {
            return this.getScheme().name();
        }

        @Override
        public Type getType() {
            return Type.ftp;
        }

        @Override
        public String getName() {
            return "FTP-SSL";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("FTP-SSL (Explicit AUTH TLS)");
        }

        @Override
        public Scheme getScheme() {
            return Scheme.ftps;
        }

        @Override
        public String disk() {
            return FTP.disk();
        }

        @Override
        public String icon() {
            return FTP.icon();
        }

        @Override
        public boolean isUTCTimezone() {
            return false;
        }

        @Override
        public boolean isEncodingConfigurable() {
            return true;
        }

        @Override
        public boolean isConnectModeConfigurable() {
            return true;
        }
    };

    public static final Protocol S3_SSL = new Protocol() {
        @Override
        public String getName() {
            return "S3";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("S3 (Amazon Simple Storage Service)", "S3");
        }

        @Override
        public String getIdentifier() {
            return "s3";
        }

        @Override
        public boolean isPortConfigurable() {
            return false;
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "s3"};
        }

        @Override
        public String getDefaultHostname() {
            return "s3.amazonaws.com";
        }

        @Override
        public Set<String> getLocations() {
            return new HashSet<String>(Arrays.asList(
                    "US",
                    "EU",
                    "us-west-1",
                    "us-west-2",
                    "ap-southeast-1",
                    "ap-southeast-2",
                    "ap-northeast-1",
                    "sa-east-1",
                    "s3-us-gov-west-1",
                    "s3-fips-us-gov-west-1"
            ));
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public String getUsernamePlaceholder() {
            return Locale.localizedString("Access Key ID", "S3");
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("Secret Access Key", "S3");
        }

        @Override
        public String favicon() {
            return this.icon();
        }
    };

    public static final Protocol CLOUDFRONT = new Protocol() {
        @Override
        public String getName() {
            return "Cloudfront";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Amazon CloudFront", "S3");
        }

        @Override
        public String getIdentifier() {
            return "cloudfront";
        }

        @Override
        public boolean isPortConfigurable() {
            return false;
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String getDefaultHostname() {
            return "cloudfront.amazonaws.com";
        }

        @Override
        public String getUsernamePlaceholder() {
            return Locale.localizedString("Access Key ID", "S3");
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("Secret Access Key", "S3");
        }
    };

    public static final Protocol WEBDAV = new Protocol() {
        @Override
        public String getName() {
            return "WebDAV (HTTP)";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("WebDAV (Web-based Distributed Authoring and Versioning)");
        }

        @Override
        public String getIdentifier() {
            return "dav";
        }

        @Override
        public Scheme getScheme() {
            return Scheme.http;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "dav"};
        }

        @Override
        public String disk() {
            return FTP.disk();
        }

        @Override
        public String icon() {
            return FTP.icon();
        }
    };

    public static final Protocol WEBDAV_SSL = new Protocol() {
        @Override
        public String getName() {
            return "WebDAV (HTTPS)";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("WebDAV (HTTP/SSL)");
        }

        @Override
        public String getIdentifier() {
            return "davs";
        }

        @Override
        public Type getType() {
            return Type.dav;
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "davs"};
        }

        @Override
        public String disk() {
            return FTP_TLS.disk();
        }

        @Override
        public String icon() {
            return FTP_TLS.icon();
        }
    };

    public static final Protocol CLOUDFILES = new Protocol() {
        @Override
        public String getName() {
            return Locale.localizedString("Cloud Files", "Mosso");
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Rackspace Cloud Files", "Mosso");
        }

        @Override
        public String getIdentifier() {
            return "cf";
        }

        @Override
        public Type getType() {
            return Type.swift;
        }

        @Override
        public boolean isPortConfigurable() {
            return false;
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String disk() {
            return SWIFT.disk();
        }

        @Override
        public String icon() {
            return SWIFT.icon();
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "mosso", "cloudfiles", "cf"};
        }

        @Override
        public boolean isHostnameConfigurable() {
            return false;
        }

        @Override
        public String getDefaultHostname() {
            return "auth.api.rackspacecloud.com";
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("API Access Key", "Mosso");
        }
    };

    public static final Protocol SWIFT = new Protocol() {
        @Override
        public String getName() {
            return Locale.localizedString("Swift", "Mosso");
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Swift (OpenStack Object Storage)", "Mosso");
        }

        @Override
        public String getIdentifier() {
            return "swift";
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "swift"};
        }

        @Override
        public boolean isHostnameConfigurable() {
            return true;
        }

        @Override
        public String getDefaultHostname() {
            return "auth.api.rackspacecloud.com";
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("API Access Key", "Mosso");
        }
    };

    public static final Protocol GOOGLESTORAGE_SSL = new Protocol() {
        @Override
        public String getName() {
            return "Google Cloud Storage";
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Google Cloud Storage", "S3");
        }

        @Override
        public String getIdentifier() {
            return "gs";
        }

        @Override
        public Type getType() {
            return Type.googlestorage;
        }

        @Override
        public String disk() {
            return "googlestorage";
        }

        @Override
        public boolean isHostnameConfigurable() {
            return false;
        }

        @Override
        public String getDefaultHostname() {
            return "storage.googleapis.com";
        }

        @Override
        public Set<String> getLocations() {
            return new HashSet<String>(Arrays.asList(
                    "US", "EU"
            ));
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public boolean isPortConfigurable() {
            return false;
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String getUsernamePlaceholder() {
            return String.format("%s/x-goog-project-id", Locale.localizedString("Access Key", "S3"));
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("Secret", "S3");
        }

        @Override
        public String favicon() {
            return this.icon();
        }

        @Override
        public boolean validate(final Credentials credentials) {
            // OAuth only requires the project token
            return StringUtils.isNotBlank(credentials.getUsername());
        }
    };
    public static final Protocol SWIFT_KEYSTONE = new Protocol() {
        @Override
        public String getName() {
            return Locale.localizedString("SwiftKeystone", "Mosso");
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Swift (Keystone)", "Mosso");
        }

        @Override
        public String getIdentifier() {
            return "swiftkeystone";
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "swift"};
        }

        @Override
        public boolean isHostnameConfigurable() {
            return true;
        }

        @Override
        public String getDefaultHostname() {
            return "pinga.ect.ufrn.br";
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("API Access Key", "Mosso");
        }
    };
	public static final Protocol SWIFT_FEDERATED_KEYSTONE = new Protocol() {
        @Override
        public String getName() {
            return Locale.localizedString("SwiftFederatedKeystone", "Mosso");
        }

        @Override
        public String getDescription() {
            return Locale.localizedString("Swift (Federated Keystone)", "Mosso");
        }

        @Override
        public String getIdentifier() {
            return "swiftfederatedkeystone";
        }

        @Override
        public Scheme getScheme() {
            return Scheme.https;
        }

        @Override
        public String[] getSchemes() {
            return new String[]{this.getScheme().name(), "swift"};
        }

        @Override
        public boolean isHostnameConfigurable() {
            return true;
        }

        @Override
        public String getDefaultHostname() {
            return "pinga.ect.ufrn.br";
        }

        @Override
        public boolean isWebUrlConfigurable() {
            return false;
        }

        @Override
        public boolean isAnonymousConfigurable() {
            return false;
        }

        @Override
        public String getPasswordPlaceholder() {
            return Locale.localizedString("API Access Key", "Mosso");
        }
    };

	}
