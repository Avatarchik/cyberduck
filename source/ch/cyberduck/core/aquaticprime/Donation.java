package ch.cyberduck.core.aquaticprime;

/*
 *  Copyright (c) 2009 David Kocher. All rights reserved.
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

import ch.cyberduck.core.library.Native;
import ch.cyberduck.core.local.Local;

import org.apache.log4j.Logger;

/**
 * @version $Id: Donation.java 10557 2012-10-22 17:25:14Z dkocher $
 */
public class Donation extends AbstractLicense {
    private static Logger log = Logger.getLogger(Donation.class);

    public static void register() {
        LicenseFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends LicenseFactory {
        @Override
        protected License open(final Local file) {
            if(file.getName().endsWith(".cyberducklicense")) {
                return new Donation(file);
            }
            return LicenseFactory.EMPTY_LICENSE;
        }

        @Override
        protected License create() {
            return this.open();
        }
    }

    static {
        Native.load("Prime");
    }

    /**
     * @param file The license key file.
     */
    public Donation(Local file) {
        super(file);
    }

    /**
     * @return True if valid license key
     */
    @Override
    public boolean verify() {
        final boolean valid = this.verify(this.getFile().getAbsolute());
        if(valid) {
            if(log.isInfoEnabled()) {
                log.info(String.format("Valid donation key in %s", this.getFile().getAbsolute()));
            }
        }
        else {
            log.warn(String.format("Not a valid donation key in %s", this.getFile().getAbsolute()));
        }
        return valid;
    }

    private native boolean verify(String license);

    @Override
    public String getValue(String property) {
        return this.getValue(this.getFile().getAbsolute(), property);
    }

    private native String getValue(String license, String property);
}
