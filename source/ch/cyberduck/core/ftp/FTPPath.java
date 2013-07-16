package ch.cyberduck.core.ftp;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
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
import ch.cyberduck.core.StreamListener;
import ch.cyberduck.core.date.MDTMMillisecondsDateFormatter;
import ch.cyberduck.core.date.MDTMSecondsDateFormatter;
import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ProxyInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPCommand;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileEntryParser;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @version $Id: FTPPath.java 10862 2013-04-16 17:05:35Z dkocher $
 */
public class FTPPath extends Path {
    private static final Logger log = Logger.getLogger(FTPPath.class);

    private final FTPSession session;

    public FTPPath(FTPSession s, String parent, String name, int type) {
        super(parent, name, type);
        this.session = s;
    }

    public FTPPath(FTPSession s, String path, int type) {
        super(path, type);
        this.session = s;
    }

    public FTPPath(FTPSession s, String parent, Local file) {
        super(parent, file);
        this.session = s;
    }

    public <T> FTPPath(FTPSession s, T dict) {
        super(dict);
        this.session = s;
    }

    @Override
    public FTPSession getSession() {
        return session;
    }

    /**
     *
     */
    private abstract static class DataConnectionAction {
        public abstract boolean run() throws IOException;
    }

    /**
     * @param action Action that needs to open a data connection
     * @return True if action was successful
     * @throws IOException I/O error
     */
    private boolean data(final DataConnectionAction action) throws IOException {
        try {
            // Make sure to always configure data mode because connect event sets defaults.
            if(this.getSession().getConnectMode().equals(FTPConnectMode.PASV)) {
                this.getSession().getClient().enterLocalPassiveMode();
            }
            else if(this.getSession().getConnectMode().equals(FTPConnectMode.PORT)) {
                this.getSession().getClient().enterLocalActiveMode();
            }
            return action.run();
        }
        catch(SocketTimeoutException failure) {
            log.warn("Timeout opening data socket:" + failure.getMessage());
            // Fallback handling
            if(Preferences.instance().getBoolean("ftp.connectmode.fallback")) {
                this.getSession().interrupt();
                this.getSession().check();
                try {
                    return this.fallback(action);
                }
                catch(IOException e) {
                    this.getSession().interrupt();
                    log.warn("Connect mode fallback failed:" + e.getMessage());
                    // Throw original error message
                    throw failure;
                }
            }
        }
        return false;
    }

    /**
     * @param action Action that needs to open a data connection
     * @return True if action was successful
     * @throws IOException I/O error
     */
    private boolean fallback(final DataConnectionAction action) throws IOException {
        // Fallback to other connect mode
        if(this.getSession().getClient().getDataConnectionMode() == FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE) {
            log.warn("Fallback to active data connection");
            this.getSession().getClient().enterLocalActiveMode();
        }
        else if(this.getSession().getClient().getDataConnectionMode() == FTPClient.ACTIVE_LOCAL_DATA_CONNECTION_MODE) {
            log.warn("Fallback to passive data connection");
            this.getSession().getClient().enterLocalPassiveMode();
        }
        final boolean result = action.run();
        // No I/O failure. Switch mode in bookmark.
        if(this.getSession().getClient().getDataConnectionMode() == FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE) {
            this.getSession().getHost().setFTPConnectMode(FTPConnectMode.PORT);
        }
        else if(this.getSession().getClient().getDataConnectionMode() == FTPClient.PASSIVE_LOCAL_DATA_CONNECTION_MODE) {
            this.getSession().getHost().setFTPConnectMode(FTPConnectMode.PASV);
        }
        return result;
    }

