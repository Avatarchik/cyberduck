package ch.cyberduck.core;

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

import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.security.Security;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.LogManager;

/**
 * Holding all application preferences. Default values get overwritten when loading
 * the <code>PREFERENCES_FILE</code>.
 * Singleton class.
 *
 * @version $Id: Preferences.java 10920 2013-04-23 13:55:06Z dkocher $
 */
public abstract class Preferences {
    private static final Logger log = Logger.getLogger(Preferences.class);

    private static Preferences current = null;

    protected Map<String, String> defaults
            = new HashMap<String, String>();

    /**
     * TTL for DNS queries
     */
    static {
        Security.setProperty("networkaddress.cache.ttl", "10");
        Security.setProperty("networkaddress.cache.negative.ttl", "5");
    }

    private static final Object lock = new Object();

    /**
     * @return The singleton instance of me.
     */
    public static Preferences instance() {
        synchronized(lock) {
            if(null == current) {
                current = PreferencesFactory.createPreferences();
                current.load();
                current.setDefaults();
                current.post();
            }
            return current;
        }
    }

    /**
     * Called after the defaults have been set.
     */
    protected void post() {
        // Ticket #2539
        if(this.getBoolean("connection.dns.ipv6")) {
            System.setProperty("java.net.preferIPv6Addresses", String.valueOf(true));
        }
    }

    /**
     * Update the given property with a string value.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public abstract void setProperty(String property, String v);

    /**
     * Update the given property with a list value
     *
     * @param property The name of the property to create or update
     * @param values   The new or updated value
     */
    public abstract void setProperty(String property, List<String> values);

    /**
     * Remove a user customized property from the preferences.
     *
     * @param property Property name
     */
    public abstract void deleteProperty(String property);

    /**
     * Internally always saved as a string.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public void setProperty(String property, boolean v) {
        this.setProperty(property, v ? String.valueOf(true) : String.valueOf(false));
    }

    /**
     * Internally always saved as a string.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public void setProperty(String property, int v) {
        this.setProperty(property, String.valueOf(v));
    }

    /**
     * Internally always saved as a string.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public void setProperty(String property, float v) {
        this.setProperty(property, String.valueOf(v));
    }

    /**
     * Internally always saved as a string.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public void setProperty(String property, long v) {
        this.setProperty(property, String.valueOf(v));
    }

    /**
     * Internally always saved as a string.
     *
     * @param property The name of the property to create or update
     * @param v        The new or updated value
     */
    public void setProperty(String property, double v) {
        this.setProperty(property, String.valueOf(v));
    }

