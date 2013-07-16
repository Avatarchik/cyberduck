package ch.cyberduck.core.s3;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
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

import ch.cyberduck.core.*;
import ch.cyberduck.core.cloud.CloudPath;
import ch.cyberduck.core.date.RFC1123DateFormatter;
import ch.cyberduck.core.date.UserDateFormatterFactory;
import ch.cyberduck.core.http.DelayedHttpEntityCallable;
import ch.cyberduck.core.http.ResponseOutputStream;
import ch.cyberduck.core.i18n.Locale;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.local.Local;
import ch.cyberduck.core.threading.NamedThreadFactory;
import ch.cyberduck.core.transfer.TransferStatus;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.log4j.Logger;
import org.jets3t.service.ServiceException;
import org.jets3t.service.StorageObjectsChunk;
import org.jets3t.service.VersionOrDeleteMarkersChunk;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.acl.CanonicalGrantee;
import org.jets3t.service.acl.EmailAddressGrantee;
import org.jets3t.service.acl.GrantAndPermission;
import org.jets3t.service.acl.GroupGrantee;
import org.jets3t.service.model.BaseVersionOrDeleteMarker;
import org.jets3t.service.model.MultipartPart;
import org.jets3t.service.model.MultipartUpload;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.S3Owner;
import org.jets3t.service.model.S3Version;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.model.container.ObjectKeyAndVersion;
import org.jets3t.service.utils.ServiceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * @version $Id: S3Path.java 10902 2013-04-22 09:29:42Z dkocher $
 */
public class S3Path extends CloudPath {
    private static Logger log = Logger.getLogger(S3Path.class);

    private final S3Session session;

    public S3Path(S3Session s, String parent, String name, int type) {
        super(parent, name, type);
        this.session = s;
    }

    public S3Path(S3Session s, String path, int type) {
        super(path, type);
        this.session = s;
    }

    public S3Path(S3Session s, String parent, Local file) {
        super(parent, file);
        this.session = s;
    }

    public <T> S3Path(S3Session s, T dict) {
        super(dict);
        this.session = s;
    }

    @Override
    public S3Session getSession() {
        return session;
    }

    /**
     * Object details not contained in standard listing.
     *
     * @see #getDetails()
     */
    protected StorageObject _details;

    /**
     * Retrieve and cache object details.
     *
     * @return Object details
     * @throws IOException      I/O error
     * @throws ServiceException Service error
     */
    protected StorageObject getDetails() throws IOException, ServiceException {
        final String container = this.getContainerName();
        if(null == _details || !_details.isMetadataComplete()) {
            try {
                if(this.attributes().isDuplicate()) {
                    _details = this.getSession().getClient().getVersionedObjectDetails(this.attributes().getVersionId(),
                            container, this.getKey());
                }
                else {
                    _details = this.getSession().getClient().getObjectDetails(container, this.getKey());
                }
            }
            catch(ServiceException e) {
                // Anonymous services can only get a publicly-readable object's details
                log.warn("Cannot read object details:" + e.getMessage());
            }
        }
        if(null == _details) {
            log.warn("Cannot read object details.");
            StorageObject object = new StorageObject(this.getKey());
            object.setBucketName(this.getContainerName());
            return object;
        }
        return _details;
    }

    /**
     * Versioning support. Copy a previous version of the object into the same bucket.
     * The copied object becomes the latest version of that object and all object versions are preserved.
     */
    @Override
    public void revert() {
        if(this.attributes().isFile()) {
            try {
                final S3Object destination = new S3Object(this.getKey());
                // Keep same storage class
                destination.setStorageClass(this.attributes().getStorageClass());
                // Keep encryption setting
                destination.setServerSideEncryptionAlgorithm(this.attributes().getEncryption());
                // Apply non standard ACL
                if(Acl.EMPTY.equals(this.attributes().getAcl())) {
                    this.readAcl();
                }
                destination.setAcl(this.convert(this.attributes().getAcl()));
                this.getSession().getClient().copyVersionedObject(this.attributes().getVersionId(),
                        this.getContainerName(), this.getKey(), this.getContainerName(), destination, false);
            }
            catch(ServiceException e) {
                this.error("Cannot revert file", e);
            }
            catch(IOException e) {
                this.error("Cannot revert file", e);
            }
        }
    }

    @Override
    public void readAcl() {
        try {
            final Credentials credentials = this.getSession().getHost().getCredentials();
            if(credentials.isAnonymousLogin()) {
                return;
            }
            final String container = this.getContainerName();
            if(this.isContainer()) {
                // This method can be performed by anonymous services, but can only succeed if the
                // bucket's existing ACL already allows write access by the anonymous user.
                // In general, you can only access the ACL of a bucket if the ACL already in place
                // for that bucket (in S3) allows you to do so.
                this.attributes().setAcl(this.convert(this.getSession().getClient().getBucketAcl(container)));
            }
            else if(attributes().isFile() || attributes().isPlaceholder()) {
                AccessControlList list;
                if(this.getSession().isVersioning(container)) {
                    list = this.getSession().getClient().getVersionedObjectAcl(this.attributes().getVersionId(),
                            container, this.getKey());
                }
                else {
                    // This method can be performed by anonymous services, but can only succeed if the
                    // object's existing ACL already allows read access by the anonymous user.
                    list = this.getSession().getClient().getObjectAcl(container, this.getKey());
                }
                this.attributes().setAcl(this.convert(list));
                this.attributes().setOwner(list.getOwner().getDisplayName());
            }
        }
        catch(ServiceException e) {
            this.error("Cannot read file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot read file attributes", e);
        }
    }

