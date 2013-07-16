package ch.cyberduck.ui;

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

import ch.cyberduck.core.Collection;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathFactory;
import ch.cyberduck.core.Session;
import ch.cyberduck.core.SessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version $Id: PathPasteboard.java 9648 2012-08-22 12:09:26Z dkocher $
 */
public final class PathPasteboard extends Collection<Path> implements Pasteboard<Path> {

    private static Map<Session, PathPasteboard> instances = new HashMap<Session, PathPasteboard>() {
        @Override
        public boolean isEmpty() {
            for(PathPasteboard pasteboard : this.values()) {
                if(!pasteboard.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    };

    private static final long serialVersionUID = -6390582952938739270L;

    private boolean cut;

    public void setCut(boolean cut) {
        this.cut = cut;
    }

    public void setCopy(boolean copy) {
        this.cut = !copy;
    }

    public boolean isCut() {
        return cut;
    }

    public boolean isCopy() {
        return !cut;
    }

    /**
     * Factory to create a pasteboard for a session
     *
     * @param session Session instance
     * @return Pasteboard for a given session
     */
    public static PathPasteboard getPasteboard(final Session session) {
        if(!instances.containsKey(session)) {
            instances.put(session, new PathPasteboard(session));
        }
        return instances.get(session);
    }

    /**
     * @return All available pasteboards
     */
    public static List<PathPasteboard> allPasteboards() {
        return new ArrayList<PathPasteboard>(instances.values());
    }

    private Session session;

    private PathPasteboard(Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    /**
     * @return Content of pasteboard with a new session
     */
    public List<Path> copy() {
        return this.copy(SessionFactory.createSession(session.getHost()));
    }

    /**
     * Get content of pasteboard with a given session
     *
     * @param session Session to use
     * @return Content of pasteboard
     */
    public List<Path> copy(final Session session) {
        List<Path> content = new ArrayList<Path>();
        for(Path path : this) {
            content.add(PathFactory.createPath(session, path.getAsDictionary()));
        }
        return content;
    }

    /**
     * Delete this pasteboard
     */
    public void delete() {
        instances.remove(session);
    }
}
