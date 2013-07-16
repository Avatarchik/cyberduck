package ch.cyberduck.core.text;

/*
 * Copyright (c) 2002-2009 David Kocher. All rights reserved.
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

import java.text.Collator;
import java.util.Comparator;

/**
 * @version $Id: NaturalOrderComparator.java 10477 2012-10-19 13:28:31Z dkocher $
 */
public class NaturalOrderComparator implements Comparator<String>, java.io.Serializable {
    private static final long serialVersionUID = -5851677380348435176L;

    private Collator collator = new NaturalOrderCollator();

    @Override
    public int compare(String s1, String s2) {
        return collator.compare(s1, s2);
    }
}