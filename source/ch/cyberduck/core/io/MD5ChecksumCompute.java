package ch.cyberduck.core.io;

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

import org.apache.log4j.Logger;
import org.jets3t.service.utils.ServiceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

/**
 * @version $Id: MD5ChecksumCompute.java 10336 2012-10-16 20:38:40Z dkocher $
 */
public class MD5ChecksumCompute implements ChecksumCompute {
    private static final Logger log = Logger.getLogger(MD5ChecksumCompute.class);

    @Override
    public String compute(InputStream in) {
        try {
            return ServiceUtils.toHex(ServiceUtils.computeMD5Hash(in));
        }
        catch(NoSuchAlgorithmException e) {
            log.error(String.format("Checksum failure %s", e.getMessage()));
        }
        catch(IOException e) {
            log.error(String.format("Checksum failure %s", e.getMessage()));
        }
        return null;
    }
}
