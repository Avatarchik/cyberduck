package ch.cyberduck.core.sftp;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
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
import ch.cyberduck.core.Path;
import ch.cyberduck.core.Permission;
import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.Protocol;
import ch.cyberduck.core.StreamListener;
import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.io.IOResumeException;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;

import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.SFTPException;
import ch.ethz.ssh2.SFTPInputStream;
import ch.ethz.ssh2.SFTPOutputStream;
import ch.ethz.ssh2.SFTPv3Client;
import ch.ethz.ssh2.SFTPv3DirectoryEntry;
import ch.ethz.ssh2.SFTPv3FileAttributes;
import ch.ethz.ssh2.SFTPv3FileHandle;

/**
 * @version $Id: SFTPPath.java 10837 2013-04-10 20:42:42Z dkocher $
 */
public class SFTPPath extends Path {
    private static final Logger log = Logger.getLogger(SFTPPath.class);

    private final SFTPSession session;

    public SFTPPath(SFTPSession s, String parent, String name, int type) {
        super(parent, name, type);
        this.session = s;
    }

    public SFTPPath(SFTPSession s, String path, int type) {
        super(path, type);
        this.session = s;
    }

    public SFTPPath(SFTPSession s, String parent, Local file) {
        super(parent, file);
        this.session = s;
    }

    public <T> SFTPPath(SFTPSession s, T dict) {
        super(dict);
        this.session = s;
    }

    @Override
    public SFTPSession getSession() {
        return session;
    }

