package ch.cyberduck.core.cf;

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

import ch.cyberduck.core.AbstractPath;
import ch.cyberduck.core.AttributedList;
import ch.cyberduck.core.ConnectionCanceledException;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.StreamListener;
import ch.cyberduck.core.cdn.Distribution;
import ch.cyberduck.core.cloud.CloudPath;
import ch.cyberduck.core.http.DelayedHttpEntityCallable;
import ch.cyberduck.core.http.ResponseOutputStream;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.log4j.Logger;
import org.jets3t.service.utils.ServiceUtils;
import org.w3c.util.DateParser;
import org.w3c.util.InvalidDateException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rackspacecloud.client.cloudfiles.FilesContainerInfo;
import com.rackspacecloud.client.cloudfiles.FilesContainerMetaData;
import com.rackspacecloud.client.cloudfiles.FilesNotFoundException;
import com.rackspacecloud.client.cloudfiles.FilesObject;
import com.rackspacecloud.client.cloudfiles.FilesObjectMetaData;

/**
 * Rackspace Cloud Files Implementation
 *
 * @version $Id: CFPath.java 10889 2013-04-19 15:42:22Z dkocher $
 */
public class CFPath extends CloudPath {
    private static Logger log = Logger.getLogger(CFPath.class);

    private final CFSession session;

    public CFPath(CFSession s, String parent, String name, int type) {
        super(parent, name, type);
        this.session = s;
    }

    public CFPath(CFSession s, String path, int type) {
        super(path, type);
        this.session = s;
    }

    public CFPath(CFSession s, String parent, Local file) {
        super(parent, file);
        this.session = s;
    }

    public <T> CFPath(CFSession s, T dict) {
        super(dict);
        this.session = s;
    }

    @Override
    public CFSession getSession() {
        return session;
    }

    @Override
    public boolean exists() {
        if(super.exists()) {
            return true;
        }
        if(this.isContainer()) {
            try {
                return this.getSession().getClient().containerExists(this.getName());
            }
            catch(HttpException e) {
                log.warn(String.format("Container %s does not exist", this.getName()));
                return false;
            }
            catch(ConnectionCanceledException e) {
                log.warn(e.getMessage());
            }
            catch(IOException e) {
                log.warn(e.getMessage());
            }
        }
        return super.exists();
    }

