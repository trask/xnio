/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.channels.ChannelOption;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.channels.MultipointDatagramChannel;
import org.jboss.xnio.channels.UdpChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;

/**
 *
 */
public class BioMulticastChannelImpl extends BioDatagramChannelImpl implements UdpChannel {
    private final MulticastSocket multicastSocket;

    @SuppressWarnings({"unchecked"})
    BioMulticastChannelImpl(final int sendBufSize, final int recvBufSize, final Executor handlerExecutor, final IoHandler<? super UdpChannel> handler, final MulticastSocket multicastSocket, final AtomicLong globalBytesRead, final AtomicLong globalBytesWritten, final AtomicLong globalMessagesRead, final AtomicLong globalMessagesWritten) {
        super(sendBufSize, recvBufSize, handlerExecutor, (IoHandler<? super MultipointDatagramChannel<SocketAddress>>) handler, multicastSocket, globalBytesRead, globalBytesWritten, globalMessagesRead, globalMessagesWritten);
        this.multicastSocket = multicastSocket;
    }

    public Key join(final InetAddress group, final NetworkInterface iface) throws IOException {
        return new BioKey(iface, group);
    }

    public Key join(final InetAddress group, final NetworkInterface iface, final InetAddress source) throws IOException {
        throw new UnsupportedOperationException("source filtering not supported");
    }

    private static final Set<ChannelOption<?>> OPTIONS;

    static {
        final Set<ChannelOption<?>> options = new HashSet<ChannelOption<?>>(BioDatagramChannelImpl.OPTIONS);
        options.add(CommonOptions.MULTICAST_TTL);
        OPTIONS = Collections.unmodifiableSet(options);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T getOption(final ChannelOption<T> option) throws UnsupportedOptionException, IOException {
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.MULTICAST_TTL.equals(option)) {
            return (T) Integer.valueOf(multicastSocket.getTimeToLive());
        } else {
            return super.getOption(option);
        }
    }

    public Set<ChannelOption<?>> getOptions() {
        return OPTIONS;
    }

    public <T> Configurable setOption(final ChannelOption<T> option, final T value) throws IllegalArgumentException, IOException {
        if (! OPTIONS.contains(option)) {
            throw new UnsupportedOptionException("Option not supported: " + option);
        }
        if (CommonOptions.MULTICAST_TTL.equals(option)) {
            multicastSocket.setTimeToLive(((Integer)value).intValue());
            return this;
        } else {
            return super.setOption(option, value);
        }
    }

    private final class BioKey implements Key {

        private final AtomicBoolean openFlag = new AtomicBoolean(true);
        private final NetworkInterface networkInterface;
        private final InetAddress group;

        private BioKey(final NetworkInterface networkInterface, final InetAddress group) throws IOException {
            this.networkInterface = networkInterface;
            this.group = group;
            multicastSocket.joinGroup(new InetSocketAddress(group, 0), networkInterface);
        }

        public Key block(final InetAddress source) throws IOException {
            throw new UnsupportedOperationException("source filtering not supported");
        }

        public Key unblock(final InetAddress source) throws IOException {
            // no operation
            return this;
        }

        public UdpChannel getChannel() {
            return BioMulticastChannelImpl.this;
        }

        public InetAddress getGroup() {
            return group;
        }

        public NetworkInterface getNetworkInterface() {
            return networkInterface;
        }

        public InetAddress getSourceAddress() {
            return null;
        }

        public boolean isOpen() {
            return openFlag.get();
        }

        public void close() throws IOException {
            if (openFlag.getAndSet(false)) {
                multicastSocket.leaveGroup(new InetSocketAddress(group, 0), networkInterface);
            }
        }
    }
}
