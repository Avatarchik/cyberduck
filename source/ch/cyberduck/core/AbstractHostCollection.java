package ch.cyberduck.core;

/*
 *  Copyright (c) 2008 David Kocher. All rights reserved.
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

import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.text.NaturalOrderComparator;

import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @version $Id: AbstractHostCollection.java 10477 2012-10-19 13:28:31Z dkocher $
 */
public abstract class AbstractHostCollection extends Collection<Host> implements EditableCollection {
    private static final long serialVersionUID = -255801158019850767L;

    private static Logger log = Logger.getLogger(AbstractHostCollection.class);

    private static final AbstractHostCollection EMPTY = new AbstractHostCollection() {
        private static final long serialVersionUID = -8444415684736364173L;

        @Override
        public String getName() {
            return Locale.localizedString("None");
        }
    };

    public static AbstractHostCollection empty() {
        return EMPTY;
    }

    public AbstractHostCollection() {
        super();
    }

    public AbstractHostCollection(java.util.Collection<Host> c) {
        super(c);
    }

    /**
     * @return Group label
     */
    public abstract String getName();

    /**
     * @param h Bookmark
     * @return User comment for bookmark or null
     */
    public String getComment(final Host h) {
        if(StringUtils.isNotBlank(h.getComment())) {
            return StringUtils.remove(StringUtils.remove(h.getComment(), CharUtils.LF), CharUtils.CR);
        }
        return null;
    }

    @Override
    public boolean addAll(java.util.Collection<? extends Host> c) {
        List<Host> temporary = new ArrayList<Host>();
        for(Host host : c) {
            if(temporary.contains(host)) {
                log.warn(String.format("Reset UUID of duplicate in collection for %s", host));
                host.setUuid(null);
            }
            temporary.add(host);
        }
        return super.addAll(temporary);
    }

    @Override
    public boolean add(final Host host) {
        if(this.contains(host)) {
            log.warn(String.format("Reset UUID of duplicate in collection for %s", host));
            host.setUuid(null);
        }
        return super.add(host);
    }

    @Override
    public void add(int row, Host host) {
        if(this.contains(host)) {
            log.warn(String.format("Reset UUID of duplicate in collection for %s", host));
            host.setUuid(null);
        }
        super.add(row, host);
    }

    @Override
    public void collectionItemAdded(Host item) {
        this.sort();
        super.collectionItemAdded(item);
    }

    @Override
    public void collectionItemRemoved(Host item) {
        this.sort();
        super.collectionItemRemoved(item);
    }

    private Comparator<String> comparator = new NaturalOrderComparator();

    public void sortByNickname() {
        this.sort(new Comparator<Host>() {
            @Override
            public int compare(Host o1, Host o2) {
                return comparator.compare(o1.getNickname(), o2.getNickname());
            }
        });
    }

    public void sortByHostname() {
        this.sort(new Comparator<Host>() {
            @Override
            public int compare(Host o1, Host o2) {
                return comparator.compare(o1.getHostname(), o2.getHostname());
            }
        });
    }

    public void sortByProtocol() {
        this.sort(new Comparator<Host>() {
            @Override
            public int compare(Host o1, Host o2) {
                return comparator.compare(o1.getProtocol().getProvider(), o2.getProtocol().getProvider());
            }
        });
    }

    public void sort(Comparator<Host> comparator) {
        Collections.sort(FolderBookmarkCollection.favoritesCollection(), comparator);
        // Save new index
        this.save();
    }

    protected void sort() {
        //
    }

    /**
     * Lookup bookmark by UUID
     *
     * @param uuid Identifier of bookmark
     * @return Null if not found
     */
    public Host lookup(final String uuid) {
        for(Host bookmark : this) {
            if(bookmark.getUuid().equals(uuid)) {
                return bookmark;
            }
        }
        return null;
    }

    /**
     * Add new bookmark to the collection
     *
     * @return True if bookmark collection can be extended
     */
    @Override
    public boolean allowsAdd() {
        return true;
    }

    /**
     * Remove a bookmark from the collection
     *
     * @return True if bookmarks can be removed
     */
    @Override
    public boolean allowsDelete() {
        return true;
    }

    /**
     * Edit the bookmark configuration
     *
     * @return True if bookmarks can be edited
     */
    @Override
    public boolean allowsEdit() {
        return true;
    }

    public void save() {
        // Not persistent by default
    }

    protected void load(Collection<Host> c) {
        this.addAll(c);
        this.collectionLoaded();
    }
}