    /**
     * setting the default prefs values
     */
    protected void setDefaults() {
        defaults.put("tmp.dir", System.getProperty("java.io.tmpdir"));

        /**
         * The logging level (debug, info, warn, error)
         */
        defaults.put("logging.config", "log4j-cocoa.xml");
        defaults.put("logging", "error");

        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for(Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }
        // call only once during initialization time of your application
        SLF4JBridgeHandler.install();

        /**
         * How many times the application was launched
         */
        defaults.put("uses", "0");
        /**
         * True if donation dialog will be displayed before quit
         */
        defaults.put("donate.reminder", String.valueOf(-1));
        defaults.put("donate.reminder.interval", String.valueOf(20)); // in days
        defaults.put("donate.reminder.date", String.valueOf(new Date(0).getTime()));

        defaults.put("defaulthandler.reminder", String.valueOf(true));

        defaults.put("mail.feedback", "mailto:feedback@cyberduck.ch");

        defaults.put("website.donate", "http://cyberduck.ch/donate/");
        defaults.put("website.home", "http://cyberduck.ch/");
        defaults.put("website.forum", "http://forum.cyberduck.ch/");
        defaults.put("website.help", "http://help.cyberduck.ch/" + this.locale());
        defaults.put("website.bug", "http://trac.cyberduck.ch/newticket/");
        defaults.put("website.crash", "http://crash.cyberduck.ch/report");

        defaults.put("rendezvous.enable", String.valueOf(true));
        defaults.put("rendezvous.loopback.supress", String.valueOf(true));

        defaults.put("growl.enable", String.valueOf(true));
        defaults.put("growl.limit", String.valueOf(10));

        defaults.put("path.symboliclink.resolve", String.valueOf(false));
        /**
         * Normalize path names
         */
        defaults.put("path.normalize", String.valueOf(true));
        defaults.put("path.normalize.unicode", String.valueOf(false));

        defaults.put("local.symboliclink.resolve", String.valueOf(false));
        defaults.put("local.normalize.unicode", String.valueOf(true));
        defaults.put("local.list.native", String.valueOf(true));

        /**
         * Maximum number of directory listings to cache using a most recently used implementation
         */
        defaults.put("browser.cache.size", String.valueOf(1000));
        defaults.put("transfer.cache.size", String.valueOf(50));
        defaults.put("icon.cache.size", String.valueOf(50));

        /**
         * Caching NS* proxy instances.
         */
        defaults.put("browser.model.cache.size", String.valueOf(200));
        defaults.put("bookmark.model.cache.size", String.valueOf(100));
        defaults.put("queue.model.cache.size", String.valueOf(50));

        defaults.put("info.toolbar.selected", String.valueOf(0));
        defaults.put("preferences.toolbar.selected", String.valueOf(0));

        /**
         * Current default browser view is outline view (0-List view, 1-Outline view, 2-Column view)
         */
        defaults.put("browser.view", "1");
        /**
         * Save browser sessions when quitting and restore upon relaunch
         */
        defaults.put("browser.serialize", String.valueOf(true));

        defaults.put("browser.font.size", String.valueOf(12f));

        defaults.put("browser.view.autoexpand", String.valueOf(true));
        defaults.put("browser.view.autoexpand.useDelay", String.valueOf(true));
        defaults.put("browser.view.autoexpand.delay", "1.0"); // in seconds

        defaults.put("browser.hidden.regex", "\\..*");

        defaults.put("browser.openUntitled", String.valueOf(true));
        defaults.put("browser.defaultBookmark", Locale.localizedString("None"));

        defaults.put("browser.markInaccessibleFolders", String.valueOf(true));
        /**
         * Confirm closing the browsing connection
         */
        defaults.put("browser.confirmDisconnect", String.valueOf(false));
        defaults.put("browser.disconnect.showBookmarks", String.valueOf(false));

        /**
         * Display only one info panel and change information according to selection in browser
         */
        defaults.put("browser.info.isInspector", String.valueOf(true));

        defaults.put("browser.columnKind", String.valueOf(false));
        defaults.put("browser.columnExtension", String.valueOf(false));
        defaults.put("browser.columnSize", String.valueOf(true));
        defaults.put("browser.columnModification", String.valueOf(true));
        defaults.put("browser.columnOwner", String.valueOf(false));
        defaults.put("browser.columnGroup", String.valueOf(false));
        defaults.put("browser.columnPermissions", String.valueOf(false));

        defaults.put("browser.sort.column", "FILENAME");
        defaults.put("browser.sort.ascending", String.valueOf(true));

        defaults.put("browser.alternatingRows", String.valueOf(false));
        defaults.put("browser.verticalLines", String.valueOf(false));
        defaults.put("browser.horizontalLines", String.valueOf(true));
        /**
         * Show hidden files in browser by default
         */
        defaults.put("browser.showHidden", String.valueOf(false));
        defaults.put("browser.charset.encoding", "UTF-8");
        /**
         * Edit double clicked files instead of downloading
         */
        defaults.put("browser.doubleclick.edit", String.valueOf(false));
        /**
         * Rename files when return or enter key is pressed
         */
        defaults.put("browser.enterkey.rename", String.valueOf(true));

        /**
         * Enable inline editing in browser
         */
        defaults.put("browser.editable", String.valueOf(true));

        /**
         * Warn before renaming files
         */
        defaults.put("browser.confirmMove", String.valueOf(false));

        defaults.put("browser.logDrawer.isOpen", String.valueOf(false));
        defaults.put("browser.logDrawer.size.height", String.valueOf(200));

        /**
         * Filename (Short Date Format)Extension
         */
        defaults.put("browser.duplicate.format", "{0} ({1}){2}");

        /**
         * Use octal or decimal file sizes
         */
        defaults.put("browser.filesize.decimal", String.valueOf(false));
        defaults.put("browser.date.natural", String.valueOf(true));

        defaults.put("info.toggle.permission", String.valueOf(1));
        defaults.put("info.toggle.distribution", String.valueOf(0));
        defaults.put("info.toggle.s3", String.valueOf(0));

        defaults.put("connection.toggle.options", String.valueOf(0));
        defaults.put("bookmark.toggle.options", String.valueOf(0));

        defaults.put("alert.toggle.transcript", String.valueOf(0));

        defaults.put("transfer.toggle.details", String.valueOf(1));

        /**
         * Default editor
         */
        defaults.put("editor.bundleIdentifier", "com.apple.TextEdit");
        defaults.put("editor.alwaysUseDefault", String.valueOf(false));

        defaults.put("editor.odb.enable", String.valueOf(false));
        defaults.put("editor.tmp.directory", System.getProperty("java.io.tmpdir"));

        defaults.put("filetype.text.regex",
                ".*\\.txt|.*\\.cgi|.*\\.htm|.*\\.html|.*\\.shtml|.*\\.xml|.*\\.xsl|.*\\.php|.*\\.php3|" +
                        ".*\\.js|.*\\.css|.*\\.asp|.*\\.java|.*\\.c|.*\\.cp|.*\\.cpp|.*\\.m|.*\\.h|.*\\.pl|.*\\.py|" +
                        ".*\\.rb|.*\\.sh");
        defaults.put("filetype.binary.regex",
                ".*\\.pdf|.*\\.ps|.*\\.exe|.*\\.bin|.*\\.jpeg|.*\\.jpg|.*\\.jp2|.*\\.gif|.*\\.tif|.*\\.ico|" +
                        ".*\\.icns|.*\\.tiff|.*\\.bmp|.*\\.pict|.*\\.sgi|.*\\.tga|.*\\.png|.*\\.psd|" +
                        ".*\\.hqx|.*\\.rar|.*\\.sea|.*\\.dmg|.*\\.zip|.*\\.sit|.*\\.tar|.*\\.gz|.*\\.tgz|.*\\.bz2|" +
                        ".*\\.avi|.*\\.qtl|.*\\.bom|.*\\.pax|.*\\.pgp|.*\\.mpg|.*\\.mpeg|.*\\.mp3|.*\\.m4p|" +
                        ".*\\.m4a|.*\\.mov|.*\\.avi|.*\\.qt|.*\\.ram|.*\\.aiff|.*\\.aif|.*\\.wav|.*\\.wma|" +
                        ".*\\.doc|.*\\.iso|.*\\.xls|.*\\.ppt");

        /**
         * Save bookmarks in ~/Library
         */
        defaults.put("favorites.save", String.valueOf(true));

        defaults.put("queue.openByDefault", String.valueOf(false));
        defaults.put("queue.save", String.valueOf(true));
        defaults.put("queue.removeItemWhenComplete", String.valueOf(false));
        /**
         * The maximum number of concurrent transfers
         */
        defaults.put("queue.maxtransfers", String.valueOf(5));
        /**
         * Warning when number of transfers in queue exceeds limit
         */
        defaults.put("queue.size.warn", String.valueOf(50));
        /**
         * Open completed downloads
         */
        defaults.put("queue.postProcessItemWhenComplete", String.valueOf(false));
        /**
         * Bring transfer window to front
         */
        defaults.put("queue.orderFrontOnStart", String.valueOf(true));
        defaults.put("queue.orderBackOnStop", String.valueOf(false));

        /**
         * Action when duplicate file exists
         */
        defaults.put("queue.download.fileExists", "ask");
        defaults.put("queue.upload.fileExists", "ask");
        /**
         * When triggered manually using 'Reload' in the Transfer window
         */
        defaults.put("queue.download.reload.fileExists", "ask");
        defaults.put("queue.upload.reload.fileExists", "ask");

        defaults.put("queue.upload.changePermissions", String.valueOf(true));
        defaults.put("queue.upload.permissions.useDefault", String.valueOf(false));
        defaults.put("queue.upload.permissions.file.default", String.valueOf(644));
        defaults.put("queue.upload.permissions.folder.default", String.valueOf(755));

        defaults.put("queue.upload.preserveDate", String.valueOf(false));

        defaults.put("queue.upload.skip.enable", String.valueOf(true));
        defaults.put("queue.upload.skip.regex.default",
                ".*~\\..*|\\.DS_Store|\\.svn|CVS");
        defaults.put("queue.upload.skip.regex",
                ".*~\\..*|\\.DS_Store|\\.svn|CVS");

        /**
         * Create temporary filename with an UUID and rename when upload is complete
         */
        defaults.put("queue.upload.file.temporary", String.valueOf(false));
        /**
         * Format string for temporary filename. Default to filename-uuid
         */
        defaults.put("queue.upload.file.temporary.format", "{0}-{1}");

        defaults.put("queue.upload.file.rename.format", "{0} ({1}){2}");
        defaults.put("queue.download.file.rename.format", "{0} ({1}){2}");

        defaults.put("queue.download.changePermissions", String.valueOf(true));
        defaults.put("queue.download.permissions.useDefault", String.valueOf(false));
        defaults.put("queue.download.permissions.file.default", String.valueOf(644));
        defaults.put("queue.download.permissions.folder.default", String.valueOf(755));

        defaults.put("queue.download.preserveDate", String.valueOf(true));

        defaults.put("queue.download.skip.enable", String.valueOf(true));
        defaults.put("queue.download.skip.regex.default",
                ".*~\\..*|\\.DS_Store|\\.svn|CVS|RCS|SCCS|\\.git|\\.bzr|\\.bzrignore|\\.bzrtags|\\.hg|\\.hgignore|\\.hgtags|_darcs");
        defaults.put("queue.download.skip.regex",
                ".*~\\..*|\\.DS_Store|\\.svn|CVS|RCS|SCCS|\\.git|\\.bzr|\\.bzrignore|\\.bzrtags|\\.hg|\\.hgignore|\\.hgtags|_darcs");

        defaults.put("queue.download.quarantine", String.valueOf(true));
        defaults.put("queue.download.wherefrom", String.valueOf(true));

        defaults.put("queue.sync.compare.hash", String.valueOf(true));
        defaults.put("queue.sync.compare.size", String.valueOf(false));

        defaults.put("queue.dock.badge", String.valueOf(false));

        defaults.put("queue.sleep.prevent", String.valueOf(true));

        /**
         * Bandwidth throttle options
         */
        StringBuilder options = new StringBuilder();
        options.append(5 * TransferStatus.KILO).append(",");
        options.append(10 * TransferStatus.KILO).append(",");
        options.append(20 * TransferStatus.KILO).append(",");
        options.append(50 * TransferStatus.KILO).append(",");
        options.append(100 * TransferStatus.KILO).append(",");
        options.append(150 * TransferStatus.KILO).append(",");
        options.append(200 * TransferStatus.KILO).append(",");
        options.append(500 * TransferStatus.KILO).append(",");
        options.append(1 * TransferStatus.MEGA).append(",");
        options.append(2 * TransferStatus.MEGA).append(",");
        options.append(5 * TransferStatus.MEGA).append(",");
        options.append(10 * TransferStatus.MEGA).append(",");
        options.append(15 * TransferStatus.MEGA).append(",");
        options.append(20 * TransferStatus.MEGA).append(",");
        options.append(50 * TransferStatus.MEGA).append(",");
        options.append(100 * TransferStatus.MEGA).append(",");
        defaults.put("queue.bandwidth.options", options.toString());

        /**
         * Bandwidth throttle upload stream
         */
        defaults.put("queue.upload.bandwidth.bytes", String.valueOf(-1));
        /**
         * Bandwidth throttle download stream
         */
        defaults.put("queue.download.bandwidth.bytes", String.valueOf(-1));

        /**
         * While downloading, update the icon of the downloaded file as a progress indicator
         */
        defaults.put("queue.download.icon.update", String.valueOf(true));
        defaults.put("queue.download.icon.threshold", String.valueOf(TransferStatus.MEGA * 5));

        /**
         * Default synchronize action selected in the sync dialog
         */
        defaults.put("queue.sync.action.default", "upload");
        defaults.put("queue.prompt.action.default", "overwrite");

        defaults.put("queue.logDrawer.isOpen", String.valueOf(false));
        defaults.put("queue.logDrawer.size.height", String.valueOf(200));

        defaults.put("http.compression.enable", String.valueOf(true));

        /**
         * HTTP routes to maximum number of connections allowed for those routes
         */
        defaults.put("http.connections.route", String.valueOf(5));
        /**
         * Total number of connections in the pool
         */
        defaults.put("http.connections.total", String.valueOf(5));
        defaults.put("http.manager.timeout", String.valueOf(0)); // Inifinite
        defaults.put("http.socket.buffer", String.valueOf(131072));
        defaults.put("http.credentials.charset", "ISO-8859-1");

        /**
         * Enable or disable verification that the remote host taking part
         * of a data connection is the same as the host to which the control
         * connection is attached.
         */
        defaults.put("ftp.datachannel.verify", String.valueOf(false));
        defaults.put("ftp.command.feat", String.valueOf(true));
        defaults.put("ftp.socket.buffer", String.valueOf(131072));

        /**
         * Send LIST -a
         */
        defaults.put("ftp.command.lista", String.valueOf(true));
        defaults.put("ftp.command.stat", String.valueOf(true));
        defaults.put("ftp.command.mlsd", String.valueOf(true));
        defaults.put("ftp.command.utime", String.valueOf(true));

        /**
         * Fallback to active or passive mode respectively
         */
        defaults.put("ftp.connectmode.fallback", String.valueOf(true));
        /**
         * Protect the data channel by default. For TLS, the data connection
         * can have one of two security levels.
         1) Clear (requested by 'PROT C')
         2) Private (requested by 'PROT P')
         */
        defaults.put("ftp.tls.datachannel", "P"); //C
        defaults.put("ftp.tls.session.requirereuse", String.valueOf(true));
        defaults.put("ftp.ssl.session.cache.size", String.valueOf(100));

        /**
         * Try to determine the timezone automatically using timestamp comparison from MLST and LIST
         */
        defaults.put("ftp.timezone.auto", String.valueOf(false));
        defaults.put("ftp.timezone.default", TimeZone.getDefault().getID());

        /**
         * Default bucket location
         */
        defaults.put("s3.location", "US");
        defaults.put("s3.bucket.acl.default", "public-read");
        //defaults.put("s3.bucket.acl.default", "private");
        defaults.put("s3.key.acl.default", "public-read");
        //defaults.put("s3.key.acl.default", "private");

        /**
         * Default redundancy level
         */
        defaults.put("s3.storage.class", "STANDARD");
        //defaults.put("s3.encryption.algorithm", "AES256");
        defaults.put("s3.encryption.algorithm", StringUtils.EMPTY);

        /**
         * Validaty for public S3 URLs
         */
        defaults.put("s3.url.expire.seconds", String.valueOf(24 * 60 * 60)); //expiry time for public URL

        defaults.put("s3.mfa.serialnumber", StringUtils.EMPTY);

        defaults.put("s3.listing.chunksize", String.valueOf(1000));

        /**
         * Show revisions as hidden files in browser
         */
        defaults.put("s3.revisions.enable", String.valueOf(true));
        /**
         * If set calculate MD5 sum of uploaded file and set metadata header Content-MD5
         */
        defaults.put("s3.upload.metadata.md5", String.valueOf(false));
        defaults.put("s3.upload.multipart", String.valueOf(true));
        defaults.put("s3.upload.multipart.concurency", String.valueOf(5));
        /**
         * Threshold in bytes. Only use multipart uploads for files more than 5GB
         */
        defaults.put("s3.upload.multipart.threshold", String.valueOf(5L * 1024L * 1024L * 1024L));
        defaults.put("s3.upload.multipart.size", String.valueOf(5L * 1024L * 1024L));

        /**
         * A prefix to apply to log file names
         */
        defaults.put("s3.logging.prefix", "logs/");
        defaults.put("google.logging.prefix", "log");
        defaults.put("cloudfront.logging.prefix", "logs/");

        defaults.put("google.storage.oauth.clientid", "996125414232.apps.googleusercontent.com");
        defaults.put("google.storage.oauth.secret", "YdaFjo2t74-Q0sThsXgeTv3l");

        final int month = 60 * 60 * 24 * 30; //30 days in seconds
        defaults.put("s3.cache.seconds", String.valueOf(month));

        /**
         * Default metadata for uploads. Format must be "key1=value1 key2=value2"
         */
        defaults.put("s3.metadata.default", StringUtils.EMPTY);

        defaults.put("azure.metadata.default", StringUtils.EMPTY);

        defaults.put("cf.authentication.context", "/v1.0");
        defaults.put("cf.upload.metadata.md5", String.valueOf(false));
        defaults.put("cf.metadata.default", StringUtils.EMPTY);
        defaults.put("cf.list.limit", String.valueOf(10000));
        defaults.put("cf.list.cdn.preload", String.valueOf(true));

        //doc	Microsoft Word
        //html	HTML Format
        //odt	Open Document Format
        //pdf	Portable Document Format
        //png	Portable Networks Graphic Image Format
        //rtf	Rich Format
        //txt	TXT File
        //zip	ZIP archive. Contains the images (if any) used in the document and an exported .html file.
        defaults.put("google.docs.export.document", "doc");
        defaults.put("google.docs.export.document.formats", "doc,html,odt,pdf,png,rtf,txt,zip");
        //pdf	Portable Document Format
        //png	Portable Networks Graphic Image Format
        //ppt	Powerpoint Format
        //swf	Flash Format
        //txt	TXT file
        defaults.put("google.docs.export.presentation", "ppt");
        defaults.put("google.docs.export.presentation.formats", "ppt,pdf,png,swf,txt");
        //xls	XLS (Microsoft Excel)
        //csv	CSV (Comma Seperated Value)
        //pdf	PDF (Portable Document Format)
        //ods	ODS (Open Document Spreadsheet)
        //tsv	TSV (Tab Seperated Value)
        //html	HTML Format
        defaults.put("google.docs.export.spreadsheet", "xls");
        defaults.put("google.docs.export.spreadsheet.formats", "xls,csv,pdf,ods,tsv,html");

        defaults.put("google.docs.upload.convert", String.valueOf(true));
        defaults.put("google.docs.upload.ocr", String.valueOf(false));

        /**
         * Show revisions as hidden files in browser
         */
        defaults.put("google.docs.revisions.enable", String.valueOf(false));
        /**
         * If set to true will only trash documents
         */
        defaults.put("google.docs.delete.trash", String.valueOf(false));

        /**
         * NTLM Windows Domain
         */
        defaults.put("webdav.ntlm.domain", StringUtils.EMPTY);
        defaults.put("webdav.ntlm.workstation", StringUtils.EMPTY);

        /**
         * Enable Expect-Continue handshake
         */
        defaults.put("webdav.expect-continue", String.valueOf(true));

        defaults.put("analytics.provider.qloudstat.setup", "https://qloudstat.com/configuration/add");
        defaults.put("analytics.provider.qloudstat.iam.policy",
                "{\n" +
                        "    \"Statement\": [\n" +
                        "        {\n" +
                        "            \"Action\": [\n" +
                        "                \"s3:GetObject\", \n" +
                        "                \"s3:ListBucket\"\n" +
                        "            ], \n" +
                        "            \"Condition\": {\n" +
                        "                \"Bool\": {\n" +
                        "                    \"aws:SecureTransport\": \"true\"\n" +
                        "                }\n" +
                        "            }, \n" +
                        "            \"Effect\": \"Allow\", \n" +
                        "            \"Resource\": \"arn:aws:s3:::%s/*\"\n" +
                        "        }, \n" +
                        "        {\n" +
                        "            \"Action\": [\n" +
                        "                \"s3:ListAllMyBuckets\", \n" +
                        "                \"s3:GetBucketLogging\", \n" +
                        "                \"s3:GetBucketLocation\"\n" +
                        "            ], \n" +
                        "            \"Effect\": \"Allow\", \n" +
                        "            \"Resource\": \"arn:aws:s3:::*\"\n" +
                        "        }, \n" +
                        "        {\n" +
                        "            \"Action\": [\n" +
                        "                \"cloudfront:GetDistribution\", \n" +
                        "                \"cloudfront:GetDistributionConfig\", \n" +
                        "                \"cloudfront:ListDistributions\", \n" +
                        "                \"cloudfront:GetStreamingDistribution\", \n" +
                        "                \"cloudfront:GetStreamingDistributionConfig\", \n" +
                        "                \"cloudfront:ListStreamingDistributions\"\n" +
                        "            ], \n" +
                        "            \"Condition\": {\n" +
                        "                \"Bool\": {\n" +
                        "                    \"aws:SecureTransport\": \"true\"\n" +
                        "                }\n" +
                        "            }, \n" +
                        "            \"Effect\": \"Allow\", \n" +
                        "            \"Resource\": \"*\"\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}\n");

        /**
         * Maximum concurrent connections to the same host
         * Unlimited by default
         */
        defaults.put("connection.host.max", String.valueOf(-1));
        /**
         * Default login name
         */
        defaults.put("connection.login.name", System.getProperty("user.name"));
        defaults.put("connection.login.anon.name", "anonymous");
        defaults.put("connection.login.anon.pass", "cyberduck@example.net");
        /**
         * Search for passphrases in Keychain
         */
        defaults.put("connection.login.useKeychain", String.valueOf(true));
        /**
         * Add to Keychain option is checked in login prompt
         */
        defaults.put("connection.login.addKeychain", String.valueOf(true));

        defaults.put("connection.port.default", String.valueOf(21));
        defaults.put("connection.protocol.default", Protocol.FTP.getIdentifier());
        /**
         * Socket timeout
         */
        defaults.put("connection.timeout.seconds", String.valueOf(30));
        /**
         * Retry to connect after a I/O failure automatically
         */
        defaults.put("connection.retry", String.valueOf(0));
        defaults.put("connection.retry.delay", String.valueOf(10));

        defaults.put("connection.hostname.default", StringUtils.EMPTY);
        /**
         * Try to resolve the hostname when entered in connection dialog
         */
        defaults.put("connection.hostname.check", String.valueOf(true)); //Check hostname reachability using NSNetworkDiagnostics
        defaults.put("connection.hostname.idn", String.valueOf(true)); //Convert hostnames to Punycode

        /**
         * java.net.preferIPv6Addresses
         */
        defaults.put("connection.dns.ipv6", String.valueOf(false));

        /**
         * Read proxy settings from system preferences
         */
        defaults.put("connection.proxy.enable", String.valueOf(true));
        defaults.put("connection.proxy.ntlm.domain", StringUtils.EMPTY);

        /**
         * Warning when opening connections sending credentials in plaintext
         */
        defaults.put("connection.unsecure.warning", String.valueOf(true));
        defaults.put("connection.unsecure.switch", String.valueOf(true));

        defaults.put("connection.ssl.protocols", "SSLv3, TLSv1");

        /**
         * Transfer read buffer size
         */
        defaults.put("connection.chunksize", String.valueOf(32768));

        defaults.put("disk.unmount.timeout", String.valueOf(2));

        defaults.put("transcript.length", String.valueOf(1000));

        /**
         * Read favicon from Web URL
         */
        defaults.put("bookmark.favicon.download", String.valueOf(true));

        /**
         * Default to large icon size
         */
        defaults.put("bookmark.icon.size", String.valueOf(64));

        /**
         * Use the SFTP subsystem or a SCP channel for file transfers over SSH
         */
        defaults.put("ssh.transfer", Protocol.SFTP.getIdentifier()); // Session.SCP

        defaults.put("ssh.authentication.publickey.default.enable", String.valueOf(false));
        defaults.put("ssh.authentication.publickey.default.rsa", "~/.ssh/id_rsa");
        defaults.put("ssh.authentication.publickey.default.dsa", "~/.ssh/id_dsa");

        defaults.put("archive.default", "tar.gz");

        /**
         * Archiver
         */
        defaults.put("archive.command.create.tar", "tar -cpPf {0}.tar {1}");
        defaults.put("archive.command.create.tar.gz", "tar -czpPf {0}.tar.gz {1}");
        defaults.put("archive.command.create.tar.bz2", "tar -cjpPf {0}.tar.bz2 {1}");
        defaults.put("archive.command.create.zip", "zip -qr {0}.zip {1}");
        defaults.put("archive.command.create.gz", "gzip -qr {1}");
        defaults.put("archive.command.create.bz2", "bzip2 -zk {1}");

        /**
         * Unarchiver
         */
        defaults.put("archive.command.expand.tar", "tar -xpPf {0} -C {1}");
        defaults.put("archive.command.expand.tar.gz", "tar -xzpPf {0} -C {1}");
        defaults.put("archive.command.expand.tar.bz2", "tar -xjpPf {0} -C {1}");
        defaults.put("archive.command.expand.zip", "unzip -qn {0} -d {1}");
        defaults.put("archive.command.expand.gz", "gzip -d {0}");
        defaults.put("archive.command.expand.bz2", "bzip2 -dk {0}");

        defaults.put("update.check", String.valueOf(true));
        final int day = 60 * 60 * 24;
        defaults.put("update.check.interval", String.valueOf(day)); // periodic update check in seconds

        defaults.put("terminal.bundle.identifier", "com.apple.Terminal");
        defaults.put("terminal.command", "do script \"{0}\"");
        defaults.put("terminal.command.ssh", "ssh -t {0} {1}@{2} -p {3} \"cd {4} && exec \\$SHELL\"");
    }

