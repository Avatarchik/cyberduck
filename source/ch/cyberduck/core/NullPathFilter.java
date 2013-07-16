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

/**
 * @version $Id: NullPathFilter.java 9657 2012-08-22 12:44:20Z dkocher $
 */
public class NullPathFilter<E extends AbstractPath> implements PathFilter<E> {

    public boolean accept(E file) {
        return true;
    }

    @Override
    public boolean equals(Object object) {
        if(null == object) {
            return false;
        }
        return object.getClass().equals(this.getClass());
    }

    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }
}