    @Override
    public AttributedList<Path> list(final AttributedList<Path> children) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Listing directory {0}", "Status"),
                    this.getName()));

            // Cached file parser determined from SYST response with the timezone set from the bookmark
            final FTPFileEntryParser parser = this.getSession().getFileParser();
            boolean success = false;
            try {
                if(this.getSession().isStatListSupportedEnabled()) {
                    int response = this.getSession().getClient().stat(this.getAbsolute());
                    if(FTPReply.isPositiveCompletion(response)) {
                        String[] reply = this.getSession().getClient().getReplyStrings();
                        final List<String> result = new ArrayList<String>(reply.length);
                        for(final String line : reply) {
                            //Some servers include the status code for every line.
                            if(line.startsWith(String.valueOf(response))) {
                                try {
                                    result.add(line.substring(line.indexOf(response) + line.length() + 1).trim());
                                }
                                catch(IndexOutOfBoundsException e) {
                                    log.error(String.format("Failed parsing line %s", line), e);
                                }
                            }
                            else {
                                result.add(StringUtils.stripStart(line, null));
                            }
                        }
                        success = this.parseListResponse(children, parser, result);
                    }
                    else {
                        this.getSession().setStatListSupportedEnabled(false);
                    }
                }
            }
            catch(IOException e) {
                log.warn("Command STAT failed with I/O error:" + e.getMessage());
                this.getSession().interrupt();
                this.getSession().check();
            }
            if(!success || children.isEmpty()) {
                success = this.data(new DataConnectionAction() {
                    @Override
                    public boolean run() throws IOException {
                        if(!getSession().getClient().changeWorkingDirectory(getAbsolute())) {
                            throw new FTPException(getSession().getClient().getReplyString());
                        }
                        if(!getSession().getClient().setFileType(FTPClient.ASCII_FILE_TYPE)) {
                            // Set transfer type for traditional data socket file listings. The data transfer is over the
                            // data connection in type ASCII or type EBCDIC.
                            throw new FTPException(getSession().getClient().getReplyString());
                        }
                        boolean success = false;
                        // STAT listing failed or empty
                        if(getSession().isMlsdListSupportedEnabled()
                                // Note that there is no distinct FEAT output for MLSD.
                                // The presence of the MLST feature indicates that both MLST and MLSD are supported.
                                && getSession().getClient().isFeatureSupported(FTPCommand.MLST)) {
                            success = parseMlsdResponse(children, getSession().getClient().list(FTPCommand.MLSD));
                            if(!success) {
                                getSession().setMlsdListSupportedEnabled(false);
                            }
                        }
                        if(!success) {
                            // MLSD listing failed or not enabled
                            if(getSession().isExtendedListEnabled()) {
                                try {
                                    success = parseListResponse(children, parser, getSession().getClient().list(FTPCommand.LIST, "-a"));
                                }
                                catch(FTPException e) {
                                    getSession().setExtendedListEnabled(false);
                                }
                            }
                            if(!success) {
                                // LIST -a listing failed or not enabled
                                success = parseListResponse(children, parser, getSession().getClient().list(FTPCommand.LIST));
                            }
                        }
                        return success;
                    }
                });
            }
            for(Path child : children) {
                if(child.attributes().isSymbolicLink()) {
                    if(this.getSession().getClient().changeWorkingDirectory(child.getAbsolute())) {
                        child.attributes().setType(SYMBOLIC_LINK_TYPE | DIRECTORY_TYPE);
                    }
                    else {
                        // Try if CWD to symbolic link target succeeds
                        if(this.getSession().getClient().changeWorkingDirectory(child.getSymlinkTarget().getAbsolute())) {
                            // Workdir change succeeded
                            child.attributes().setType(SYMBOLIC_LINK_TYPE | DIRECTORY_TYPE);
                        }
                        else {
                            child.attributes().setType(SYMBOLIC_LINK_TYPE | FILE_TYPE);
                        }
                    }
                }
            }
            if(!success) {
                // LIST listing failed
                log.error("No compatible file listing method found");
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

    /**
     * The "facts" for a file in a reply to a MLSx command consist of
     * information about that file.  The facts are a series of keyword=value
     * pairs each followed by semi-colon (";") characters.  An individual
     * fact may not contain a semi-colon in its name or value.  The complete
     * series of facts may not contain the space character.  See the
     * definition or "RCHAR" in section 2.1 for a list of the characters
     * that can occur in a fact value.  Not all are applicable to all facts.
     * <p/>
     * A sample of a typical series of facts would be: (spread over two
     * lines for presentation here only)
     * <p/>
     * size=4161;lang=en-US;modify=19970214165800;create=19961001124534;
     * type=file;x.myfact=foo,bar;
     * <p/>
     * This document defines a standard set of facts as follows:
     * <p/>
     * size       -- Size in octets
     * modify     -- Last modification time
     * create     -- Creation time
     * type       -- Entry type
     * unique     -- Unique id of file/directory
     * perm       -- File permissions, whether read, write, execute is
     * allowed for the login id.
     * lang       -- Language of the file name per IANA [11] registry.
     * media-type -- MIME media-type of file contents per IANA registry.
     * charset    -- Character set per IANA registry (if not UTF-8)
     *
     * @param response The "facts" for a file in a reply to a MLSx command
     * @return Parsed keys and values
     */
    protected Map<String, Map<String, String>> parseFacts(String[] response) {
        Map<String, Map<String, String>> files = new HashMap<String, Map<String, String>>();
        for(String line : response) {
            files.putAll(this.parseFacts(line));
        }
        return files;
    }

    protected Map<String, Map<String, String>> parseFacts(String line) {
        final Pattern p = Pattern.compile("\\s?(\\S+\\=\\S+;)*\\s(.*)");
        final Matcher result = p.matcher(line);
        Map<String, Map<String, String>> file = new HashMap<String, Map<String, String>>();
        if(result.matches()) {
            final String filename = result.group(2);
            final Map<String, String> facts = new HashMap<String, String>();
            for(String fact : result.group(1).split(";")) {
                String key = StringUtils.substringBefore(fact, "=");
                if(StringUtils.isBlank(key)) {
                    continue;
                }
                String value = StringUtils.substringAfter(fact, "=");
                if(StringUtils.isBlank(value)) {
                    continue;
                }
                facts.put(key.toLowerCase(java.util.Locale.ENGLISH), value);
            }
            file.put(filename, facts);
            return file;
        }
        log.warn("No match for " + line);
        return null;
    }

    /**
     * Parse response of MLSD
     *
     * @param children List to add parsed lines
     * @param replies  Lines
     * @return True if parsing is successful
     */
    protected boolean parseMlsdResponse(final AttributedList<Path> children, List<String> replies) {

        if(null == replies) {
            // This is an empty directory
            return false;
        }
        boolean success = false; // At least one entry successfully parsed
        for(String line : replies) {
            final Map<String, Map<String, String>> file = this.parseFacts(line);
            if(null == file) {
                log.error(String.format("Error parsing line %s", line));
                continue;
            }
            for(String name : file.keySet()) {
                final Path parsed = new FTPPath(this.getSession(), this.getAbsolute(),
                        StringUtils.removeStart(name, this.getAbsolute() + Path.DELIMITER), FILE_TYPE);
                parsed.setParent(this);
                // size       -- Size in octets
                // modify     -- Last modification time
                // create     -- Creation time
                // type       -- Entry type
                // unique     -- Unique id of file/directory
                // perm       -- File permissions, whether read, write, execute is allowed for the login id.
                // lang       -- Language of the file name per IANA [11] registry.
                // media-type -- MIME media-type of file contents per IANA registry.
                // charset    -- Character set per IANA registry (if not UTF-8)
                for(Map<String, String> facts : file.values()) {
                    if(!facts.containsKey("type")) {
                        log.error(String.format("No type fact in line %s", line));
                        continue;
                    }
                    if("dir".equals(facts.get("type").toLowerCase(java.util.Locale.ENGLISH))) {
                        parsed.attributes().setType(DIRECTORY_TYPE);
                    }
                    else if("file".equals(facts.get("type").toLowerCase(java.util.Locale.ENGLISH))) {
                        parsed.attributes().setType(FILE_TYPE);
                    }
                    else {
                        log.warn("Ignored type: " + line);
                        break;
                    }
                    if(name.contains(String.valueOf(DELIMITER))) {
                        if(!name.startsWith(this.getAbsolute() + Path.DELIMITER)) {
                            // Workaround for #2434.
                            log.warn("Skip listing entry with delimiter:" + name);
                            continue;
                        }
                    }
                    if(!success) {
                        if("dir".equals(facts.get("type").toLowerCase(java.util.Locale.ENGLISH)) && this.getName().equals(name)) {
                            log.warn("Possibly bogus response:" + line);
                        }
                        else {
                            success = true;
                        }
                    }
                    if(facts.containsKey("size")) {
                        parsed.attributes().setSize(Long.parseLong(facts.get("size")));
                    }
                    if(facts.containsKey("unix.uid")) {
                        parsed.attributes().setOwner(facts.get("unix.uid"));
                    }
                    if(facts.containsKey("unix.owner")) {
                        parsed.attributes().setOwner(facts.get("unix.owner"));
                    }
                    if(facts.containsKey("unix.gid")) {
                        parsed.attributes().setGroup(facts.get("unix.gid"));
                    }
                    if(facts.containsKey("unix.group")) {
                        parsed.attributes().setGroup(facts.get("unix.group"));
                    }
                    if(facts.containsKey("unix.mode")) {
                        try {
                            parsed.attributes().setPermission(new Permission(Integer.parseInt(facts.get("unix.mode"))));
                        }
                        catch(NumberFormatException e) {
                            log.error(String.format("Failed to parse fact %s", facts.get("unix.mode")));
                        }
                    }
                    if(facts.containsKey("modify")) {
                        parsed.attributes().setModificationDate(this.parseTimestamp(facts.get("modify")));
                    }
                    if(facts.containsKey("create")) {
                        parsed.attributes().setCreationDate(this.parseTimestamp(facts.get("create")));
                    }
                    if(facts.containsKey("charset")) {
                        if(!facts.get("charset").equalsIgnoreCase(this.getSession().getEncoding())) {
                            log.error(String.format("Incompatible charset %s but session is configured with %s",
                                    facts.get("charset"), this.getSession().getEncoding()));
                        }
                    }
                    children.add(parsed);
                }
            }
        }
        return success;
    }

    protected boolean parseListResponse(final AttributedList<Path> children,
                                        final FTPFileEntryParser parser, final List<String> replies) {
        if(null == replies) {
            // This is an empty directory
            return false;
        }
        boolean success = false;
        for(String line : replies) {
            final FTPFile f = parser.parseFTPEntry(line);
            if(null == f) {
                continue;
            }
            final String name = f.getName();
            if(!success) {
                // Workaround for #2410. STAT only returns ls of directory itself
                // Workaround for #2434. STAT of symbolic link directory only lists the directory itself.
                if(this.getAbsolute().equals(name)) {
                    log.warn(String.format("Skip %s", f.getName()));
                    continue;
                }
                if(name.contains(String.valueOf(DELIMITER))) {
                    if(!name.startsWith(this.getAbsolute() + Path.DELIMITER)) {
                        // Workaround for #2434.
                        log.warn("Skip listing entry with delimiter:" + name);
                        continue;
                    }
                }
            }
            success = true;
            if(name.equals(".") || name.equals("..")) {
                if(log.isDebugEnabled()) {
                    log.debug(String.format("Skip %s", f.getName()));
                }
                continue;
            }
            final Path parsed = new FTPPath(this.getSession(), this.getAbsolute(),
                    StringUtils.removeStart(name, this.getAbsolute() + Path.DELIMITER),
                    f.getType() == FTPFile.DIRECTORY_TYPE ? DIRECTORY_TYPE : FILE_TYPE);
            parsed.setParent(this);
            switch(f.getType()) {
                case FTPFile.SYMBOLIC_LINK_TYPE:
                    parsed.setSymlinkTarget(f.getLink());
                    parsed.attributes().setType(SYMBOLIC_LINK_TYPE | FILE_TYPE);
                    break;
            }
            if(parsed.attributes().isFile()) {
                parsed.attributes().setSize(f.getSize());
            }
            parsed.attributes().setOwner(f.getUser());
            parsed.attributes().setGroup(f.getGroup());
            if(this.getSession().isPermissionSupported(parser)) {
                parsed.attributes().setPermission(new Permission(
                        new boolean[][]{
                                {f.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
                                        f.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
                                        f.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION)
                                },
                                {f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION),
                                        f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION),
                                        f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION)
                                },
                                {f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION),
                                        f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION),
                                        f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION)
                                }
                        }
                ));
            }
            final Calendar timestamp = f.getTimestamp();
            if(timestamp != null) {
                parsed.attributes().setModificationDate(timestamp.getTimeInMillis());
            }
            children.add(parsed);
        }
        return success;
    }

    @Override
    public void mkdir() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Making directory {0}", "Status"),
                    this.getName()));

            if(!this.getSession().getClient().makeDirectory(this.getAbsolute())) {
                throw new FTPException(this.getSession().getClient().getReplyString());
            }
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

            if(!this.getSession().getClient().rename(this.getAbsolute(), renamed.getAbsolute())) {
                throw new FTPException(this.getSession().getClient().getReplyString());
            }
        }
        catch(IOException e) {
            this.error("Cannot rename {0}", e);
        }
    }

    @Override
    public void readSize() {
        if(this.attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting size of {0}", "Status"),
                        this.getName()));

                if(this.getSession().getClient().isFeatureSupported(FTPClient.SIZE)) {
                    if(!getSession().getClient().setFileType(FTPClient.BINARY_FILE_TYPE)) {
                        throw new FTPException(getSession().getClient().getReplyString());
                    }
                    this.attributes().setSize(this.getSession().getClient().size(this.getAbsolute()));
                }
                if(-1 == attributes().getSize()) {
                    // Read the size from the directory listing
                    final AttributedList<Path> l = this.getParent().children();
                    if(l.contains(this.getReference())) {
                        attributes().setSize(l.get(this.getReference()).attributes().getSize());
                    }
                }
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    /**
     * Parse the timestamp using the MTDM format
     *
     * @param timestamp Date string
     * @return Milliseconds
     */
    public long parseTimestamp(final String timestamp) {
        if(null == timestamp) {
            return -1;
        }
        try {
            Date parsed = new MDTMSecondsDateFormatter().parse(timestamp);
            return parsed.getTime();
        }
        catch(ParseException e) {
            log.warn("Failed to parse timestamp:" + e.getMessage());
            try {
                Date parsed = new MDTMMillisecondsDateFormatter().parse(timestamp);
                return parsed.getTime();
            }
            catch(ParseException f) {
                log.warn("Failed to parse timestamp:" + f.getMessage());
            }
        }
        log.error(String.format("Failed to parse timestamp %s", timestamp));
        return -1;
    }

    @Override
    public void readTimestamp() {
        if(this.attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting timestamp of {0}", "Status"),
                        this.getName()));

                if(this.getSession().getClient().isFeatureSupported(FTPCommand.MDTM)) {
                    final String timestamp = this.getSession().getClient().getModificationTime(this.getAbsolute());
                    if(null != timestamp) {
                        attributes().setModificationDate(this.parseTimestamp(timestamp));
                    }
                }
                if(-1 == attributes().getModificationDate()) {
                    // Read the timestamp from the directory listing
                    super.readTimestamp();
                }
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);

            }
        }
    }

    @Override
    public void delete() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                    this.getName()));

            if(attributes().isFile() || attributes().isSymbolicLink()) {
                if(!this.getSession().getClient().deleteFile(this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
            else if(attributes().isDirectory()) {
                for(AbstractPath child : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    child.delete();
                }
                this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                        this.getName()));

                if(!this.getSession().getClient().removeDirectory(this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
        }
        catch(IOException e) {
            this.error("Cannot delete {0}", e);
        }
    }

    @Override
    public void writeOwner(String owner) {
        String command = "chown";
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Changing owner of {0} to {1}", "Status"),
                    this.getName(), owner));

            if(attributes().isFile() && !attributes().isSymbolicLink()) {
                if(!this.getSession().getClient().sendSiteCommand(command + " " + owner + " " + this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
            else if(attributes().isDirectory()) {
                if(!this.getSession().getClient().sendSiteCommand(command + " " + owner + " " + this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
        }
        catch(IOException e) {
            this.error("Cannot change owner", e);
        }
    }

    @Override
    public void writeGroup(String group) {
        String command = "chgrp";
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Changing group of {0} to {1}", "Status"),
                    this.getName(), group));

            if(attributes().isFile() && !attributes().isSymbolicLink()) {
                if(!this.getSession().getClient().sendSiteCommand(command + " " + group + " " + this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
            else if(attributes().isDirectory()) {
                if(!this.getSession().getClient().sendSiteCommand(command + " " + group + " " + this.getAbsolute())) {
                    throw new FTPException(this.getSession().getClient().getReplyString());
                }
            }
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

    private boolean chmodSupported = true;

    private void writeUnixPermissionImpl(Permission permission) throws IOException {
        if(chmodSupported) {
            this.getSession().message(MessageFormat.format(Locale.localizedString("Changing permission of {0} to {1}", "Status"),
                    this.getName(), permission.getOctalString()));
            if(attributes().isFile() && !attributes().isSymbolicLink()) {
                if(this.getSession().getClient().sendSiteCommand("CHMOD " + permission.getOctalString() + " " + this.getAbsolute())) {
                    this.attributes().setPermission(permission);
                }
                else {
                    chmodSupported = false;
                }
            }
            else if(attributes().isDirectory()) {
                if(this.getSession().getClient().sendSiteCommand("CHMOD " + permission.getOctalString() + " " + this.getAbsolute())) {
                    this.attributes().setPermission(permission);
                }
                else {
                    chmodSupported = false;
                }
            }
        }
    }

    @Override
    public void writeTimestamp(long created, long modified, long accessed) {
        try {
            this.writeModificationDateImpl(created, modified);
        }
        catch(IOException e) {
            this.error("Cannot change timestamp", e);
        }
    }

    private void writeModificationDateImpl(long created, long modified) throws IOException {
        this.getSession().message(MessageFormat.format(Locale.localizedString("Changing timestamp of {0} to {1}", "Status"),
                this.getName(), UserDateFormatterFactory.get().getShortFormat(modified)));

        final MDTMSecondsDateFormatter formatter = new MDTMSecondsDateFormatter();
        if(this.getSession().getClient().isFeatureSupported(FTPCommand.MFMT)) {
            if(this.getSession().getClient().setModificationTime(this.getAbsolute(),
                    formatter.format(modified, TimeZone.getTimeZone("UTC")))) {
                this.attributes().setModificationDate(modified);
            }
        }
        else {
            if(this.getSession().isUtimeSupported()) {
                // The utime() function sets the access and modification times of the named
                // file from the structures in the argument array timep.
                // The access time is set to the value of the first element,
                // and the modification time is set to the value of the second element
                // Accessed date, modified date, created date
                if(this.getSession().getClient().sendSiteCommand("UTIME " + this.getAbsolute()
                        + " " + formatter.format(new Date(modified), TimeZone.getTimeZone("UTC"))
                        + " " + formatter.format(new Date(modified), TimeZone.getTimeZone("UTC"))
                        + " " + formatter.format(new Date(created), TimeZone.getTimeZone("UTC"))
                        + " UTC")) {
                    this.attributes().setModificationDate(modified);
                    this.attributes().setCreationDate(created);
                }
                else {
                    this.getSession().setUtimeSupported(false);
                    log.warn("UTIME not supported");
                }
            }

        }
    }

    @Override
    public void download(final BandwidthThrottle throttle, final StreamListener listener,
                         final TransferStatus status) {
        try {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = read(status);
                out = getLocal().getOutputStream(status.isResume());
                this.download(in, out, throttle, listener, status);
            }
            finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
        }
        catch(IOException e) {
            this.error("Download failed", e);
        }
    }

    @Override
    public InputStream read(final TransferStatus status) throws IOException {
        final FTPSession session = getSession();
        this.data(new DataConnectionAction() {
            @Override
            public boolean run() throws IOException {
                if(!session.getClient().setFileType(FTP.BINARY_FILE_TYPE)) {
                    throw new FTPException(session.getClient().getReplyString());
                }
                return true;
            }
        });
        if(status.isResume()) {
            // Where a server process supports RESTart in STREAM mode
            if(!session.getClient().isFeatureSupported("REST STREAM")) {
                status.setResume(false);
            }
            else {
                session.getClient().setRestartOffset(status.getCurrent());
            }
        }
        return new ProxyInputStream(session.getClient().retrieveFileStream(getAbsolute())) {
            private boolean complete;

            @Override
            protected void afterRead(final int n) throws IOException {
                if(-1 == n) {
                    complete = true;
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                }
                finally {
                    if(complete) {
                        // Read 226 status
                        if(!session.getClient().completePendingCommand()) {
                            throw new FTPException(session.getClient().getReplyString());
                        }
                    }
                    else {
                        // Interrupted transfer
                        if(!session.getClient().abort()) {
                            log.error("Error closing data socket:" + session.getClient().getReplyString());
                        }
                    }
                }
            }
        };
    }

    @Override
    public void upload(final BandwidthThrottle throttle, final StreamListener listener,
                       final TransferStatus status) {
        try {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = getLocal().getInputStream();
                out = write(status);
                this.upload(out, in, throttle, listener, status);
            }
            finally {
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
        }
        catch(IOException e) {
            this.error("Upload failed", e);
        }
    }

    @Override
    public OutputStream write(final TransferStatus status) throws IOException {
        final FTPSession session = getSession();
        this.data(new DataConnectionAction() {
            @Override
            public boolean run() throws IOException {
                if(!session.getClient().setFileType(FTPClient.BINARY_FILE_TYPE)) {
                    throw new FTPException(session.getClient().getReplyString());
                }
                return true;
            }
        });
        final OutputStream out;
        if(status.isResume()) {
            out = session.getClient().appendFileStream(getAbsolute());
        }
        else {
            out = session.getClient().storeFileStream(getAbsolute());
        }
        return new CountingOutputStream(out) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                }
                finally {
                    if(this.getByteCount() == status.getLength()) {
                        // Read 226 status
                        if(!session.getClient().completePendingCommand()) {
                            throw new FTPException(session.getClient().getReplyString());
                        }
                    }
                    else {
                        // Interrupted transfer
                        if(!session.getClient().abort()) {
                            log.error("Error closing data socket:" + session.getClient().getReplyString());
                        }
                    }
                }
            }
        };
    }
}
