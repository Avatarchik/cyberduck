package ch.cyberduck.core;

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

import ch.cyberduck.core.i18n.Locale;

import java.util.Iterator;

/**
 * @version $Id: RendezvousCollection.java 10533 2012-10-22 13:29:55Z dkocher $
 */
public final class RendezvousCollection extends AbstractHostCollection implements RendezvousListener {
    private static final long serialVersionUID = 6468881403370416829L;

    private static final RendezvousCollection RENDEZVOUS_COLLECTION
            = new RendezvousCollection();

    public static RendezvousCollection defaultCollection() {
        return RENDEZVOUS_COLLECTION;
    }

    private final Rendezvous rendezvous;

    private RendezvousCollection() {
        rendezvous = RendezvousFactory.instance();
        rendezvous.addListener(this);
        this.load();
    }

    @Override
    public void serviceResolved(String identifier, Host host) {
        this.collectionItemAdded(host);
    }

    @Override
    public void serviceLost(String servicename) {
        this.collectionItemRemoved(null);
    }

    @Override
    public String getName() {
        return Locale.localizedString("Bonjour");
    }

    @Override
    public Host get(int row) {
        return rendezvous.getService(row);
    }

    @Override
    public int size() {
        return rendezvous.numberOfServices();
    }

    @Override
    public Host remove(int row) {
        return null;
    }

    @Override
    public Object[] toArray() {
        Host[] content = new Host[this.size()];
        int i = 0;
        for(Host host : this) {
            content[i] = host;
        }
        return content;
    }

    @Override
    public Iterator<Host> iterator() {
        return rendezvous.iterator();
    }

    @Override
    public boolean allowsAdd() {
        return false;
    }

    @Override
    public boolean allowsDelete() {
        return false;
    }

    @Override
    public boolean allowsEdit() {
        return false;
    }
}