    /**
     * Default value for a given property.
     *
     * @param property The property to query.
     * @return A default value if any or null if not found.
     */
    public String getDefault(String property) {
        String value = defaults.get(property);
        if(null == value) {
            log.warn(String.format("No property with key '%s'", property));
        }
        return value;
    }

    /**
     * @param property The property to query.
     * @return The configured values determined by a whitespace separator.
     */
    public List<String> getList(String property) {
        return Arrays.asList(this.getProperty(property).split("(?<!\\\\)\\p{javaWhitespace}+"));
    }

    /**
     * Give value in user settings or default value if not customized.
     *
     * @param property The property to query.
     * @return The user configured value or default.
     */
    public abstract String getProperty(String property);

    public int getInteger(String property) {
        final String v = this.getProperty(property);
        if(null == v) {
            return -1;
        }
        return Integer.parseInt(v);
    }

    public float getFloat(String property) {
        final String v = this.getProperty(property);
        if(null == v) {
            return -1;
        }
        return Float.parseFloat(v);
    }

    public long getLong(String property) {
        final String v = this.getProperty(property);
        if(null == v) {
            return -1;
        }
        return Long.parseLong(v);
    }

    public double getDouble(String property) {
        final String v = this.getProperty(property);
        if(null == v) {
            return -1;
        }
        return Double.parseDouble(v);
    }

