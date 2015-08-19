/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.resolver.dns;

import io.netty.util.internal.ThreadLocalRandom;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Provides an infinite sequence of DNS server addresses to {@link DnsNameResolver}.
 */
@SuppressWarnings("IteratorNextCanNotThrowNoSuchElementException")
public abstract class DnsServerAddresses {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DnsServerAddresses.class);

    private static final List<InetSocketAddress> DEFAULT_NAME_SERVER_LIST;
    private static final InetSocketAddress[] DEFAULT_NAME_SERVER_ARRAY;
    private static final DnsServerAddresses DEFAULT_NAME_SERVERS;

    static {
        final int DNS_PORT = 53;
        final List<InetSocketAddress> defaultNameServers = new ArrayList<InetSocketAddress>(2);
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);

            @SuppressWarnings("unchecked")
            final List<String> list = (List<String>) nameservers.invoke(instance);
            final int size = list.size();
            for (int i = 0; i < size; i ++) {
                String dnsAddr = list.get(i);
                if (dnsAddr != null) {
                    defaultNameServers.add(new InetSocketAddress(InetAddress.getByName(dnsAddr), DNS_PORT));
                }
            }
        } catch (Exception ignore) {
            // Failed to get the system name server list.
            // Will add the default name servers afterwards.
        }

        if (!defaultNameServers.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Default DNS servers: {} (sun.net.dns.ResolverConfiguration)", defaultNameServers);
            }
        } else {
            Collections.addAll(
                    defaultNameServers,
                    new InetSocketAddress("8.8.8.8", DNS_PORT),
                    new InetSocketAddress("8.8.4.4", DNS_PORT));

            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Default DNS servers: {} (Google Public DNS as a fallback)", defaultNameServers);
            }
        }

        DEFAULT_NAME_SERVER_LIST = Collections.unmodifiableList(defaultNameServers);
        DEFAULT_NAME_SERVER_ARRAY = defaultNameServers.toArray(new InetSocketAddress[defaultNameServers.size()]);
        DEFAULT_NAME_SERVERS = sequential(DEFAULT_NAME_SERVER_ARRAY);
    }

    /**
     * Returns the list of the system DNS server addresses.  If it failed to retrieve the list of the system DNS server
     * addresses from the environment, it will return {@code "8.8.8.8"} and {@code "8.8.4.4"}, the addresses of the
     * Google public DNS servers.  Note that the {@code Iterator} of the returned list is not infinite unlike other
     * factory methods in this class.  To make the returned list infinite, pass it to the other factory method. e.g.
     * <pre>
     * addresses = {@link #sequential(Iterable) sequential}({@link #defaultAddresses()});
     * </pre>
     */
    public static List<InetSocketAddress> defaultAddressList() {
        return DEFAULT_NAME_SERVER_LIST;
    }

    public static DnsServerAddresses defaultAddresses() {
        return DEFAULT_NAME_SERVERS;
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a sequential order.
     */
    public static DnsServerAddresses sequential(Iterable<? extends InetSocketAddress> addresses) {
        return sequential0(sanitize(addresses));
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a sequential order.
     */
    public static DnsServerAddresses sequential(InetSocketAddress... addresses) {
        return sequential0(sanitize(addresses));
    }

    private static DnsServerAddresses sequential0(final InetSocketAddress... addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new DefaultDnsServerAddresses("sequential", addresses) {
            @Override
            public DnsServerAddressStream stream() {
                return new SequentialDnsServerAddressStream(addresses, 0);
            }
        };
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a shuffled order.
     */
    public static DnsServerAddresses shuffled(Iterable<? extends InetSocketAddress> addresses) {
        return shuffled0(sanitize(addresses));
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a shuffled order.
     */
    public static DnsServerAddresses shuffled(InetSocketAddress... addresses) {
        return shuffled0(sanitize(addresses));
    }

    private static DnsServerAddresses shuffled0(final InetSocketAddress[] addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new DefaultDnsServerAddresses("shuffled", addresses) {
            @Override
            public DnsServerAddressStream stream() {
                return new ShuffledDnsServerAddressStream(addresses);
            }
        };
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a rotational order.  It is similar to {@link #sequential(Iterable)}, but each
     * {@link Iterator} starts from a different starting point.  For example, the first {@link Iterable#iterator()}
     * will iterate from the first DNS server address, the second one will iterate from the second DNS server address,
     * and so on.
     */
    public static DnsServerAddresses rotational(Iterable<? extends InetSocketAddress> addresses) {
        return rotational0(sanitize(addresses));
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server addresses, whose {@link Iterator} iterates
     * the DNS server addresses in a rotational order.  It is similar to {@link #sequential(Iterable)}, but each
     * {@link Iterator} starts from a different starting point.  For example, the first {@link Iterable#iterator()}
     * will iterate from the first DNS server address, the second one will iterate from the second DNS server address,
     * and so on.
     */
    public static DnsServerAddresses rotational(InetSocketAddress... addresses) {
        return rotational0(sanitize(addresses));
    }

    private static DnsServerAddresses rotational0(final InetSocketAddress[] addresses) {
        if (addresses.length == 1) {
            return singleton(addresses[0]);
        }

        return new RotationalDnsServerAddresses(addresses);
    }

    /**
     * Returns an infinite {@link Iterable} of the specified DNS server address, whose {@link Iterator} always
     * return the same DNS server address.
     */
    public static DnsServerAddresses singleton(final InetSocketAddress address) {
        if (address == null) {
            throw new NullPointerException("address");
        }
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + address);
        }

        return new SingletonDnsServerAddresses(address);
    }

    private static InetSocketAddress[] sanitize(Iterable<? extends InetSocketAddress> addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        final List<InetSocketAddress> list;
        if (addresses instanceof Collection) {
            list = new ArrayList<InetSocketAddress>(((Collection<?>) addresses).size());
        } else {
            list = new ArrayList<InetSocketAddress>(4);
        }

        for (InetSocketAddress a : addresses) {
            if (a == null) {
                break;
            }
            if (a.isUnresolved()) {
                throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + a);
            }
            list.add(a);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("empty addresses");
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    private static InetSocketAddress[] sanitize(InetSocketAddress[] addresses) {
        if (addresses == null) {
            throw new NullPointerException("addresses");
        }

        List<InetSocketAddress> list = new ArrayList<InetSocketAddress>(addresses.length);
        for (InetSocketAddress a: addresses) {
            if (a == null) {
                break;
            }
            if (a.isUnresolved()) {
                throw new IllegalArgumentException("cannot use an unresolved DNS server address: " + a);
            }
            list.add(a);
        }

        if (list.isEmpty()) {
            return DEFAULT_NAME_SERVER_ARRAY;
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    protected DnsServerAddresses() { }

    public abstract DnsServerAddressStream stream();

    static final class SequentialDnsServerAddressStream implements DnsServerAddressStream {

        private final InetSocketAddress[] addresses;
        private int i;

        SequentialDnsServerAddressStream(InetSocketAddress[] addresses, int startIdx) {
            this.addresses = addresses;
            i = startIdx;
        }

        @Override
        public InetSocketAddress next() {
            int i = this.i;
            InetSocketAddress next = addresses[i];
            if (++ i < addresses.length) {
                this.i = i;
            } else {
                this.i = 0;
            }
            return next;
        }
    }

    static final class ShuffledDnsServerAddressStream implements DnsServerAddressStream {

        private final InetSocketAddress[] addresses;
        private int i;

        ShuffledDnsServerAddressStream(InetSocketAddress[] addresses) {
            this.addresses = addresses.clone();

            shuffle();
        }

        private void shuffle() {
            final InetSocketAddress[] addresses = this.addresses;
            final Random r = ThreadLocalRandom.current();

            for (int i = addresses.length - 1; i >= 0; i --) {
                InetSocketAddress tmp = addresses[i];
                int j = r.nextInt(i + 1);
                addresses[i] = addresses[j];
                addresses[j] = tmp;
            }
        }

        @Override
        public InetSocketAddress next() {
            int i = this.i;
            InetSocketAddress next = addresses[i];
            if (++ i < addresses.length) {
                this.i = i;
            } else {
                this.i = 0;
                shuffle();
            }
            return next;
        }
    }
}
