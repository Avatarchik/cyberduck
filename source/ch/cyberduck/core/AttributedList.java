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

import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A sortable list with a map to lookup values by key.
 *
 * @version $Id: AttributedList.java 10305 2012-10-16 12:17:22Z dkocher $
 */
public class AttributedList<E extends AbstractPath> extends CopyOnWriteArrayList<E> {
    private static final Logger log = Logger.getLogger(Cache.class);

    private static final long serialVersionUID = 8900332123622028341L;

    /**
     * Metadata of file listing
     */
    private AttributedListAttributes<E> attributes
            = new AttributedListAttributes<E>();

    /**
     * Initialize an attributed list with default attributes
     */
    public AttributedList() {
        //
    }

    /**
     * @param collection Default content
     */
    public AttributedList(java.util.Collection<E> collection) {
        this.addAll(collection);
    }

    public static <T extends AbstractPath> AttributedList<T> emptyList() {
        return new AttributedList<T>();
    }

    /**
     * Metadata of the list.
     *
     * @return File attributes
     */
    public AttributedListAttributes<E> attributes() {
        return attributes;
    }

    /**
     * Additional key,value table to lookup paths by reference
     */
    private Map<PathReference, E> references
            = new ConcurrentHashMap<PathReference, E>();

    @Override
    public boolean add(E path) {
        final AbstractPath previous = references.put(path.getReference(), path);
        if(null != previous) {
            log.warn(String.format("Replacing %s with %s in file listing.", previous, path));
        }
        return super.add(path);
    }

    @Override
    public boolean addAll(java.util.Collection<? extends E> c) {
        for(E path : c) {
            final AbstractPath previous = references.put(path.getReference(), path);
            if(null != previous) {
                log.warn(String.format("Replacing %s with %s in file listing.", previous, path));
            }
        }
        return super.addAll(c);
    }

    public E get(PathReference reference) {
        return references.get(reference);
    }

    public boolean contains(PathReference reference) {
        return references.containsKey(reference);
    }

    public int indexOf(PathReference reference) {
        return super.indexOf(references.get(reference));
    }

    /**
     * The CopyOnWriteArrayList iterator does not support remove but the sort implementation
     * makes use of it. Provide our own implementation here to circumvent.
     *
     * @param comparator The comparator to use
     * @see java.util.Collections#sort(java.util.List, java.util.Comparator)
     * @see java.util.concurrent.CopyOnWriteArrayList#iterator()
     */
    public void sort(Comparator<E> comparator) {
        if(null == comparator) {
            return;
        }
        // Because AttributedList is a CopyOnWriteArrayList we cannot use Collections#sort
        E[] sorted = (E[]) this.toArray(new AbstractPath[this.size()]);
        Arrays.sort(sorted, comparator);
        for(int i = 0; i < sorted.length; i++) {
            this.set(i, sorted[i]);
        }
    }

    public AttributedList<E> filter(final Comparator comparator, final PathFilter filter) {
        boolean needsSorting = false;
        if(null != comparator) {
            needsSorting = !attributes.getComparator().equals(comparator);
        }
        boolean needsFiltering = false;
        if(null != filter) {
            needsFiltering = !attributes.getFilter().equals(filter);
        }
        if(needsSorting) {
            // Do not sort when the list has not been filtered yet
            if(!needsFiltering) {
                this.sort(comparator);
            }
            // Saving last sorting comparator
            attributes.setComparator(comparator);
        }
        if(needsFiltering) {
            // Add previously hidden files to children
            final List<E> hidden = attributes.getHidden();
            this.addAll(hidden);
            // Clear the previously set of hidden files
            hidden.clear();
            for(E child : this) {
                if(!filter.accept(child)) {
                    // Child not accepted by filter; add to cached hidden files
                    attributes.addHidden(child);
                    // Remove hidden file from current file listing
                    this.remove(child);
                }
            }
            // Saving last filter
            attributes.setFilter(filter);
            // Sort again because the list has changed
            this.sort(comparator);
        }
        return this;
    }

    /**
     * Clear the list and all references.
     */
    @Override
    public void clear() {
        references.clear();
        attributes.clear();
        super.clear();
    }
}