package ch.cyberduck.core.local;

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

import java.util.List;

/**
 * @version $Id: ApplicationFinder.java 10490 2012-10-19 16:16:09Z dkocher $
 */
public interface ApplicationFinder {

    /**
     * @param filename File name
     * @return All of the application bundle identifiers that are capable of handling
     *         the specified content type in the specified roles.
     */
    List<Application> findAll(String filename);

    /**
     * Find application for file type.
     *
     * @param filename File name
     * @return Absolute path to installed application
     */
    Application find(String filename);

    /**
     * @param application Application description
     * @return True if path to the applicaiton is found
     */
    boolean isInstalled(Application application);

    /**
     * @param application Identifier
     * @return Application name
     */
    Application getDescription(String application);
}
