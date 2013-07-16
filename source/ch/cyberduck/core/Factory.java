package ch.cyberduck.core;

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

/**
 * @version $Id: Factory.java 9651 2012-08-22 12:30:52Z dkocher $
 */
public abstract class Factory<T> {

    /**
     * @return A new instance of the type of objects this
     *         factory creates
     */
    protected abstract T create();

    public static abstract class Platform {
        public abstract String toString();

        /**
         * @param regex Identification string
         * @return True if plattform identification matches regular expression
         */
        public boolean matches(String regex) {
            return this.toString().matches(regex);
        }

        @Override
        public boolean equals(Object other) {
            if(null == other) {
                return false;
            }
            if(other instanceof Platform) {
                return other.toString().equals(this.toString());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }
    }

    public static final Platform NATIVE_PLATFORM = new Platform() {
        @Override
        public String toString() {
            return System.getProperty("os.name");
        }
    };

    public static final Platform VERSION_PLATFORM = new Platform() {
        @Override
        public String toString() {
            return System.getProperty("os.version");
        }
    };
}