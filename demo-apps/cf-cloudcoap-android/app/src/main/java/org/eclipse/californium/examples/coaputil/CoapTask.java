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

import android.net.TrafficStats;
import android.os.AsyncTask;
import android.util.Log;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.elements.DtlsEndpointContext;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.MapBasedEndpointContext;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.californium.examples.R;
import org.eclipse.californium.examples.dns.DnsEntry;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;

/**
 * CoAP GET task
 */
public class CoapTask extends AsyncTask<CoapArguments, CoapProgress, CoapTaskResult> {
    protected static final String LOG_TAG = "coap";

    private static final String ROOT_RESOURCE_NAME = "";
    private static final String DISCOVER_RESOURCE_NAME = ".well-known/core";
    private static final String SMALL_RESOURCE_NAME = "multi-format";
    private static final String SMALL_RESOURCE_NAME_LH = "hello";
    private static final String STATISTIC_RESOURCE_NAME = "requests";

    protected volatile CoapArguments arguments;
    private volatile CoapProgress progress;
    private volatile Long connectTime;
    private AtomicBoolean connectOnRetry = new AtomicBoolean();
    private AtomicInteger dtlsRetransmissions = new AtomicInteger();
    private AtomicInteger retransmissions = new AtomicInteger();
    private AtomicInteger blocks = new AtomicInteger();

    private InetAddress[] nsLookup(String uriHost) throws UnknownHostException {
        progress = new CoapProgress(progress);
        progress.state = R.string.state_resolving;
        publishProgress(progress);
        long dnsTime = System.nanoTime();
        InetAddress addresses[] = InetAddress.getAllByName(uriHost);
        for (InetAddress address : addresses) {
            Log.i(LOG_TAG, "   " + address.getHostAddress());
        }
        dnsTime = System.nanoTime() - dnsTime;
        progress = new CoapProgress(progress);
        progress.state = R.string.state_resolved;
        progress.hostAddresses = addresses;
        progress.dnsResolveNanoTime = dnsTime;
        progress.storeDns = true;
        publishProgress(progress);
        Log.i(LOG_TAG, "Resolved: " + uriHost + " to " + addresses[0].getHostAddress());
        return addresses;
    }

