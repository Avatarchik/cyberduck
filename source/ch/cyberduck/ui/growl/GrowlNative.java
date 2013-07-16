package ch.cyberduck.ui.growl;

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

import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.library.Native;

/**
 * @version $Id: GrowlNative.java 10707 2012-12-22 19:14:05Z dkocher $
 */
public final class GrowlNative extends Growl {

    private GrowlNative() {
        Native.load("Growl");
    }

    public static void register() {
        if(Preferences.instance().getBoolean("growl.enable")) {
            if(Factory.VERSION_PLATFORM.matches("10\\.(5|6|7).*")) {
                GrowlFactory.addFactory(Factory.VERSION_PLATFORM, new Factory());
            }
        }
    }

    private static class Factory extends GrowlFactory {
        @Override
        protected Growl create() {
            return new GrowlNative();
        }
    }

    @Override
    public native void setup();

    @Override
    public native void notify(String title, String description);

    @Override
    public native void notifyWithImage(String title, String description, String image);

}
