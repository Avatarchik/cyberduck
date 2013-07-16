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
import ch.cyberduck.core.date.CalendarService;
import ch.cyberduck.core.date.Instant;

import org.apache.log4j.Logger;

import java.util.Calendar;

/**
 * @version $Id: TimestampComparisonService.java 10548 2012-10-22 16:14:57Z dkocher $
 */
public class TimestampComparisonService implements ComparisonService {
    private static Logger log = Logger.getLogger(CombinedComparisionService.class);

    private CalendarService calendarService = new CalendarService();

    @Override
    public Comparison compare(final Path p) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Compare timestamp for %s", p.getAbsolute()));
        }
        final PathAttributes attributes = p.attributes();
        if(-1 == attributes.getModificationDate()) {
            if(p.getSession().isReadTimestampSupported()) {
                // Make sure we have a UTC timestamp
                p.readTimestamp();
            }
        }
        if(-1 == attributes.getModificationDate()) {
            log.warn("No modification date available for comparison:" + p);
            return Comparison.UNEQUAL;
        }
        final Calendar remote = calendarService.asDate(attributes.getModificationDate(), Instant.SECOND);
        final Calendar local = calendarService.asDate(p.getLocal().attributes().getModificationDate(), Instant.SECOND);
        if(local.before(remote)) {
            return Comparison.REMOTE_NEWER;
        }
        if(local.after(remote)) {
            return Comparison.LOCAL_NEWER;
        }
        //same timestamp
        return Comparison.EQUAL;
    }
}