    /**
     * @param list ACL from server
     * @return Editable ACL
     */
    protected Acl convert(final AccessControlList list) {
        if(log.isDebugEnabled()) {
            try {
                log.debug(list.toXml());
            }
            catch(ServiceException e) {
                log.error(e.getMessage());
            }
        }
        Acl acl = new Acl();
        acl.setOwner(new Acl.CanonicalUser(list.getOwner().getId(), list.getOwner().getDisplayName()));
        for(GrantAndPermission grant : list.getGrantAndPermissions()) {
            Acl.Role role = new Acl.Role(grant.getPermission().toString());
            if(grant.getGrantee() instanceof CanonicalGrantee) {
                acl.addAll(new Acl.CanonicalUser(grant.getGrantee().getIdentifier(),
                        ((CanonicalGrantee) grant.getGrantee()).getDisplayName(), false), role);
            }
            else if(grant.getGrantee() instanceof EmailAddressGrantee) {
                acl.addAll(new Acl.EmailUser(grant.getGrantee().getIdentifier()), role);
            }
            else if(grant.getGrantee() instanceof GroupGrantee) {
                acl.addAll(new Acl.GroupUser(grant.getGrantee().getIdentifier()), role);
            }
        }
        return acl;
    }

    private static final String METADATA_HEADER_EXPIRES = "Expires";

    /**
     * Implements http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.21
     *
     * @param expiration Expiration date to set in header
     */
    public void setExpiration(final Date expiration) {
        try {
            this.getSession().check();
            // You can also copy an object and update its metadata at the same time. Perform a
            // copy-in-place  (with the same bucket and object names for source and destination)
            // to update an object's metadata while leaving the object's data unchanged.
            final StorageObject target = this.getDetails();
            target.addMetadata(METADATA_HEADER_EXPIRES, new RFC1123DateFormatter().format(expiration, TimeZone.getTimeZone("UTC")));
            this.getSession().getClient().updateObjectMetadata(this.getContainerName(), target);
        }
        catch(ServiceException e) {
            this.error("Cannot write file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot write file attributes", e);
        }
    }

    public static final String METADATA_HEADER_CACHE_CONTROL = "Cache-Control";

    /**
     * Implements http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9
     *
     * @param maxage Timespan in seconds from when the file is requested
     */
    public void setCacheControl(final String maxage) {
        try {
            this.getSession().check();
            // You can also copy an object and update its metadata at the same time. Perform a
            // copy-in-place  (with the same bucket and object nexames for source and destination)
            // to update an object's metadata while leaving the object's data unchanged.
            final StorageObject target = this.getDetails();
            if(StringUtils.isEmpty(maxage)) {
                target.removeMetadata(METADATA_HEADER_CACHE_CONTROL);
            }
            else {
                target.addMetadata(METADATA_HEADER_CACHE_CONTROL, maxage);
            }
            this.getSession().getClient().updateObjectMetadata(this.getContainerName(), target);
        }
        catch(ServiceException e) {
            this.error("Cannot write file attributes", e);
        }
        catch(IOException e) {
            this.error("Cannot write file attributes", e);
        }
    }

    @Override
    public void readMetadata() {
        if(attributes().isFile() || attributes().isPlaceholder()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Reading metadata of {0}", "Status"),
                        this.getName()));

