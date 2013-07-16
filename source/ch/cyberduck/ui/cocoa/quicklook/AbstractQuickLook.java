package ch.cyberduck.ui.cocoa.quicklook;

import ch.cyberduck.core.local.Local;

import java.util.List;

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

/**
 * @version $Id: AbstractQuickLook.java 10530 2012-10-22 12:03:11Z dkocher $
 */
public abstract class AbstractQuickLook implements QuickLook {

    @Override
    public void select(final List<Local> files) {
        //
    }

    @Override
    public void willBeginQuickLook() {
        //
    }

    @Override
    public void didEndQuickLook() {
        //
    }
}
