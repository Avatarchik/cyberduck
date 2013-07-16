package ch.cyberduck.core.transfer;

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

import ch.cyberduck.core.Preferences;
import ch.cyberduck.core.local.ApplicationBadgeLabelerFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @version $Id: Queue.java 10915 2013-04-23 13:05:12Z dkocher $
 */
public class Queue {
    private static final Logger log = Logger.getLogger(Queue.class);

    private static Queue instance;

    private static final Object lock = new Object();

    public static Queue instance() {
        synchronized(lock) {
            if(null == instance) {
                instance = new Queue();
            }
            return instance;
        }
    }

    /**
     * One transfer at least is always allowed to run. Queued accesses for threads blocked
     * on insertion or removal, are processed in FIFO order
     */
    private ArrayBlockingQueue<Transfer> overflow
            = new ArrayBlockingQueue<Transfer>(1, true);


    /**
     * All running transfers.
     */
    private List<Transfer> running
            = Collections.synchronizedList(new ArrayList<Transfer>());

    /**
     * Idle this transfer until a free slot is avilable depending on
     * the maximum number of concurrent transfers allowed in the Preferences.
     *
     * @param t This transfer should respect the settings for maximum number of transfers
     */
    public void add(final Transfer t) {
        if(log.isDebugEnabled()) {
            log.debug("add:" + t);
        }
        if(running.size() >= Preferences.instance().getInteger("queue.maxtransfers")) {
            t.fireTransferQueued();
            if(log.isInfoEnabled()) {
                log.info("Queuing:" + t);
            }
            while(running.size() >= Preferences.instance().getInteger("queue.maxtransfers")) {
                // The maximum number of transfers is already reached
                if(t.isCanceled()) {
                    break;
                }
                // Wait for transfer slot.
                try {
                    overflow.put(t);
                }
                catch(InterruptedException e) {
                    log.error(e.getMessage());
                }
            }
            if(log.isInfoEnabled()) {
                log.info("Released from queue:" + t);
            }
            t.fireTransferResumed();
        }
        running.add(t);
        ApplicationBadgeLabelerFactory.get().badge(String.valueOf(running.size()));
    }

    /**
     * @param t Transfer to drop from queue
     */
    public void remove(final Transfer t) {
        if(running.remove(t)) {
            if(0 == running.size()) {
                ApplicationBadgeLabelerFactory.get().badge(StringUtils.EMPTY);
            }
            else {
                ApplicationBadgeLabelerFactory.get().badge(String.valueOf(running.size()));
            }
            // Transfer has finished.
            this.poll();
        }
        else {
            // Transfer was still in the queue and has not started yet.
            overflow.remove(t);
        }
    }

    /**
     * Resize queue with current setting in preferences.
     */
    public void resize() {
        log.debug("resize");
        int size = running.size();
        while(size < Preferences.instance().getInteger("queue.maxtransfers")) {
            if(overflow.isEmpty()) {
                log.debug("No more waiting transfers in queue");
                break;
            }
            this.poll();
            size++;
        }
    }

    private void poll() {
        log.debug("poll");
        // Clear space for other transfer from the head of the queue
        overflow.poll();
    }
}