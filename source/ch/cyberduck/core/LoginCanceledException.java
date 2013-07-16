package ch.cyberduck.core;

import ch.cyberduck.core.i18n.Locale;

/*
 *  Copyright (c) 2006 David Kocher. All rights reserved.
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
 * To be used if a login attempt is canceled by the user
 *
 * @version $Id: LoginCanceledException.java 9665 2012-08-22 13:15:35Z dkocher $
 */
public class LoginCanceledException extends ConnectionCanceledException {
    private static final long serialVersionUID = 3299339665746039518L;

    public LoginCanceledException() {
        super(Locale.localizedString("Login canceled", "Credentials"));
    }
}