    private InetAddress select(boolean ipv6, InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (ipv6) {
                if (address instanceof Inet6Address) {
                    Log.i(LOG_TAG, " IPv6 => " + address.getHostAddress());
                    return address;
                }
            } else {
                if (address instanceof Inet4Address) {
                    Log.i(LOG_TAG, " IPv4 => " + address.getHostAddress());
                    return address;
                }
            }
            Log.i(LOG_TAG, "    " + address.getHostAddress());
        }
        return null;
    }

    @Override
    protected CoapTaskResult doInBackground(CoapArguments... args) {
        arguments = args[0];
        progress = new CoapProgress(arguments.uriString);
        try {
            URI uri = new URI(arguments.uriString);
            String uriHost = uri.getHost();
            if (uriHost == null) {
                return new CoapTaskResult(progress, new UnknownHostException("missing hostname in URI"), null);
            }
            progress.host = uriHost;

            DnsEntry dnsEntry = null;
            if (arguments.dnsCache != null) {
                dnsEntry = arguments.dnsCache.getAddress(uriHost, null);
            }
            boolean lookup = true;
            InetAddress addresses[] = null;
            if (dnsEntry == null || dnsEntry.expired) {
                try {
                    lookup = false;
                    addresses = nsLookup(uriHost);
                } catch (UnknownHostException e) {
                    Log.e(LOG_TAG, "URI: " + uriHost, e);
                    if (dnsEntry == null) {
                        return new CoapTaskResult(progress, e, null);
                    }
                    addresses = dnsEntry.addresses;
                    Log.i(LOG_TAG, "Cached: " + uriHost + " to " + addresses[0].getHostAddress() + " (expired)");
                }
            } else {
                addresses = dnsEntry.addresses;
                Log.i(LOG_TAG, "Cached: " + uriHost + " to " + addresses[0].getHostAddress());
            }
            boolean ipv6 = arguments.ipv6;
            InetAddress hostAddress = select(ipv6, addresses);
            if (hostAddress == null && lookup) {
                // lookup, if ip family is not available
                try {
                    addresses = nsLookup(uriHost);
                    hostAddress = select(ipv6, addresses);
                } catch (UnknownHostException e) {
                    Log.e(LOG_TAG, "URI: " + uriHost, e);
                    if (dnsEntry == null) {
                        return new CoapTaskResult(progress, e, null);
                    }
                }
            }
            if (hostAddress == null) {
                hostAddress = addresses[0];
                Log.i(LOG_TAG, " => " + hostAddress.getHostAddress());
            }

            String rid = null;
            String scheme = uri.getScheme();
            String uriQuery = uri.getQuery();
            String resource = uri.getPath();
            int port = uri.getPort();
            CoapArguments.RequestMode mode = CoapArguments.RequestMode.ROOT;
            Request request = Request.newGet();
            if (resource.isEmpty()) {
                if (!hostAddress.isLoopbackAddress() || arguments.requestMode != CoapArguments.RequestMode.STATISTIC) {
                    mode = arguments.requestMode;
                }
                switch (mode) {
                    case DISCOVER:
                        resource = DISCOVER_RESOURCE_NAME;
                        break;
                    case SMALL:
                        if (hostAddress.isLoopbackAddress()) {
                            resource += SMALL_RESOURCE_NAME_LH;
                        } else {
                            resource += SMALL_RESOURCE_NAME;
                        }
                        break;
                    case STATISTIC:
                        if (port < 0) {
                            for (String extHost : arguments.extendedHosts) {
                                if (uriHost.equals(extHost)) {
                                    port = CoAP.getDefaultPort(scheme) + 100;
                                    break;
                                }
                            }
                        }
                        resource += STATISTIC_RESOURCE_NAME;
                        rid = ReceivetestClient.createRid();
                        uriQuery = "dev=" + arguments.uniqueID + "&rid=" + rid;
                        request = Request.newPost();
                        request.getOptions().setAccept(APPLICATION_JSON);
                        break;
                    case ROOT:
                    default:
                        resource += ROOT_RESOURCE_NAME;
                        break;
                }
            }
            if (!resource.isEmpty() && !resource.startsWith("/")) {
                resource = "/" + resource;
            }
            Endpoint endpoint = EndpointManager.getEndpointManager().getDefaultEndpoint(scheme);
            final int maxRetransmissions = endpoint == null ? 0 : endpoint.getConfig().getInt(NetworkConfig.Keys.MAX_RETRANSMIT, 0);
            final boolean dtls = CoAP.isSecureScheme(scheme) && !CoAP.isTcpScheme(scheme);
            uri = new URI(scheme, uri.getUserInfo(), hostAddress.getHostAddress(), port, resource, uriQuery, uri.getFragment());

            final Request observed = request;
            request.addMessageObserver(new MessageObserverAdapter() {

                private Long startConnect;
                private boolean sent;

                @Override
                public void onConnecting() {
                    startConnect = System.nanoTime();
                    progress = new CoapProgress(progress);
                    progress.state = R.string.state_connecting;
                    progress.connectOnRetry = connectOnRetry.get();
                    publishProgress(progress);
                }

                @Override
                public void onDtlsRetransmission(int flight) {
                    progress = new CoapProgress(progress);
                    progress.state = R.string.state_connecting;
                    progress.dtlsRetries = dtlsRetransmissions.incrementAndGet();
                    progress.retries = progress.dtlsRetries;
                    publishProgress(progress);
                }

                @Override
                public void onRetransmission() {
                    boolean rxOk = true;
                    blocks.getAndDecrement();
                    progress = new CoapProgress(progress);
                    progress.state = R.string.state_loading;
                    progress.coapRetries = retransmissions.incrementAndGet();
                    progress.retries = progress.coapRetries;
                    if (progress.rxTotal != null) {
                        int rxTotalIndex = progress.rxTotal.length;
                        long rxTotal = TrafficStats.getTotalRxBytes();
                        progress.rxTotal = Arrays.copyOf(progress.rxTotal, rxTotalIndex + 1);
                        progress.rxTotal[rxTotalIndex] = rxTotal;
                        if (rxTotalIndex > 0) {
                            --rxTotalIndex;
                            if (rxTotalIndex > 0) {
                                --rxTotalIndex;
                            }
                            rxOk = progress.rxTotal[rxTotalIndex] != rxTotal;
                        }
                    }
                    publishProgress(progress);
                    if (dtls && startConnect == null && (0 < maxRetransmissions && maxRetransmissions - 1 <= progress.coapRetries)) {
                        if (rxOk || (0 < maxRetransmissions && maxRetransmissions <= progress.coapRetries)) {
                            Log.i(LOG_TAG, "reconnect ...");
                            EndpointContext destinationContext =
                                    MapBasedEndpointContext.addEntries(
                                            observed.getDestinationContext(), DtlsEndpointContext.KEY_RESUMPTION_TIMEOUT, "0");
                            observed.setDestinationContext(destinationContext);
                            connectOnRetry.set(true);
                            sent = false;
                        }
                    }
                }

                @Override
                public void onSent() {
                    progress = new CoapProgress(progress);
                    progress.blocks = blocks.incrementAndGet();
                    progress.state = R.string.state_loading;
                    if (!sent) {
                        sent = true;
                        calculateConnectTime();
                        progress.connectTime = connectTime;
                    }
                    publishProgress(progress);
                }

                @Override
                protected void failed() {
                    calculateConnectTime();
                }

                private void calculateConnectTime() {
                    if (startConnect != null && connectTime == null) {
                        connectTime = System.nanoTime() - startConnect;
                    }
                }
            });
            progress = new CoapProgress(progress);
            progress.state = R.string.state_start;
            progress.uri = uri;
            progress.uriString = uri.toASCIIString();
            publishProgress(progress);

            Log.i(LOG_TAG, "=>> " + arguments.uniqueID + " <<==");
            request.setURI(uri);

            CoapClient client = new CoapClient();
            if (CoAP.isTcpScheme(scheme)) {
                client.setTimeout(1000L * 30); // 30s
            }
            try {
                progress = new CoapProgress(progress);
                progress.rxStart = TrafficStats.getUidRxBytes(arguments.uid);
                progress.txStart = TrafficStats.getUidTxBytes(arguments.uid);
                long rxTotal = TrafficStats.getTotalRxBytes();
                if (rxTotal != TrafficStats.UNSUPPORTED) {
                    progress.rxTotal = new long[]{TrafficStats.getTotalRxBytes()};
                }
                CoapResponse response;
                try {
                    response = client.advanced(request);
                } finally {
                    progress = new CoapProgress(progress);
                    progress.rxEnd = TrafficStats.getUidRxBytes(arguments.uid);
                    progress.txEnd = TrafficStats.getUidTxBytes(arguments.uid);
                    long rx = progress.rxEnd - progress.rxStart;
                    long tx = progress.txEnd - progress.txStart;
                    Log.i(LOG_TAG, uri.getScheme() + " tx: " + tx + ", rx: " + rx);
                }
                if (response != null && response.getOptions().getContentFormat() == MediaTypeRegistry.APPLICATION_LINK_FORMAT) {
                    Set<WebLink> webLinks = LinkFormat.parse(response.getResponseText());
                    return new CoapTaskResult(progress, response, webLinks, rid);
                } else {
                    return new CoapTaskResult(progress, response, rid);
                }
            } catch (ConnectorException e) {
                return new CoapTaskResult(progress, e, rid);
            } catch (IOException e) {
                return new CoapTaskResult(progress, e, rid);
            } catch (RuntimeException e) {
                return new CoapTaskResult(progress, e, rid);
            }

        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "URI:", e);
            return new CoapTaskResult(progress, e, null);
        } catch (NullPointerException e) {
            Log.e(LOG_TAG, "URI:", e);
            return new CoapTaskResult(progress, e, null);
        } catch (URISyntaxException e) {
            Log.e(LOG_TAG, "URI:", e);
            return new CoapTaskResult(progress, e, null);
        }
    }

    public static String formatDuration(long timeMillis) {
        if (timeMillis < 10000) {
            return timeMillis + " [ms]";
        } else if (timeMillis < 1000 * 300) {
            long millis = timeMillis % 1000;
            if (millis == 0) {
                return (timeMillis / 1000) + " [s]";
            } else {
                return String.format("%d,%03d [s]", (timeMillis / 1000), millis);
            }
        } else {
            long timeS = timeMillis / 1000;
            long seconds = timeS % 60;
            if (seconds == 0) {
                return (timeS / 60) + " [min]";
            } else {
                return String.format("%d:%02d [min:s]", (timeS / 60), seconds);
            }
        }
    }
}
