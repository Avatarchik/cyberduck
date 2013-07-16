package ch.cyberduck.ui;

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

import ch.cyberduck.core.Path;

import java.io.Serializable;
import java.util.Comparator;

/**
 * The base class for comparators used to sort by column type in the browser
 *
 * @version $Id: BrowserComparator.java 10124 2012-10-14 15:25:01Z dkocher $
 */
public abstract class BrowserComparator implements Comparator<Path>, Serializable {
    private static final long serialVersionUID = -5905031111032653689L;

    protected boolean ascending;
    private BrowserComparator second;

    /**
     * @param ascending The items should be sorted in a ascending manner.
     *                  Usually this means lower numbers first or natural language sorting
     *                  for alphabetic comparators
     * @param second    Second level comparator
     */
    public BrowserComparator(boolean ascending, BrowserComparator second) {
        this.ascending = ascending;
        this.second = second;
    }

    public boolean isAscending() {
        return this.ascending;
    }

    /**
     * @param object Other comparator
     * @return True if the same identifier and ascending boolean value
     * @see #getIdentifier()
     * @see #isAscending()
     */
    @Override
    public boolean equals(Object object) {
        if(object instanceof BrowserComparator) {
            BrowserComparator other = (BrowserComparator) object;
            if(other.getIdentifier().equals(this.getIdentifier())) {
                return other.isAscending() == this.isAscending();
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = (ascending ? 1 : 0);
        result = 31 * result + (second != null ? second.hashCode() : 0);
        return result;
    }

    @Override
    public int compare(Path p1, Path p2) {
        int result = compareFirst(p1, p2);
        if(0 == result && null != second) {
            return second.compareFirst(p1, p2);
        }
        return result;
    }

    protected abstract int compareFirst(Path p1, Path p2);

    /**
     * @return An unique identifier for this comparator
     */
    public abstract String getIdentifier();
}
