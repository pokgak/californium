/*******************************************************************************
 * Copyright (c) 2019 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - Initial creation
 ******************************************************************************/
package org.eclipse.californium.examples.coaputil;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;

public class CoapProgress {
    public String uriString;
    public int state;
    public URI uri;
    public String host;
    public InetAddress hostAddresses[];
    public Long dnsResolveNanoTime;
    public Long connectTime;
    public int dtlsRetries;
    public int coapRetries;
    public int retries;
    public int blocks;
    public long rxStart;
    public long txStart;
    public long rxEnd;
    public long txEnd;
    public long[] rxTotal;
    public boolean connectOnRetry;
    public boolean storeDns;

    public CoapProgress(CoapProgress progress) {
        this.uriString = progress.uriString;
        this.state = progress.state;
        this.uri = progress.uri;
        this.host = progress.host;
        this.hostAddresses = progress.hostAddresses;
        this.dnsResolveNanoTime = progress.dnsResolveNanoTime;
        this.connectTime = progress.connectTime;
        this.dtlsRetries = progress.dtlsRetries;
        this.coapRetries = progress.coapRetries;
        this.retries = progress.retries;
        this.blocks = progress.blocks;
        this.rxStart = progress.rxStart;
        this.txStart = progress.txStart;
        this.rxEnd = progress.rxEnd;
        this.txEnd = progress.txEnd;
        if (progress.rxTotal != null) {
            this.rxTotal = Arrays.copyOf(progress.rxTotal, progress.rxTotal.length);
        }
        this.connectOnRetry = progress.connectOnRetry;
    }

    public CoapProgress(String uriString) {
        this.uriString = uriString;
    }
}