                final StorageObject target = this.getDetails();
                HashMap<String, String> metadata = new HashMap<String, String>();
                Map<String, Object> source = target.getModifiableMetadata();
                for(Map.Entry<String, Object> entry : source.entrySet()) {
                    metadata.put(entry.getKey(), entry.getValue().toString());
                }
                this.attributes().setEncryption(target.getServerSideEncryptionAlgorithm());
                this.attributes().setMetadata(metadata);
            }
            catch(ServiceException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public void writeMetadata(Map<String, String> meta) {
        if(attributes().isFile() || attributes().isPlaceholder()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Writing metadata of {0}", "Status"),
                        this.getName()));

                final StorageObject target = this.getDetails();
                target.replaceAllMetadata(new HashMap<String, Object>(meta));
                // Apply non standard ACL
                if(Acl.EMPTY.equals(this.attributes().getAcl())) {
                    this.readAcl();
                }
                target.setAcl(this.convert(this.attributes().getAcl()));
                this.getSession().getClient().updateObjectMetadata(this.getContainerName(), target);
                target.setMetadataComplete(false);
            }
            catch(ServiceException e) {
                this.error("Cannot write file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot write file attributes", e);
            }
            finally {
                this.attributes().clear(false, false, false, true);
            }
        }
    }

    @Override
    public void readChecksum() {
        if(attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Compute MD5 hash of {0}", "Status"),
                        this.getName()));

                final StorageObject details = this.getDetails();
                if(StringUtils.isNotEmpty(details.getMd5HashAsHex())) {
                    attributes().setChecksum(details.getMd5HashAsHex());
                }
                else {
                    log.debug("Setting ETag Header as checksum for:" + this.toString());
                    attributes().setChecksum(details.getETag());
                }
                attributes().setETag(details.getETag());
            }
            catch(ServiceException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public void readSize() {
        if(attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting size of {0}", "Status"),
                        this.getName()));

                final StorageObject details = this.getDetails();
                attributes().setSize(details.getContentLength());
            }
            catch(ServiceException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public void readTimestamp() {
        if(attributes().isFile()) {
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Getting timestamp of {0}", "Status"),
                        this.getName()));

                final StorageObject details = this.getDetails();
                attributes().setModificationDate(details.getLastModifiedDate().getTime());
            }
            catch(ServiceException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
    }

    @Override
    public InputStream read(final TransferStatus status) throws IOException {
        try {
            if(this.attributes().isDuplicate()) {
                return this.getSession().getClient().getVersionedObject(attributes().getVersionId(),
                        this.getContainerName(), this.getKey(),
                        null, // ifModifiedSince
                        null, // ifUnmodifiedSince
                        null, // ifMatch
                        null, // ifNoneMatch
                        status.isResume() ? status.getCurrent() : null, null).getDataInputStream();
            }
            return this.getSession().getClient().getObject(this.getContainerName(), this.getKey(),
                    null, // ifModifiedSince
                    null, // ifUnmodifiedSince
                    null, // ifMatch
                    null, // ifNoneMatch
                    status.isResume() ? status.getCurrent() : null, null).getDataInputStream();
        }
        catch(ServiceException e) {
            IOException failure = new IOException(e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }

    @Override
    public void download(BandwidthThrottle throttle, final StreamListener listener,
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

    /**
     * Default size threshold for when to use multipart uploads.
     */
    private static final long DEFAULT_MULTIPART_UPLOAD_THRESHOLD =
            Preferences.instance().getLong("s3.upload.multipart.threshold");

    /**
     * Default minimum part size for upload parts.
     */
    private static final int DEFAULT_MINIMUM_UPLOAD_PART_SIZE =
            Preferences.instance().getInteger("s3.upload.multipart.size");

    /**
     * The maximum allowed parts in a multipart upload.
     */
    public static final int MAXIMUM_UPLOAD_PARTS = 10000;

    @Override
    public void upload(final BandwidthThrottle throttle, final StreamListener listener, final TransferStatus status) {
        try {
            if(attributes().isFile()) {
                final StorageObject object = this.createObjectDetails();

                this.getSession().message(MessageFormat.format(Locale.localizedString("Uploading {0}", "Status"),
                        this.getName()));

                if(this.getSession().isMultipartUploadSupported()
                        && status.getLength() > DEFAULT_MULTIPART_UPLOAD_THRESHOLD) {
                    this.uploadMultipart(throttle, listener, status, object);
                }
                else {
                    this.uploadSingle(throttle, listener, status, object);
                }
            }
        }
        catch(ServiceException e) {
            this.error("Upload failed", e);
        }
        catch(IOException e) {
            this.error("Upload failed", e);
        }
    }

    private StorageObject createObjectDetails() throws IOException {
        final StorageObject object = new StorageObject(this.getKey());
        final String type = new MappingMimeTypeService().getMime(getName());
        object.setContentType(type);
        if(Preferences.instance().getBoolean("s3.upload.metadata.md5")) {
            this.getSession().message(MessageFormat.format(
                    Locale.localizedString("Compute MD5 hash of {0}", "Status"), this.getName()));
            object.setMd5Hash(ServiceUtils.fromHex(this.getLocal().attributes().getChecksum()));
        }
        Acl acl = this.attributes().getAcl();
        if(Acl.EMPTY.equals(acl)) {
            if(Preferences.instance().getProperty("s3.bucket.acl.default").equals("public-read")) {
                object.setAcl(this.getSession().getPublicCannedReadAcl());
            }
            else {
                // Owner gets FULL_CONTROL. No one else has access rights (default).
                object.setAcl(this.getSession().getPrivateCannedAcl());
            }
        }
        else {
            object.setAcl(this.convert(acl));
        }
        // Storage class
        if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.storage.class"))) {
            object.setStorageClass(Preferences.instance().getProperty("s3.storage.class"));
        }
        if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.encryption.algorithm"))) {
            object.setServerSideEncryptionAlgorithm(Preferences.instance().getProperty("s3.encryption.algorithm"));
        }
        // Default metadata for new files
        for(String m : Preferences.instance().getList("s3.metadata.default")) {
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
                log.warn(String.format("Missing key in header %s", m));
                continue;
            }
            String value = m.substring(split + 1);
            if(StringUtils.isEmpty(value)) {
                log.warn(String.format("Missing value in header %s", m));
                continue;
            }
            object.addMetadata(name, value);
        }
        return object;
    }

    /**
     * @param throttle Bandwidth throttle
     * @param listener Callback for bytes sent
     * @param status   Transfer status
     * @param object   File location
     * @throws IOException      I/O error
     * @throws ServiceException Service error
     */
    private void uploadSingle(final BandwidthThrottle throttle, final StreamListener listener,
                              final TransferStatus status, final StorageObject object)
            throws IOException, ServiceException {

        InputStream in = null;
        ResponseOutputStream<StorageObject> out = null;
        MessageDigest digest = null;
        if(!Preferences.instance().getBoolean("s3.upload.metadata.md5")) {
            // Content-MD5 not set. Need to verify ourselves instad of S3
            try {
                digest = MessageDigest.getInstance("MD5");
            }
            catch(NoSuchAlgorithmException e) {
                log.error(e.getMessage());
            }
        }
        try {
            if(null == digest) {
                log.warn("MD5 calculation disabled");
                in = this.getLocal().getInputStream();
            }
            else {
                in = new DigestInputStream(this.getLocal().getInputStream(), digest);
            }
            out = this.write(object, status.getLength() - status.getCurrent(),
                    Collections.<String, String>emptyMap());
            this.upload(out, in, throttle, listener, status);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        if(null != digest) {
            final StorageObject part = out.getResponse();
            this.getSession().message(MessageFormat.format(
                    Locale.localizedString("Compute MD5 hash of {0}", "Status"), this.getName()));
            // Obtain locally-calculated MD5 hash.
            String hexMD5 = ServiceUtils.toHex(digest.digest());
            this.getSession().getClient().verifyExpectedAndActualETagValues(hexMD5, part);
        }
    }

    /**
     * @param throttle Bandwidth throttle
     * @param listener Callback for bytes sent
     * @param status   Transfer status
     * @param object   File location
     * @throws IOException      I/O error
     * @throws ServiceException Service error
     */
    private void uploadMultipart(final BandwidthThrottle throttle, final StreamListener listener,
                                 final TransferStatus status, final StorageObject object)
            throws IOException, ServiceException {

        final ThreadFactory threadFactory = new NamedThreadFactory("multipart");

        MultipartUpload multipart = null;
        if(status.isResume()) {
            // This operation lists in-progress multipart uploads. An in-progress multipart upload is a
            // multipart upload that has been initiated, using the Initiate Multipart Upload request, but has
            // not yet been completed or aborted.
            final List<MultipartUpload> uploads = this.getSession().getClient().multipartListUploads(this.getContainerName());
            for(MultipartUpload upload : uploads) {
                if(!upload.getBucketName().equals(this.getContainerName())) {
                    continue;
                }
                if(!upload.getObjectKey().equals(this.getKey())) {
                    continue;
                }
                if(log.isInfoEnabled()) {
                    log.info(String.format("Resume multipart upload %s", upload.getUploadId()));
                }
                multipart = upload;
                break;
            }
        }
        if(null == multipart) {
            log.info("No pending multipart upload found");

            // Initiate multipart upload with metadata
            Map<String, Object> metadata = object.getModifiableMetadata();
            if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.storage.class"))) {
                metadata.put(this.getSession().getClient().getRestHeaderPrefix() + "storage-class",
                        Preferences.instance().getProperty("s3.storage.class"));
            }
            if(StringUtils.isNotBlank(Preferences.instance().getProperty("s3.encryption.algorithm"))) {
                metadata.put(this.getSession().getClient().getRestHeaderPrefix() + "server-side-encryption",
                        Preferences.instance().getProperty("s3.encryption.algorithm"));
            }

            multipart = this.getSession().getClient().multipartStartUpload(
                    this.getContainerName(), this.getKey(), metadata);
        }

        final List<MultipartPart> completed;
        if(status.isResume()) {
            log.info(String.format("List completed parts of %s", multipart.getUploadId()));
            // This operation lists the parts that have been uploaded for a specific multipart upload.
            completed = this.getSession().getClient().multipartListParts(multipart);
        }
        else {
            completed = new ArrayList<MultipartPart>();
        }

        /**
         * At any point, at most
         * <tt>nThreads</tt> threads will be active processing tasks.
         */
        final ExecutorService pool = Executors.newFixedThreadPool(
                Preferences.instance().getInteger("s3.upload.multipart.concurency"), threadFactory);

        try {
            final List<Future<MultipartPart>> parts = new ArrayList<Future<MultipartPart>>();

            final long defaultPartSize = Math.max((status.getLength() / MAXIMUM_UPLOAD_PARTS),
                    DEFAULT_MINIMUM_UPLOAD_PART_SIZE);

            long remaining = status.getLength();
            long marker = 0;

            for(int partNumber = 1; remaining > 0; partNumber++) {
                boolean skip = false;
                if(status.isResume()) {
                    log.info(String.format("Determine if part %d can be skipped", partNumber));
                    for(MultipartPart c : completed) {
                        if(c.getPartNumber().equals(partNumber)) {
                            log.info("Skip completed part number " + partNumber);
                            listener.bytesSent(c.getSize());
                            skip = true;
                            break;
                        }
                    }
                }

                // Last part can be less than 5 MB. Adjust part size.
                final long length = Math.min(defaultPartSize, remaining);

                if(!skip) {
                    // Submit to queue
                    parts.add(this.submitPart(throttle, listener, status, multipart, pool, partNumber, marker, length));
                }

                remaining -= length;
                marker += length;
            }
            for(Future<MultipartPart> future : parts) {
                try {
                    completed.add(future.get());
                }
                catch(InterruptedException e) {
                    log.error("Part upload failed:" + e.getMessage());
                    throw new ConnectionCanceledException(e.getMessage(), e);
                }
                catch(ExecutionException e) {
                    log.warn("Part upload failed:" + e.getMessage());
                    if(e.getCause() instanceof ServiceException) {
                        throw (ServiceException) e.getCause();
                    }
                    if(e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new ConnectionCanceledException(e.getMessage(), e);
                }
            }
            if(status.isComplete()) {
                this.getSession().getClient().multipartCompleteUpload(multipart, completed);
            }
        }
        finally {
            if(!status.isComplete()) {
                // Cancel all previous parts
                log.info(String.format("Cancel multipart upload %s", multipart.getUploadId()));
                this.getSession().getClient().multipartAbortUpload(multipart);
            }
            // Cancel future tasks
            pool.shutdown();
        }
    }

    private Future<MultipartPart> submitPart(final BandwidthThrottle throttle, final StreamListener listener,
                                             final TransferStatus status, final MultipartUpload multipart,
                                             final ExecutorService pool,
                                             final int partNumber,
                                             final long offset, final long length) throws ConnectionCanceledException {
        if(pool.isShutdown()) {
            throw new ConnectionCanceledException();
        }
        log.info(String.format("Submit part %d to queue", partNumber));
        return pool.submit(new Callable<MultipartPart>() {
            @Override
            public MultipartPart call() throws IOException, ServiceException {
                final Map<String, String> requestParameters = new HashMap<String, String>();
                requestParameters.put("uploadId", multipart.getUploadId());
                requestParameters.put("partNumber", String.valueOf(partNumber));

                InputStream in = null;
                ResponseOutputStream<StorageObject> out = null;
                MessageDigest digest = null;
                try {
                    if(!Preferences.instance().getBoolean("s3.upload.metadata.md5")) {
                        // Content-MD5 not set. Need to verify ourselves instad of S3
                        try {
                            digest = MessageDigest.getInstance("MD5");
                        }
                        catch(NoSuchAlgorithmException e) {
                            log.error(e.getMessage());
                        }
                    }
                    if(null == digest) {
                        log.warn("MD5 calculation disabled");
                        in = getLocal().getInputStream();
                    }
                    else {
                        in = new DigestInputStream(getLocal().getInputStream(), digest);
                    }
                    out = write(new StorageObject(getKey()), length, requestParameters);
                    upload(out, in, throttle, listener, offset, length, status);
                }
                finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                }
                final StorageObject part = out.getResponse();
                if(null != digest) {
                    // Obtain locally-calculated MD5 hash
                    String hexMD5 = ServiceUtils.toHex(digest.digest());
                    getSession().getClient().verifyExpectedAndActualETagValues(hexMD5, part);
                }
                // Populate part with response data that is accessible via the object's metadata
                return new MultipartPart(partNumber, part.getLastModifiedDate(),
                        part.getETag(), part.getContentLength());
            }
        });
    }

    @Override
    public OutputStream write(final TransferStatus status) throws IOException {
        return this.write(this.createObjectDetails(), status.getLength() - status.getCurrent(),
                Collections.<String, String>emptyMap());
    }

    private ResponseOutputStream<StorageObject> write(final StorageObject part, final long contentLength,
                                                      final Map<String, String> requestParams) throws IOException {
        DelayedHttpEntityCallable<StorageObject> command = new DelayedHttpEntityCallable<StorageObject>() {
            @Override
            public StorageObject call(AbstractHttpEntity entity) throws IOException {
                try {
                    getSession().getClient().putObjectWithRequestEntityImpl(getContainerName(), part, entity, requestParams);
                }
                catch(ServiceException e) {
                    IOException failure = new IOException(e.getMessage());
                    failure.initCause(e);
                    throw failure;
                }
                return part;
            }

            @Override
            public long getContentLength() {
                return contentLength;
            }
        };
        return this.write(command);
    }

    @Override
    public AttributedList<Path> list(final AttributedList<Path> children) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Listing directory {0}", "Status"),
                    this.getName()));

            if(this.isRoot()) {
                // List all buckets
                for(StorageBucket bucket : this.getSession().getBuckets(true)) {
                    Path p = PathFactory.createPath(this.getSession(), this.getAbsolute(), bucket.getName(),
                            VOLUME_TYPE | DIRECTORY_TYPE);
                    if(null != bucket.getOwner()) {
                        p.attributes().setOwner(bucket.getOwner().getDisplayName());
                    }
                    if(null != bucket.getCreationDate()) {
                        p.attributes().setCreationDate(bucket.getCreationDate().getTime());
                    }
                    children.add(p);
                }
            }
            else {
                final String container = this.getContainerName();
                // Keys can be listed by prefix. By choosing a common prefix
                // for the names of related keys and marking these keys with
                // a special character that delimits hierarchy, you can use the list
                // operation to select and browse keys hierarchically
                String prefix = StringUtils.EMPTY;
                if(!this.isContainer()) {
                    // estricts the response to only contain results that begin with the
                    // specified prefix. If you omit this optional argument, the value
                    // of Prefix for your query will be the empty string.
                    // In other words, the results will be not be restricted by prefix.
                    prefix = this.getKey();
                    if(!prefix.endsWith(String.valueOf(Path.DELIMITER))) {
                        prefix += Path.DELIMITER;
                    }
                }
                // If this optional, Unicode string parameter is included with your request,
                // then keys that contain the same string between the prefix and the first
                // occurrence of the delimiter will be rolled up into a single result
                // element in the CommonPrefixes collection. These rolled-up keys are
                // not returned elsewhere in the response.
                final String delimiter = String.valueOf(Path.DELIMITER);
                children.addAll(this.listObjects(container, prefix, delimiter));
                if(Preferences.instance().getBoolean("s3.revisions.enable")) {
                    if(this.getSession().isVersioning(container)) {
                        String priorLastKey = null;
                        String priorLastVersionId = null;
                        do {
                            final VersionOrDeleteMarkersChunk chunk = this.getSession().getClient().listVersionedObjectsChunked(
                                    container, prefix, delimiter,
                                    Preferences.instance().getInteger("s3.listing.chunksize"),
                                    priorLastKey, priorLastVersionId, true);
                            children.addAll(this.listVersions(container, Arrays.asList(chunk.getItems())));
                            priorLastKey = chunk.getNextKeyMarker();
                            priorLastVersionId = chunk.getNextVersionIdMarker();
                        }
                        while(priorLastKey != null);
                    }
                }
            }
        }
        catch(ServiceException e) {
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

    protected AttributedList<Path> listObjects(String bucket, String prefix, String delimiter)
            throws IOException, ServiceException {
        final AttributedList<Path> children = new AttributedList<Path>();
        // Null if listing is complete
        String priorLastKey = null;
        do {
            // Read directory listing in chunks. List results are always returned
            // in lexicographic (alphabetical) order.
            final StorageObjectsChunk chunk = this.getSession().getClient().listObjectsChunked(
                    bucket, prefix, delimiter,
                    Preferences.instance().getInteger("s3.listing.chunksize"), priorLastKey);

            final StorageObject[] objects = chunk.getObjects();
            for(StorageObject object : objects) {
                final S3Path p = (S3Path) PathFactory.createPath(this.getSession(), bucket,
                        object.getKey(), FILE_TYPE);
                p.setParent(this);
                p.attributes().setSize(object.getContentLength());
                p.attributes().setModificationDate(object.getLastModifiedDate().getTime());
                // Directory placeholders
                if(object.isDirectoryPlaceholder()) {
                    p.attributes().setType(DIRECTORY_TYPE);
                    p.attributes().setPlaceholder(true);
                }
                else if(0 == object.getContentLength()) {
                    if("application/x-directory".equals(p.getDetails().getContentType())) {
                        p.attributes().setType(DIRECTORY_TYPE);
                        p.attributes().setPlaceholder(true);
                    }
                }
                final Object etag = object.getMetadataMap().get(StorageObject.METADATA_HEADER_ETAG);
                if(null != etag) {
                    String s = etag.toString().replaceAll("\"", StringUtils.EMPTY);
                    p.attributes().setChecksum(s);
                    if(s.equals("d66759af42f282e1ba19144df2d405d0")) {
                        // Fix #5374 s3sync.rb interoperability
                        p.attributes().setType(DIRECTORY_TYPE);
                        p.attributes().setPlaceholder(true);
                    }
                }
                p.attributes().setStorageClass(object.getStorageClass());
                p.attributes().setEncryption(object.getServerSideEncryptionAlgorithm());
                if(object instanceof S3Object) {
                    p.attributes().setVersionId(((S3Object) object).getVersionId());
                }
                children.add(p);
            }
            final String[] prefixes = chunk.getCommonPrefixes();
            for(String common : prefixes) {
                if(common.equals(String.valueOf(Path.DELIMITER))) {
                    log.warn("Skipping prefix " + common);
                    continue;
                }
                final Path p = PathFactory.createPath(this.getSession(),
                        bucket, common, DIRECTORY_TYPE);
                p.setParent(this);
                if(children.contains(p.getReference())) {
                    continue;
                }
                p.attributes().setPlaceholder(false);
                children.add(p);
            }
            priorLastKey = chunk.getPriorLastKey();
        }
        while(priorLastKey != null);
        return children;
    }

    private List<Path> listVersions(String bucket, List<BaseVersionOrDeleteMarker> versionOrDeleteMarkers)
            throws IOException, ServiceException {
        // Amazon S3 returns object versions in the order in which they were
        // stored, with the most recently stored returned first.
        Collections.sort(versionOrDeleteMarkers, new Comparator<BaseVersionOrDeleteMarker>() {
            @Override
            public int compare(BaseVersionOrDeleteMarker o1, BaseVersionOrDeleteMarker o2) {
                return o1.getLastModified().compareTo(o2.getLastModified());
            }
        });
        final List<Path> versions = new ArrayList<Path>();
        int i = 0;
        for(BaseVersionOrDeleteMarker marker : versionOrDeleteMarkers) {
            if((marker.isDeleteMarker() && marker.isLatest())
                    || !marker.isLatest()) {
                // Latest version already in default listing
                final S3Path path = (S3Path) PathFactory.createPath(this.getSession(),
                        bucket, marker.getKey(), FILE_TYPE);
                path.setParent(this);
                // Versioning is enabled if non null.
                path.attributes().setVersionId(marker.getVersionId());
                path.attributes().setRevision(++i);
                path.attributes().setDuplicate(true);
                path.attributes().setModificationDate(marker.getLastModified().getTime());
                if(marker instanceof S3Version) {
                    path.attributes().setSize(((S3Version) marker).getSize());
                    path.attributes().setETag(((S3Version) marker).getEtag());
                    path.attributes().setStorageClass(((S3Version) marker).getStorageClass());
                }
                versions.add(path);
            }
        }
        return versions;
    }

    @Override
    public void mkdir() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Making directory {0}", "Status"),
                    this.getName()));

            if(this.isContainer()) {
                // Create bucket
                if(!ServiceUtils.isBucketNameValidDNSName(this.getName())) {
                    throw new ServiceException(Locale.localizedString("Bucket name is not DNS compatible", "S3"));
                }
                String location = Preferences.instance().getProperty("s3.location");
                if(!this.getSession().getHost().getProtocol().getLocations().contains(location)) {
                    log.warn("Default bucket location not supported by provider:" + location);
                    location = "US";
                    log.warn("Fallback to US");
                }
                AccessControlList acl;
                if(Preferences.instance().getProperty("s3.bucket.acl.default").equals("public-read")) {
                    acl = this.getSession().getPublicCannedReadAcl();
                }
                else {
                    acl = this.getSession().getPrivateCannedAcl();
                }
                this.getSession().getClient().createBucket(this.getContainerName(), location, acl);
            }
            else {
                StorageObject object = new StorageObject(this.getKey() + Path.DELIMITER);
                object.setBucketName(this.getContainerName());
                // Set object explicitly to private access by default.
                object.setAcl(this.getSession().getPrivateCannedAcl());
                object.setContentLength(0);
                object.setContentType("application/x-directory");
                this.getSession().getClient().putObject(this.getContainerName(), object);
            }
        }
        catch(ServiceException e) {
            this.error("Cannot create folder {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot create folder {0}", e);
        }
    }

    /**
     * Write ACL to bucket or object.
     *
     * @param acl       The updated access control list.
     * @param recursive Descend into directory placeholders
     */
    @Override
    public void writeAcl(Acl acl, boolean recursive) {
        try {
            if(null == acl.getOwner()) {
                // Owner is lost in controller
                acl.setOwner(this.attributes().getAcl().getOwner());
            }
            if(this.isContainer()) {
                this.getSession().getClient().putBucketAcl(this.getContainerName(), this.convert(acl));
            }
            else {
                if(attributes().isFile() || attributes().isPlaceholder()) {
                    this.getSession().getClient().putObjectAcl(this.getContainerName(), this.getKey(), this.convert(acl));
                }
                if(attributes().isDirectory()) {
                    if(recursive) {
                        for(Path child : this.children()) {
                            if(!this.getSession().isConnected()) {
                                break;
                            }
                            // Existing ACL might not be cached
                            if(Acl.EMPTY.equals(child.attributes().getAcl())) {
                                child.readAcl();
                            }
                            final List<Acl.UserAndRole> existing = child.attributes().getAcl().asList();
                            acl.addAll(existing.toArray(new Acl.UserAndRole[existing.size()]));
                            child.writeAcl(acl, recursive);
                        }
                    }
                }
            }
        }
        catch(ServiceException e) {
            this.error("Cannot change permissions", e);
        }
        catch(IOException e) {
            this.error("Cannot change permissions", e);
        }
        finally {
            this.attributes().clear(false, false, true, false);
        }
    }

    /**
     * Convert ACL for writing to service.
     *
     * @param acl Edited ACL
     * @return ACL to write to server
     */
    protected AccessControlList convert(Acl acl) {
        if(null == acl) {
            return null;
        }
        AccessControlList list = new AccessControlList();
        list.setOwner(new S3Owner(acl.getOwner().getIdentifier(), acl.getOwner().getDisplayName()));
        for(Acl.UserAndRole userAndRole : acl.asList()) {
            if(!userAndRole.isValid()) {
                continue;
            }
            if(userAndRole.getUser() instanceof Acl.EmailUser) {
                list.grantPermission(new EmailAddressGrantee(userAndRole.getUser().getIdentifier()),
                        org.jets3t.service.acl.Permission.parsePermission(userAndRole.getRole().getName()));
            }
            else if(userAndRole.getUser() instanceof Acl.GroupUser) {
                list.grantPermission(new GroupGrantee(userAndRole.getUser().getIdentifier()),
                        org.jets3t.service.acl.Permission.parsePermission(userAndRole.getRole().getName()));
            }
            else if(userAndRole.getUser() instanceof Acl.CanonicalUser) {
                list.grantPermission(new CanonicalGrantee(userAndRole.getUser().getIdentifier()),
                        org.jets3t.service.acl.Permission.parsePermission(userAndRole.getRole().getName()));
            }
            else {
                log.warn("Unsupported user:" + userAndRole.getUser());
            }
        }
        if(log.isDebugEnabled()) {
            try {
                log.debug(list.toXml());
            }
            catch(ServiceException e) {
                log.error(e.getMessage());
            }
        }
        return list;
    }

    @Override
    public void delete() {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Deleting {0}", "Status"),
                    this.getName()));

            final String container = this.getContainerName();
            if(attributes().isFile()) {
                this.delete(container, Collections.singletonList(
                        new ObjectKeyAndVersion(this.getKey(), this.attributes().getVersionId())));
            }
            else if(attributes().isDirectory()) {
                final List<ObjectKeyAndVersion> files = new ArrayList<ObjectKeyAndVersion>();
                for(Path child : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    if(child.attributes().isDirectory()) {
                        child.delete();
                    }
                    else {
                        files.add(new ObjectKeyAndVersion(child.getKey(), child.attributes().getVersionId()));
                    }
                }
                if(!this.isContainer()) {
                    // Because we normalize paths and remove a trailing delimiter we add it here again as the
                    // default directory placeholder formats has the format `/placeholder/' as a key.
                    files.add(new ObjectKeyAndVersion(this.getKey() + Path.DELIMITER,
                            this.attributes().getVersionId()));
                    // Always returning 204 even if the key does not exist.
                    // Fallback to legacy directory placeholders with metadata instead of key with trailing delimiter
                    files.add(new ObjectKeyAndVersion(this.getKey(),
                            this.attributes().getVersionId()));
                    // AWS does not return 404 for non-existing keys
                }
                if(!files.isEmpty()) {
                    this.delete(container, files);
                }
                if(this.isContainer()) {
                    // Finally delete bucket itself
                    this.getSession().getClient().deleteBucket(container);
                }
            }
        }
        catch(ServiceException e) {
            this.error("Cannot delete {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot delete {0}", e);
        }
    }

    /**
     * @param container Bucket
     * @param keys      Key and version ID for versioned object or null
     * @throws ConnectionCanceledException Authentication canceled for MFA delete
     * @throws ServiceException            Service error
     */
    protected void delete(String container, List<ObjectKeyAndVersion> keys) throws ConnectionCanceledException, ServiceException {
        if(this.getSession().isMultiFactorAuthentication(container)) {
            final LoginController c = LoginControllerFactory.get(this.getSession());
            final Credentials credentials = this.getSession().mfa(c);
            this.getSession().getClient().deleteMultipleObjectsWithMFA(container,
                    keys.toArray(new ObjectKeyAndVersion[keys.size()]),
                    credentials.getUsername(),
                    credentials.getPassword(),
                    true);
        }
        else {
            if(this.getHost().getHostname().equals(Protocol.S3_SSL.getDefaultHostname())) {
                this.getSession().getClient().deleteMultipleObjects(container,
                        keys.toArray(new ObjectKeyAndVersion[keys.size()]),
                        true);
            }
            else {
                for(ObjectKeyAndVersion k : keys) {
                    this.getSession().getClient().deleteObject(container, k.getKey());
                }
            }
        }
    }

    @Override
    public void rename(AbstractPath renamed) {
        try {
            this.getSession().check();
            this.getSession().message(MessageFormat.format(Locale.localizedString("Renaming {0} to {1}", "Status"),
                    this.getName(), renamed));

            if(attributes().isFile() || attributes().isPlaceholder()) {
                final StorageObject destination = new StorageObject(((S3Path) renamed).getKey());
                // Keep same storage class
                destination.setStorageClass(this.attributes().getStorageClass());
                // Keep encryption setting
                destination.setServerSideEncryptionAlgorithm(this.attributes().getEncryption());
                // Apply non standard ACL
                if(Acl.EMPTY.equals(this.attributes().getAcl())) {
                    this.readAcl();
                }
                destination.setAcl(this.convert(this.attributes().getAcl()));
                // Moving the object retaining the metadata of the original.
                this.getSession().getClient().moveObject(this.getContainerName(), this.getKey(), ((S3Path) renamed).getContainerName(),
                        destination, false);
            }
            else if(attributes().isDirectory()) {
                for(AbstractPath i : this.children()) {
                    if(!this.getSession().isConnected()) {
                        break;
                    }
                    i.rename(PathFactory.createPath(this.getSession(), renamed.getAbsolute(),
                            i.getName(), i.attributes().getType()));
                }
            }
        }
        catch(ServiceException e) {
            this.error("Cannot rename {0}", e);
        }
        catch(IOException e) {
            this.error("Cannot rename {0}", e);
        }
    }

    @Override
    public void copy(AbstractPath copy, BandwidthThrottle throttle, StreamListener listener, final TransferStatus status) {
        if(((Path) copy).getSession().equals(this.getSession())) {
            // Copy on same server
            try {
                this.getSession().check();
                this.getSession().message(MessageFormat.format(Locale.localizedString("Copying {0} to {1}", "Status"),
                        this.getName(), copy));

                if(this.attributes().isFile()) {
                    StorageObject destination = new StorageObject(((S3Path) copy).getKey());
                    // Keep same storage class
                    destination.setStorageClass(this.attributes().getStorageClass());
                    // Keep encryption setting
                    destination.setServerSideEncryptionAlgorithm(this.attributes().getEncryption());
                    // Apply non standard ACL
                    if(Acl.EMPTY.equals(this.attributes().getAcl())) {
                        this.readAcl();
                    }
                    destination.setAcl(this.convert(this.attributes().getAcl()));
                    // Copying object applying the metadata of the original
                    this.getSession().getClient().copyObject(this.getContainerName(), this.getKey(),
                            ((S3Path) copy).getContainerName(), destination, false);
                    listener.bytesSent(this.attributes().getSize());
                    status.setComplete();
                }
            }
            catch(ServiceException e) {
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
     * Overwritten to provide publicly accessible URL of given object
     *
     * @return Using scheme from protocol
     */
    @Override
    public String toURL() {
        return this.toURL(this.getHost().getProtocol().getScheme().toString());
    }

    /**
     * Overwritten to provide publicy accessible URL of given object
     *
     * @return Plain HTTP link
     */
    @Override
    public String toHttpURL() {
        return this.toURL("http");
    }

    /**
     * Properly URI encode and prepend the bucket name.
     *
     * @param scheme Protocol
     * @return URL to be displayed in browser
     */
    private String toURL(final String scheme) {
        final StringBuilder url = new StringBuilder(scheme);
        url.append("://");
        if(this.isRoot()) {
            url.append(this.getHost().getHostname());
        }
        else {
            String container = this.getContainerName();
            String hostname = this.getSession().getHostnameForContainer(container);
            if(hostname.startsWith(container)) {
                url.append(hostname);
                if(!this.isContainer()) {
                    url.append(URIEncoder.encode(this.getKey()));
                }
            }
            else {
                url.append(this.getSession().getHost().getHostname());
                url.append(URIEncoder.encode(this.getAbsolute()));
            }
        }
        return url.toString();
    }

    /**
     * Query string authentication. Query string authentication is useful for giving HTTP or browser access to
     * resources that would normally require authentication. The signature in the query string secures the request
     *
     * @return A signed URL with a limited validity over time.
     */
    public DescriptiveUrl toSignedUrl() {
        return toSignedUrl(Preferences.instance().getInteger("s3.url.expire.seconds"));
    }

    /**
     * @param seconds Expire after seconds elapsed
     * @return Temporary URL to be displayed in browser
     */
    protected DescriptiveUrl toSignedUrl(final int seconds) {
        Calendar expiry = Calendar.getInstance();
        expiry.add(Calendar.SECOND, seconds);
        return new DescriptiveUrl(this.createSignedUrl(seconds),
                MessageFormat.format(Locale.localizedString("{0} URL"), Locale.localizedString("Signed", "S3"))
                        + " (" + MessageFormat.format(Locale.localizedString("Expires on {0}", "S3") + ")",
                        UserDateFormatterFactory.get().getShortFormat(expiry.getTimeInMillis()))
        );
    }

    /**
     * Query String Authentication generates a signed URL string that will grant
     * access to an S3 resource (bucket or object)
     * to whoever uses the URL up until the time specified.
     *
     * @param expiry Validity of URL
     * @return Temporary URL to be displayed in browser
     */
    private String createSignedUrl(final int expiry) {
        if(this.attributes().isFile()) {
            try {
                if(this.getSession().getHost().getCredentials().isAnonymousLogin()) {
                    log.info("Anonymous cannot create signed URL");
                    return null;
                }
                // Determine expiry time for URL
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, expiry);
                long secondsSinceEpoch = cal.getTimeInMillis() / 1000;

                // Generate URL
                return this.getSession().getClient().createSignedUrl("GET",
                        this.getContainerName(), this.getKey(), null,
                        null, secondsSinceEpoch, false, this.getHost().getProtocol().isSecure(), false);
            }
            catch(ServiceException e) {
                this.error("Cannot read file attributes", e);
            }
            catch(IOException e) {
                this.error("Cannot read file attributes", e);
            }
        }
        return null;
    }

    /**
     * Generates a URL string that will return a Torrent file for an object in S3,
     * which file can be downloaded and run in a BitTorrent client.
     *
     * @return Torrent URL
     */
    public DescriptiveUrl toTorrentUrl() {
        if(this.attributes().isFile()) {
            try {
                return new DescriptiveUrl(this.getSession().getClient().createTorrentUrl(this.getContainerName(),
                        this.getKey()));
            }
            catch(ConnectionCanceledException e) {
                log.warn(e.getMessage());
            }
        }
        return new DescriptiveUrl(null, null);
    }

    @Override
    public Set<DescriptiveUrl> getHttpURLs() {
        final Set<DescriptiveUrl> urls = super.getHttpURLs();
        // Always include HTTP URL
        urls.add(new DescriptiveUrl(this.toURL("http"),
                MessageFormat.format(Locale.localizedString("{0} URL"), "http".toUpperCase(java.util.Locale.ENGLISH))));
        DescriptiveUrl hour = this.toSignedUrl(60 * 60);
        if(StringUtils.isNotBlank(hour.getUrl())) {
            urls.add(hour);
        }
        // Default signed URL expiring in 24 hours.
        DescriptiveUrl day = this.toSignedUrl(Preferences.instance().getInteger("s3.url.expire.seconds"));
        if(StringUtils.isNotBlank(day.getUrl())) {
            urls.add(day);
        }
        DescriptiveUrl week = this.toSignedUrl(7 * 24 * 60 * 60);
        if(StringUtils.isNotBlank(week.getUrl())) {
            urls.add(week);
        }
        DescriptiveUrl torrent = this.toTorrentUrl();
        if(StringUtils.isNotBlank(torrent.getUrl())) {
            urls.add(new DescriptiveUrl(torrent.getUrl(),
                    MessageFormat.format(Locale.localizedString("{0} URL"), Locale.localizedString("Torrent"))));
        }
        return urls;
    }
}