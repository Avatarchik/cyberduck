package ch.cyberduck.ui;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
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

import ch.cyberduck.core.text.NaturalOrderComparator;
import ch.cyberduck.core.Path;

import java.util.Comparator;

/**
 * @version $Id: FilenameComparator.java 10477 2012-10-19 13:28:31Z dkocher $
 */
public class FilenameComparator extends BrowserComparator {
    private static final long serialVersionUID = -6726865487297853350L;

    private Comparator<String> impl = new NaturalOrderComparator();

    public FilenameComparator(boolean ascending) {
        super(ascending, null);
    }

    @Override
    protected int compareFirst(Path p1, Path p2) {
        if(ascending) {
            return impl.compare(p1.getName(), p2.getName());
        }
        return -impl.compare(p1.getName(), p2.getName());
    }

    @Override
    public String getIdentifier() {
        return "filename";
    }
}