    @Override
    public void readSize() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Getting size of {0}", "Status"),
                    this.getName()));

            if(this.isContainer()) {
                attributes().setSize(
                        this.getSession().getClient().getContainerInfo(this.getContainerName()).getTotalSize()
                );
            }
            else if(this.attributes().isFile()) {
                attributes().setSize(
                        Long.valueOf(this.getSession().getClient().getObjectMetaData(this.getContainerName(), this.getKey()).getContentLength())
                );
            }
        }
        catch(HttpException e) {
            this.error("Cannot read file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot read file attributes", e);
        }
    }

    @Override
    public void readChecksum() {
        if(this.attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Compute MD5 hash of {0}", "Status"),
                        this.getName()));

                final String checksum = this.getSession().getClient().getObjectMetaData(
                        this.getContainerName(), this.getKey()).getETag();
                attributes().setChecksum(checksum);
                attributes().setETag(checksum);
            }
            catch(HttpException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public void readTimestamp() {
        if(this.attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting timestamp of {0}", "Status"),
                        this.getName()));

                try {
                    attributes().setModificationDate(
                            ServiceUtils.parseRfc822Date(this.getSession().getClient().getObjectMetaData(this.getContainerName(),
                                    this.getKey()).getLastModified()).getTime()
                    );
                }
                catch(ParseException e) {
                    log.error("Failure parsing timestamp", e);
                }
            }
            catch(HttpException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public AttributedList<Path> list(final AttributedList<Path> children) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Listing directory {0}", "Status"),
                    this.getName()));

            if(this.isRoot()) {
                final int limit = Preferences.instance().getInteger("cf.list.limit");
                String marker = null;
                List<FilesContainerInfo> list;
                // List all containers
                do {
                    list = this.getSession().getClient().listContainersInfo(limit, marker);
                    for(FilesContainerInfo container : list) {
                        Path p = new CFPath(this.getSession(), this.getAbsolute(), container.getName(),
                                VOLUME_TYPE | DIRECTORY_TYPE);
                        p.attributes().setSize(container.getTotalSize());
                        p.attributes().setOwner(this.getSession().getClient().getUserName());

                        children.add(p);

                        marker = container.getName();
                    }
                }
                while(list.size() == limit);
            }
            else {
                final int limit = Preferences.instance().getInteger("cf.list.limit");
                String marker = null;
                List<FilesObject> list;
                do {
                    list = this.getSession().getClient().listObjectsStartingWith(this.getContainerName(),
                            this.isContainer() ? StringUtils.EMPTY : this.getKey() + Path.DELIMITER, null, limit, marker, Path.DELIMITER);
                    for(FilesObject object : list) {
                        final Path file = new CFPath(this.getSession(), this.getContainerName(), object.getName(),
                                "application/directory".equals(object.getMimeType()) ? DIRECTORY_TYPE : FILE_TYPE);
                        file.setParent(this);
                        if(file.attributes().isFile()) {
                            file.attributes().setSize(object.getSize());
                            file.attributes().setChecksum(object.getMd5sum());
                            try {
                                final Date modified = DateParser.parse(object.getLastModified());
                                if(null != modified) {
                                    file.attributes().setModificationDate(modified.getTime());
                                }
                            }
                            catch(InvalidDateException e) {
                                log.warn("Not ISO 8601 format:" + e.getMessage());
                            }
                        }
                        if(file.attributes().isDirectory()) {
                            file.attributes().setPlaceholder(true);
                            if(children.contains(file.getReference())) {
                                continue;
                            }
                        }
                        file.attributes().setOwner(this.attributes().getOwner());

                        children.add(file);

                        marker = object.getName();
                    }
                    if(Preferences.instance().getBoolean("cf.list.cdn.preload")) {
                        for(Distribution.Method method : this.getSession().cdn().getMethods(this.getContainerName())) {
                            // Cache CDN configuration
                            this.getSession().cdn().read(this.getSession().cdn().getOrigin(method, this.getContainerName()), method);
                        }
                    }
                }
                while(list.size() == limit);
            }
        }
        catch(HttpException e) {
            log.warn("Listing directory failed:" + e.getMessage());
            children.attributes().setReadable(false);
            if(!session.cache().containsKey(this.getReference())) {
                this.error(e.getMessage(), e);
            }
        }
        catch(IOException e) {
            log.warn("Listing directory failed:" + e.getMessage());
            children.attributes().setReadable(false);
            if(!session.cache().containsKey(this.getReference())) {
                this.error(e.getMessage(), e);
            }
        }
        return children;
    }

    @Override
    public InputStream read(final TransferStatus status) throws IOException {
        try {
            if(status.isResume()) {
                return this.getSession().getClient().getObjectAsRangedStream(this.getContainerName(), this.getKey(),
                        status.getCurrent(), status.getLength());
            }
            return this.getSession().getClient().getObjectAsStream(this.getContainerName(), this.getKey());
        }
        catch(HttpException e) {
            IOException failure = new IOException(e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }

    @Override
    public void download(final BandwidthThrottle throttle, final StreamListener listener,
                         final TransferStatus status) {
        OutputStream out = null;
        InputStream in = null;
        try {
            in = this.read(status);
            out = this.getLocal().getOutputStream(status.isResume());
            this.download(in, out, throttle, listener, status);
        }
        catch(IOException e) {
            this.error("Download failed", e);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public void upload(final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        try {
            String md5sum = null;
            if(Preferences.instance().getBoolean("cf.upload.metadata.md5")) {
                this.getSession().message(MessageFormat.format(Locale.localizedString("Compute MD5 hash of {0}", "Status"),
                        this.getName()));
                md5sum = this.getLocal().attributes().getChecksum();
            }
            MessageDigest digest = null;
            if(!Preferences.instance().getBoolean("cf.upload.metadata.md5")) {
                try {
                    digest = MessageDigest.getInstance("MD5");
                }
                catch(NoSuchAlgorithmException e) {
                    log.error("Failure loading MD5 digest", e);
                }
            }
            InputStream in = null;
            ResponseOutputStream<String> out = null;
            try {
                if(null == digest) {
                    log.warn("MD5 calculation disabled");
                    in = this.getLocal().getInputStream();
                }
                else {
                    in = new DigestInputStream(this.getLocal().getInputStream(), digest);
                }
                out = this.write(status, md5sum);
                this.upload(out, in, throttle, listener, status);
            }
            finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
            if(null != digest && null != out) {
                this.getSession().message(MessageFormat.format(
                        Locale.localizedString("Compute MD5 hash of {0}", "Status"), this.getName()));
                // Obtain locally-calculated MD5 hash.
                String expectedETag = ServiceUtils.toHex(digest.digest());
                // Compare our locally-calculated hash with the ETag returned.
                final String result = out.getResponse();
                if(!expectedETag.equals(result)) {
                    throw new IOException("Mismatch between MD5 hash of uploaded data ("
                            + expectedETag + ") and ETag returned ("
                            + result + ") for object key: "
                            + this.getKey());
                }
                else {
                    if(log.isDebugEnabled()) {
                        log.debug("Object upload was automatically verified, the calculated MD5 hash " +
                                "value matched the ETag returned: " + this.getKey());
                    }
                }
            }
        }
        catch(IOException e) {
            this.error("Upload failed", e);
        }
    }

    @Override
    public OutputStream write(final TransferStatus status) throws IOException {
        return this.write(status, null);
    }

    private ResponseOutputStream<String> write(final TransferStatus status, final String md5sum) throws IOException {
        final HashMap<String, String> metadata = new HashMap<String, String>();
        // Default metadata for new files
        for(String m : Preferences.instance().getList("cf.metadata.default")) {
            if(StringUtils.isBlank(m)) {
                log.warn(String.format("Invalid header %s", m));
                continue;
            }
            if(!m.contains("=")) {
                log.warn(String.format("Invalid header %s", m));
                continue;
            }
            int split = m.indexOf('=');
            String name = m.substring(0, split);
            if(StringUtils.isBlank(name)) {
                log.warn(String.format("Missing key in %s", m));
                continue;
            }
            String value = m.substring(split + 1);
            if(StringUtils.isEmpty(value)) {
                log.warn(String.format("Missing value in %s", m));
                continue;
            }
            metadata.put(name, value);
        }

        // Submit store call to background thread
        final DelayedHttpEntityCallable<String> command = new DelayedHttpEntityCallable<String>() {
            /**
             *
             * @return The ETag returned by the server for the uploaded object
             */
            @Override
            public String call(AbstractHttpEntity entity) throws IOException {
                try {
                    return CFPath.this.getSession().getClient().storeObjectAs(CFPath.this.getContainerName(),
                            CFPath.this.getKey(), entity,
                            metadata, md5sum);
                }
                catch(HttpException e) {
                    IOException failure = new IOException(e.getMessage());
                    failure.initCause(e);
                    throw failure;
                }
            }

            @Override
            public long getContentLength() {
                return status.getLength() - status.getCurrent();
            }
        };
        return this.write(command);
    }

    @Override
    public void mkdir() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Making directory {0}", "Status"),
                    this.getName()));

            if(this.isContainer()) {
                // Create container at top level
                this.getSession().getClient().createContainer(this.getName());
            }
            else {
                // Create virtual directory
                this.getSession().getClient().createFullPath(this.getContainerName(), this.getKey());
            }
        }
        catch(HttpException e) {
            this.error("Cannot create folder {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot create folder {0}", e);
        }
    }

    @Override
    public void delete() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                    this.getName()));

            final String container = this.getContainerName();
            if(attributes().isFile()) {
                this.getSession().getClient().deleteObject(container, this.getKey());
            }
            else if(attributes().isDirectory()) {
                for(AbstractPath i : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    i.delete();
                }
                if(this.isContainer()) {
                    this.getSession().getClient().deleteContainer(container);
                }
                else {
                    try {
                        this.getSession().getClient().deleteObject(container, this.getKey());
                    }
                    catch(FilesNotFoundException e) {
                        // No real placeholder but just a delimiter returned in the object listing.
                        log.warn(e.getMessage());
                    }
                }
            }
        }
        catch(HttpException e) {
            this.error("Cannot delete {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot delete {0}", e);
        }
    }

    @Override
    public void readMetadata() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Reading metadata of {0}", "Status"),
                    this.getName()));

            if(this.attributes().isFile()) {
                final FilesObjectMetaData meta
                        = this.getSession().getClient().getObjectMetaData(this.getContainerName(), this.getKey());
                this.attributes().setMetadata(meta.getMetaData());
            }
            if(this.attributes().isVolume()) {
                final FilesContainerMetaData meta
                        = this.getSession().getClient().getContainerMetaData(this.getContainerName());
                this.attributes().setMetadata(meta.getMetaData());
            }
        }
        catch(HttpException e) {
            this.error("Cannot read file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot read file attributes", e);
        }
    }

    @Override
    public void writeMetadata(final Map<String, String> meta) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Writing metadata of {0}", "Status"),
                    this.getName()));

            if(this.attributes().isFile()) {
                this.getSession().getClient().updateObjectMetadata(this.getContainerName(), this.getKey(), meta);
            }
            else if(this.attributes().isVolume()) {
                this.getSession().getClient().updateContainerMetadata(this.getContainerName(), meta);
            }
        }
        catch(HttpException e) {
            this.error("Cannot write file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot write file attributes", e);
        }
        finally {
            this.attributes().clear(false, false, false, true);
        }
    }

    @Override
    public void rename(final AbstractPath renamed) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Renaming {0} to {1}", "Status"),
                    this.getName(), renamed));

            if(this.attributes().isFile()) {
                this.getSession().getClient().copyObject(this.getContainerName(), this.getKey(),
                        ((CFPath) renamed).getContainerName(), ((CFPath) renamed).getKey());
                this.getSession().getClient().deleteObject(this.getContainerName(), this.getKey());
            }
            else if(this.attributes().isDirectory()) {
                for(Path i : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    i.rename(new CFPath(this.getSession(), renamed.getAbsolute(),
                            i.getName(), i.attributes().getType()));
                }
                try {
                    this.getSession().getClient().deleteObject(this.getContainerName(), this.getKey());
                }
                catch(FilesNotFoundException e) {
                    // No real placeholder but just a delimiter returned in the object listing.
                    log.warn(e.getMessage());
                }
            }
        }
        catch(HttpException e) {
            this.error("Cannot rename {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot rename {0}", e);
        }
    }

    @Override
    public void copy(final AbstractPath copy, final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        if(((Path) copy).getSession().equals(this.getSession())) {
            // Copy on same server
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Copying {0} to {1}", "Status"),
                        this.getName(), copy));

                if(this.attributes().isFile()) {
                    this.getSession().getClient().copyObject(this.getContainerName(), this.getKey(),
                            ((CFPath) copy).getContainerName(), ((CFPath) copy).getKey());
                    listener.bytesSent(this.attributes().getSize());
                    status.setComplete();
                }
            }
            catch(HttpException e) {
                this.error("Cannot copy {0}", e);
            }
            catch(IOException e) {
                this.error("Cannot copy {0}", e);
            }
        }
        else {
            // Copy to different host
            super.copy(copy, throttle, listener, status);
        }
    }

    /**
     * @return Publicy accessible URL of given object
     */
    @Override
    public String toHttpURL() {
        CFSession session = this.getSession();
        for(Distribution.Method method : session.cdn().getMethods(this.getContainerName())) {
            if(session.cdn().isCached(method)) {
                final Distribution distribution = session.cdn().read(session.cdn().getOrigin(method, this.getContainerName()), method);
                return distribution.getURL(this);
            }
        }
        // Storage URL is not accessible
        return null;
    }
}
