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

import org.eclipse.californium.examples.dns.DnsCache;

/**
 * Arguments for {@link CoapTask}.
 */
public class CoapArguments {
    public enum RequestMode {
        ROOT, SMALL, DISCOVER, STATISTIC
    }

    public final boolean endpointsChanged;
    public final boolean ipv6;
    public final int uid;
    public final String uriString;
    public final String uniqueID;
    public final DnsCache dnsCache;
    public final RequestMode requestMode;
    public final String extendedHosts[];

    public CoapArguments(boolean endpointsChanged, boolean ipv6, int uid, String uriString, String uniqueID, DnsCache dnsCache, RequestMode requestMode, String... extendedHosts) {
        this.endpointsChanged = endpointsChanged;
        this.ipv6 = ipv6;
        this.uid = uid;
        this.uriString = uriString;
        this.uniqueID = uniqueID;
        this.dnsCache = dnsCache;
        this.requestMode = requestMode;
        this.extendedHosts = extendedHosts;
    }

    public static class Builder {
        public boolean endpointsChanged;
        public boolean ipv6;
        public int uid;
        public String uriString;
        public String uniqueID;
        public DnsCache dnsCache;
        public RequestMode requestMode;
        public String extendedHosts[];

        public void setExtendedHosts(String... extendedHosts) {
            this.extendedHosts = extendedHosts;
        }

        public CoapArguments build() {
            return new CoapArguments(endpointsChanged, ipv6, uid, uriString, uniqueID, dnsCache, requestMode, extendedHosts);
        }
    }
}