    @Override
    public AttributedList<Path> list(final AttributedList<Path> children) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Listing directory {0}", "Status"),
                    this.getName()));

            for(SFTPv3DirectoryEntry f : this.getSession().sftp().ls(this.getAbsolute())) {
                if(f.filename.equals(".") || f.filename.equals("..")) {
                    continue;
                }
                SFTPv3FileAttributes attributes = f.attributes;
                SFTPPath p = new SFTPPath(this.getSession(), this.getAbsolute(),
                        f.filename, attributes.isDirectory() ? DIRECTORY_TYPE : FILE_TYPE);
                p.setParent(this);
                p.readAttributes(attributes);
                children.add(p);
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
    public void mkdir() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Making directory {0}", "Status"),
                    this.getName()));

            this.getSession().sftp().mkdir(this.getAbsolute(),
                    Integer.parseInt(new Permission(Preferences.instance().getInteger("queue.upload.permissions.folder.default")).getOctalString(), 8));
        }
        catch(IOException e) {
            this.error("Cannot create folder {0}", e);
        }
    }

    @Override
    public void rename(AbstractPath renamed) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Renaming {0} to {1}", "Status"),
                    this.getName(), renamed));

            if(renamed.exists()) {
                renamed.delete();
            }
            this.getSession().sftp().mv(this.getAbsolute(), renamed.getAbsolute());
        }
        catch(IOException e) {
            this.error("Cannot rename {0}", e);
        }
    }

    @Override
    public void delete() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                    this.getName()));

            if(this.attributes().isFile() || this.attributes().isSymbolicLink()) {
                this.getSession().sftp().rm(this.getAbsolute());
            }
            else if(this.attributes().isDirectory()) {
                for(AbstractPath child : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    child.delete();
                }
                this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                        this.getName()));

                this.getSession().sftp().rmdir(this.getAbsolute());
            }
        }
        catch(IOException e) {
            this.error("Cannot delete {0}", e);
        }
    }

    protected void readAttributes() throws IOException {
        this.readAttributes(this.getSession().sftp().stat(this.getAbsolute()));
    }

    protected void readAttributes(SFTPv3FileAttributes attributes) {
        if(null != attributes.size) {
            if(this.attributes().isFile()) {
                this.attributes().setSize(attributes.size);
            }
        }
        String perm = attributes.getOctalPermissions();
        if(null != perm) {
            try {
                String octal = Integer.toOctalString(attributes.permissions);
                this.attributes().setPermission(new Permission(Integer.parseInt(octal.substring(octal.length() - 4))));
            }
            catch(IndexOutOfBoundsException e) {
                log.warn(String.format("Failure parsing mode:%s", e.getMessage()));
            }
            catch(NumberFormatException e) {
                log.warn(String.format("Failure parsing mode:%s", e.getMessage()));
            }
        }
        if(null != attributes.uid) {
            this.attributes().setOwner(attributes.uid.toString());
        }
        if(null != attributes.gid) {
            this.attributes().setGroup(attributes.gid.toString());
        }
        if(null != attributes.mtime) {
            this.attributes().setModificationDate(Long.parseLong(attributes.mtime.toString()) * 1000L);
        }
        if(null != attributes.atime) {
            this.attributes().setAccessedDate(Long.parseLong(attributes.atime.toString()) * 1000L);
        }
        if(attributes.isSymlink()) {
            try {
                String target = this.getSession().sftp().readLink(this.getAbsolute());
                if(!target.startsWith(String.valueOf(Path.DELIMITER))) {
                    target = Path.normalize(this.getParent().getAbsolute() + String.valueOf(Path.DELIMITER) + target);
                }
                this.setSymlinkTarget(target);
                SFTPv3FileAttributes targetAttributes = this.getSession().sftp().stat(target);
                if(targetAttributes.isDirectory()) {
                    this.attributes().setType(SYMBOLIC_LINK_TYPE | DIRECTORY_TYPE);
                }
                else if(targetAttributes.isRegularFile()) {
                    this.attributes().setType(SYMBOLIC_LINK_TYPE | FILE_TYPE);
                }
            }
            catch(IOException e) {
                log.warn(String.format("Cannot read symbolic link target of %s:%s", this.getAbsolute(), e.getMessage()));
                this.attributes().setType(FILE_TYPE);
            }
        }
    }

    protected void writeAttributes(SFTPv3FileAttributes attributes) throws IOException {
        this.getSession().sftp().setstat(this.getAbsolute(), attributes);
    }

    @Override
    public void readSize() {
        if(this.attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting size of {0}", "Status"),
                        this.getName()));

                this.readAttributes();
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public void readTimestamp() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Getting timestamp of {0}", "Status"),
                    this.getName()));

            this.readAttributes();
        }
        catch(IOException e) {
            this.error("Cannot read file attributes", e);
        }
    }

    @Override
    public void readUnixPermission() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Getting permission of {0}", "Status"),
                    this.getName()));

            this.readAttributes();
        }
        catch(IOException e) {
            this.error("Cannot read file attributes", e);
        }
    }

    @Override
    public void writeOwner(String owner) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Changing owner of {0} to {1}", "Status"),
                    this.getName(), owner));

            SFTPv3FileAttributes attr = new SFTPv3FileAttributes();
            attr.uid = new Integer(owner);
            this.writeAttributes(attr);
        }
        catch(IOException e) {
            this.error("Cannot change owner", e);
        }
    }

    @Override
    public void writeGroup(String group) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Changing group of {0} to {1}", "Status"),
                    this.getName(), group));

            SFTPv3FileAttributes attr = new SFTPv3FileAttributes();
            attr.gid = new Integer(group);
            this.writeAttributes(attr);
        }
        catch(IOException e) {
            this.error("Cannot change group", e);
        }
    }

    @Override
    public void writeUnixPermission(Permission permission) {
        try {
            this.getSession().check();
            this.writeUnixPermissionImpl(permission);
        }
        catch(IOException e) {
            this.error("Cannot change permissions", e);
        }
    }

    private void writeUnixPermissionImpl(final Permission permission) throws IOException {
        this.getSession().message(MessageFormat.format(Locale.localizedString("Changing permission of {0} to {1}", "Status"),
                this.getName(), permission.getOctalString()));

        try {
            SFTPv3FileAttributes attr = new SFTPv3FileAttributes();
            attr.permissions = Integer.parseInt(permission.getOctalString(), 8);
            this.writeAttributes(attr);
        }
        catch(SFTPException ignore) {
            // We might not be able to change the attributes if we are not the owner of the file
            log.warn(ignore.getMessage());
        }
        finally {
            this.attributes().clear(false, false, true, false);
        }
    }

    @Override
    public void writeTimestamp(long created, long modified, long accessed) {
        try {
            this.writeModificationDateImpl(modified);
        }
        catch(IOException e) {
            this.error("Cannot change timestamp", e);
        }
    }

    private void writeModificationDateImpl(long modified) throws IOException {
        this.getSession().message(MessageFormat.format(Locale.localizedString("Changing timestamp of {0} to {1}", "Status"),
                this.getName(), UserDateFormatterFactory.get().getShortFormat(modified)));
        try {
            SFTPv3FileAttributes attrs = new SFTPv3FileAttributes();
            int t = (int) (modified / 1000);
            // We must both set the accessed and modified time. See AttribFlags.SSH_FILEXFER_ATTR_V3_ACMODTIME
            attrs.atime = t;
            attrs.mtime = t;
            this.writeAttributes(attrs);
        }
        catch(SFTPException ignore) {
            // We might not be able to change the attributes if we are not the owner of the file
            log.warn(ignore.getMessage());
        }
        finally {
            this.attributes().clear(true, false, false, false);
        }
    }

    @Override
    public InputStream read(final TransferStatus status) throws IOException {
        InputStream in = null;
        if(Preferences.instance().getProperty("ssh.transfer").equals(Protocol.SFTP.getIdentifier())) {
            final SFTPv3FileHandle handle = this.getSession().sftp().openFileRO(this.getAbsolute());
            in = new SFTPInputStream(handle);
            if(status.isResume()) {
                log.info(String.format("Skipping %d bytes", status.getCurrent()));
                final long skipped = in.skip(status.getCurrent());
                if(skipped < status.getCurrent()) {
                    throw new IOResumeException(String.format("Skipped %d bytes instead of %d", skipped, status.getCurrent()));
                }
            }
            // No parallel requests if the file size is smaller than the buffer.
            this.getSession().sftp().setRequestParallelism(
                    (int) (status.getLength() / Preferences.instance().getInteger("connection.chunksize")) + 1
            );
        }
        else if(Preferences.instance().getProperty("ssh.transfer").equals(Protocol.SCP.getIdentifier())) {
            SCPClient scp = this.getSession().openScp();
            scp.setCharset(this.getSession().getEncoding());
            in = scp.get(this.getAbsolute());
        }
        return in;
    }

    @Override
    public void download(BandwidthThrottle throttle, StreamListener listener,
                         final TransferStatus status) {
        InputStream in = null;
        OutputStream out = null;
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
    public void symlink(String target) {
        if(log.isDebugEnabled()) {
            log.debug("symlink:" + target);
        }
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Uploading {0}", "Status"),
                    this.getName()));

            this.getSession().sftp().createSymlink(this.getAbsolute(), target);
        }
        catch(IOException e) {
            this.error("Cannot create file {0}", e);
        }
    }

    @Override
    public OutputStream write(final TransferStatus status) throws IOException {
        final String mode = Preferences.instance().getProperty("ssh.transfer");
        if(mode.equals(Protocol.SFTP.getIdentifier())) {
            SFTPv3FileHandle handle;
            if(status.isResume() && this.exists()) {
                handle = this.getSession().sftp().openFile(this.getAbsolute(),
                        SFTPv3Client.SSH_FXF_WRITE | SFTPv3Client.SSH_FXF_APPEND, null);
            }
            else {
                handle = this.getSession().sftp().openFile(this.getAbsolute(),
                        SFTPv3Client.SSH_FXF_CREAT | SFTPv3Client.SSH_FXF_TRUNC | SFTPv3Client.SSH_FXF_WRITE, null);
            }
            final OutputStream out = new SFTPOutputStream(handle);
            if(status.isResume()) {
                long skipped = ((SFTPOutputStream) out).skip(status.getCurrent());
                log.info(String.format("Skipping %d bytes", skipped));
                if(skipped < status.getCurrent()) {
                    throw new IOResumeException(String.format("Skipped %d bytes instead of %d", skipped, status.getCurrent()));
                }
            }
            // No parallel requests if the file size is smaller than the buffer.
            this.getSession().sftp().setRequestParallelism(
                    (int) (status.getLength() / Preferences.instance().getInteger("connection.chunksize")) + 1
            );
            return out;
        }
        else if(mode.equals(Protocol.SCP.getIdentifier())) {
            SCPClient scp = this.getSession().openScp();
            scp.setCharset(this.getSession().getEncoding());
            return scp.put(this.getName(), status.getLength(),
                    this.getParent().getAbsolute(),
                    "0" + this.attributes().getPermission().getOctalString());
        }
        throw new IOException("Unknown transfer mode:" + mode);
    }

    @Override
    public void upload(final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = this.getLocal().getInputStream();
            out = this.write(status);
            this.upload(out, in, throttle, listener, status);
        }
        catch(IOException e) {
            this.error("Upload failed", e);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public boolean touch() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Uploading {0}", "Status"),
                    this.getName()));

            SFTPv3FileAttributes attr = new SFTPv3FileAttributes();
            Permission permission = new Permission(Preferences.instance().getInteger("queue.upload.permissions.file.default"));
            attr.permissions = Integer.parseInt(permission.getOctalString(), 8);
            this.getSession().sftp().createFile(this.getAbsolute(), attr);
            try {
                // Even if specified above when creating the file handle, we still need to update the
                // permissions after the creating the file. SSH_FXP_OPEN does not support setting
                // attributes in version 4 or lower.
                this.writeUnixPermissionImpl(permission);
            }
            catch(SFTPException ignore) {
                log.warn(ignore.getMessage());
            }
            return true;
        }
        catch(IOException e) {
            this.error("Cannot create file {0}", e);
            return false;
        }
    }
}