    public boolean getBoolean(String property) {
        final String v = this.getProperty(property);
        if(null == v) {
            return false;
        }
        if(v.equalsIgnoreCase(String.valueOf(true))) {
            return true;
        }
        if(v.equalsIgnoreCase(String.valueOf(false))) {
            return false;
        }
        if(v.equalsIgnoreCase(String.valueOf(1))) {
            return true;
        }
        if(v.equalsIgnoreCase(String.valueOf(0))) {
            return false;
        }
        try {
            return v.equalsIgnoreCase("yes");
        }
        catch(NumberFormatException e) {
            return false;
        }
    }

    /**
     * Store preferences; ensure perisistency
     */
    public abstract void save();

    /**
     * Overriding the default values with prefs from the last session.
     */
    protected abstract void load();

    /**
     * @return The preferred locale of all localizations available
     *         in this application bundle
     */
    public String locale() {
        return this.applicationLocales().iterator().next();
    }

    /**
     * The localizations available in this application bundle
     * sorted by preference by the user.
     *
     * @return Available locales in application bundle
     */
    public abstract List<String> applicationLocales();

    /**
     * @return Available locales in system
     */
    public abstract List<String> systemLocales();

    /**
     * @param locale ISO Language identifier
     * @return Human readable language name in the target language
     */
    public String getDisplayName(String locale) {
        java.util.Locale l;
        if(StringUtils.contains(locale, "_")) {
            l = new java.util.Locale(locale.split("_")[0], locale.split("_")[1]);
        }
        else {
            l = new java.util.Locale(locale);
        }
        return StringUtils.capitalize(l.getDisplayName(l));
    }
}
