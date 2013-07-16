package ch.cyberduck.core;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
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

import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.local.LocalFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * @version $Id: HistoryCollection.java 10343 2012-10-17 15:31:47Z dkocher $
 */
public class HistoryCollection extends AbstractFolderHostCollection {
    private static final long serialVersionUID = 2270155702956300755L;

    private static final HistoryCollection HISTORY_COLLECTION = new HistoryCollection(
            LocalFactory.createLocal(Preferences.instance().getProperty("application.support.path"), "History")
    );

    public HistoryCollection(Local f) {
        super(f);
    }

    /**
     * @return Singleton instance
     */
    public static HistoryCollection defaultCollection() {
        return HISTORY_COLLECTION;
    }

    @Override
    public Local getFile(Host bookmark) {
        return LocalFactory.createLocal(folder, bookmark.getNickname(true) + ".duck");
    }

    @Override
    public String getComment(Host host) {
        Date timestamp = host.getTimestamp();
        if(null != timestamp) {
            // Set comment to timestamp when server was last accessed
            return UserDateFormatterFactory.get().getLongFormat(timestamp.getTime());
        }
        // There might be files from previous versions that have no timestamp yet.
        return null;
    }

    /**
     * Does not allow duplicate entries.
     *
     * @param row      Row number
     * @param bookmark Bookmark
     */
    @Override
    public void add(int row, Host bookmark) {
        if(this.contains(bookmark)) {
            this.remove(bookmark);
        }
        super.add(row, bookmark);
    }

    /**
     * Does not allow duplicate entries.
     *
     * @param bookmark Bookmark
     * @return Always true
     */
    @Override
    public boolean add(Host bookmark) {
        if(this.contains(bookmark)) {
            this.remove(bookmark);
        }
        return super.add(bookmark);
    }

    /**
     * Sort by timestamp of bookmark file.
     */
    @Override
    protected void sort() {
        Collections.sort(this, new Comparator<Host>() {
            @Override
            public int compare(Host o1, Host o2) {
                if(null == o1.getTimestamp()) {
                    return 1;
                }
                if(null == o2.getTimestamp()) {
                    return -1;
                }
                return -o1.getTimestamp().compareTo(o2.getTimestamp());
            }
        });
    }

    /**
     * Does not allow manual additions
     *
     * @return False
     */
    @Override
    public boolean allowsAdd() {
        return false;
    }

    /**
     * Does not allow editing entries
     *
     * @return False
     */
    @Override
    public boolean allowsEdit() {
        return false;
    }
}
