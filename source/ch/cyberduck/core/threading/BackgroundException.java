package ch.cyberduck.core.threading;

/*
 *  Copyright (c) 2006 David Kocher. All rights reserved.
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

import ch.cyberduck.core.Host;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.ftp.FTPException;
import ch.cyberduck.core.i18n.Locale;

import org.apache.commons.lang.StringUtils;
import org.apache.http.StatusLine;
import org.apache.log4j.Logger;
import org.jets3t.service.CloudFrontServiceException;
import org.jets3t.service.ServiceException;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.MessageFormat;

import ch.ethz.ssh2.SFTPException;
import com.amazonaws.AmazonServiceException;
import com.googlecode.sardine.impl.SardineException;
import com.rackspacecloud.client.cloudfiles.FilesException;

/**
 * @version $Id: BackgroundException.java 10935 2013-04-24 15:57:35Z dkocher $
 */
public class BackgroundException extends Exception {
    private static Logger log = Logger.getLogger(BackgroundException.class);

    private static final long serialVersionUID = -6114495291207129418L;

    private String message;

    private Path path;

    private Host host;

    public BackgroundException(Host host, Path path, String message, Throwable cause) {
        super(cause);
        this.host = host;
        this.path = path;
        if(null == message) {
            this.message = Locale.localizedString("Unknown");
        }
        else if(null == path) {
            this.message = StringUtils.chomp(message);
        }
        else {
            try {
                this.message = MessageFormat.format(StringUtils.chomp(message), path.getName());
            }
            catch(IllegalArgumentException e) {
                log.warn(String.format("Error parsing message with format %s", e.getMessage()));
                this.message = StringUtils.chomp(message);
            }
        }
    }

    @Override
    public String getMessage() {
        return Locale.localizedString(message, "Error");
    }

    /**
     * @return The real cause of the exception thrown
     */
    @Override
    public Throwable getCause() {
        final Throwable cause = super.getCause();
        if(null == cause) {
            return this;
        }
        Throwable root = cause.getCause();
        if(null == root) {
            return cause;
        }
        while(root.getCause() != null) {
            root = root.getCause();
        }
        if(StringUtils.isNotBlank(root.getMessage())) {
            return root;
        }
        return cause;
    }

    /**
     * @return What kind of error
     */
    public String getReadableTitle() {
        final Throwable cause = this.getCause();
        if(cause instanceof FTPException) {
            return String.format("FTP %s", Locale.localizedString("Error"));
        }
        if(cause instanceof SFTPException) {
            return String.format("SSH %s", Locale.localizedString("Error"));
        }
        if(cause instanceof ServiceException) {
            return String.format("S3 %s", Locale.localizedString("Error"));
        }
        if(cause instanceof AmazonServiceException) {
            return String.format("IAM %s", Locale.localizedString("Error"));
        }
        if(cause instanceof CloudFrontServiceException) {
            return String.format("CloudFront %s", Locale.localizedString("Error"));
        }
        if(cause instanceof org.apache.http.HttpException) {
            return String.format("HTTP %s", Locale.localizedString("Error"));
        }
        if(cause instanceof SocketException) {
            return String.format("Network %s", Locale.localizedString("Error"));
        }
        if(cause instanceof UnknownHostException) {
            return String.format("DNS %s", Locale.localizedString("Error"));
        }
        if(cause instanceof IOException) {
            return String.format("I/O %s", Locale.localizedString("Error"));
        }
        return Locale.localizedString("Error");
    }

    /**
     * @return Detailed message from the underlying cause.
     */
    public String getDetailedCauseMessage() {
        final Throwable cause = this.getCause();
        StringBuilder buffer = new StringBuilder();
        if(null != cause) {
            if(StringUtils.isNotBlank(cause.getMessage())) {
                String m = StringUtils.chomp(cause.getMessage());
                buffer.append(m);
                if(!m.endsWith(".")) {
                    buffer.append(".");
                }
            }
            if(cause instanceof ServiceException) {
                final ServiceException s3 = (ServiceException) cause;
                if(StringUtils.isNotBlank(s3.getResponseStatus())) {
                    // HTTP method status
                    buffer.append(" ").append(s3.getResponseStatus()).append(".");
                }
                if(StringUtils.isNotBlank(s3.getErrorMessage())) {
                    // S3 protocol message
                    buffer.append(" ").append(s3.getErrorMessage());
                }
            }
            else if(cause instanceof SardineException) {
                final SardineException http = (SardineException) cause;
                if(StringUtils.isNotBlank(http.getResponsePhrase())) {
                    buffer.delete(0, buffer.length());
                    // HTTP method status
                    buffer.append(http.getResponsePhrase()).append(".");
                }
            }
            else if(cause instanceof org.jets3t.service.impl.rest.HttpException) {
                final org.jets3t.service.impl.rest.HttpException http = (org.jets3t.service.impl.rest.HttpException) cause;
                buffer.append(" ").append(http.getResponseCode());
                if(StringUtils.isNotBlank(http.getResponseMessage())) {
                    buffer.append(" ").append(http.getResponseMessage());
                }
            }
            else if(cause instanceof CloudFrontServiceException) {
                final CloudFrontServiceException cf = (CloudFrontServiceException) cause;
                if(StringUtils.isNotBlank(cf.getErrorMessage())) {
                    buffer.append(" ").append(cf.getErrorMessage());
                }
                if(StringUtils.isNotBlank(cf.getErrorDetail())) {
                    buffer.append(" ").append(cf.getErrorDetail());
                }
            }
            else if(cause instanceof FilesException) {
                final FilesException cf = (FilesException) cause;
                final StatusLine status = cf.getHttpStatusLine();
                if(null != status) {
                    if(StringUtils.isNotBlank(status.getReasonPhrase())) {
                        buffer.append(" ").append(status.getReasonPhrase());
                    }
                }
            }
            else if(cause instanceof AmazonServiceException) {
                final AmazonServiceException a = (AmazonServiceException) cause;
                final String status = a.getErrorCode();
                if(StringUtils.isNotBlank(status)) {
                    buffer.append(" ").append(status);
                }
            }
        }
        String message = buffer.toString();
        if(!StringUtils.isEmpty(message)) {
            if(Character.isLetter(message.charAt(message.length() - 1))) {
                message = message + ".";
            }
        }
        return Locale.localizedString(message, "Error");
    }

    /**
     * @return The path accessed when the exception was thrown or null if
     *         the exception is not related to any path
     */
    public Path getPath() {
        return path;
    }

    /**
     * @return The session this exception occurred
     */
    public Host getHost() {
        return host;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(!(o instanceof BackgroundException)) {
            return false;
        }
        BackgroundException that = (BackgroundException) o;
        if(this.getCause() != null ? !this.getCause().equals(that.getCause()) : that.getCause() != null) {
            return false;
        }
        if(message != null ? !message.equals(that.message) : that.message != null) {
            return false;
        }
        if(path != null ? !path.equals(that.path) : that.path != null) {
            return false;
        }
        if(host != null ? !host.equals(that.host) : that.host != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + (this.getCause() != null ? this.getCause().hashCode() : 0);
        return result;
    }
}
