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
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.Scheme;

/**
 * @version $Id: AnalyticsProvider.java 10603 2012-11-23 18:07:42Z dkocher $
 */
public interface AnalyticsProvider {

    String getName();

    String getSetup(Protocol protocol, Scheme method, String container,
                    Credentials credentials);
}
