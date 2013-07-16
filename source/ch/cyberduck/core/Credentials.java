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

import ch.cyberduck.core.local.Local;

import org.apache.commons.lang.StringUtils;

/**
 * Stores the login credentials
 *
 * @version $Id: Credentials.java 10935 2013-04-24 15:57:35Z dkocher $
 */
public class Credentials {

    /**
     * The login name
     */
    private String user;

    /**
     * The login password
     */
    private transient String password;

    /**
     * If not null, use public key authentication if SSH is the protocol
     */
    private Local identity;

    /**
     * If the credentials should be stored in the Keychain upon successful login
     */
    private boolean keychained;

    /**
     * Default credentials from Preferences
     */
    public Credentials() {
        this(null, null, Preferences.instance().getBoolean("connection.login.useKeychain"));
    }

    /**
     * @param user     Login with this username
     * @param password Passphrase
     */
    public Credentials(String user, String password) {
        this(user, password, Preferences.instance().getBoolean("connection.login.useKeychain"));
    }

    /**
     * @param user     The username to use or null if anonymous
     * @param password The password to use or null if anonymous
     * @param save     if the credential should be added to the keychain uppon successful login
     */
    public Credentials(String user, String password, boolean save) {
        this.keychained = save;
        this.user = user;
        this.password = password;
    }

    /**
     * @return The login identification
     */
    public String getUsername() {
        return this.user;
    }

    public void setUsername(String user) {
        this.user = user;
    }

    /**
     * @return The login secret
     */
    public String getPassword() {
        if(StringUtils.isEmpty(password)) {
            if(this.isAnonymousLogin()) {
                return Preferences.instance().getProperty("connection.login.anon.pass");
            }
        }
        return password;
    }

    public void setPassword(String pass) {
        this.password = pass;
    }

    /**
     * Use this to define if passwords should be added to the keychain
     *
     * @param saved If true, the password of the login is added to the keychain uppon
     *              successfull login
     */
    public void setSaved(boolean saved) {
        this.keychained = saved;
    }

    /**
     * @return true if the password will be added to the system keychain when logged in successfully
     */
    public boolean isSaved() {
        return this.keychained;
    }

    /**
     * @return true if the username is anononymous
     */
    public boolean isAnonymousLogin() {
        final String u = this.getUsername();
        if(StringUtils.isEmpty(u)) {
            return false;
        }
        return Preferences.instance().getProperty("connection.login.anon.name").equals(u);
    }

    /**
     * SSH specific
     *
     * @return true if public key authentication should be used. This is the case, if a
     *         private key file has been specified
     * @see #setIdentity
     */
    public boolean isPublicKeyAuthentication() {
        if(null == this.getIdentity()) {
            return false;
        }
        return this.getIdentity().exists();
    }

    /**
     * The path for the private key file to use for public key authentication; e.g. ~/.ssh/id_rsa
     *
     * @param file Private key file
     */
    public void setIdentity(Local file) {
        this.identity = file;
    }

    /**
     * @return The path to the private key file to use for public key authentication
     */
    public Local getIdentity() {
        return identity;
    }

    /**
     * @param protocol The protocol to verify against.
     * @return True if the login credential are valid for the given protocol.
     */
    public boolean validate(final Protocol protocol) {
        return protocol.validate(this);
    }

    public String getUsernamePlaceholder() {
        return null;
    }

    public String getPasswordPlaceholder() {
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof Credentials)) {
            return false;
        }
        final Credentials that = (Credentials) o;
        if(password != null ? !password.equals(that.password) : that.password != null) {
            return false;
        }
        if(user != null ? !user.equals(that.user) : that.user != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}
