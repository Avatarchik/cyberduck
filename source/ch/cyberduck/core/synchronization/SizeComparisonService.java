package ch.cyberduck.core.synchronization;

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

import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;

import org.apache.log4j.Logger;

/**
 * @version $Id: SizeComparisonService.java 10548 2012-10-22 16:14:57Z dkocher $
 */
public class SizeComparisonService implements ComparisonService {
    private static Logger log = Logger.getLogger(ComparisonService.class);

    @Override
    public Comparison compare(final Path p) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Compare size for %s", p.getAbsolute()));
        }
        final PathAttributes attributes = p.attributes();
        if(attributes.isFile()) {
            if(attributes.getSize() == -1) {
                p.readSize();
            }
            //fist make sure both files are larger than 0 bytes
            if(attributes.getSize() == 0 && p.getLocal().attributes().getSize() == 0) {
                return Comparison.EQUAL;
            }
            if(attributes.getSize() == 0) {
                return Comparison.LOCAL_NEWER;
            }
            if(p.getLocal().attributes().getSize() == 0) {
                return Comparison.REMOTE_NEWER;
            }
            if(attributes.getSize() == p.getLocal().attributes().getSize()) {
                return Comparison.EQUAL;
            }
        }
        //different file size - further comparison check
        return Comparison.UNEQUAL;
    }
}
