/**
 *
 * (C) Copyright 2008-2011 syslog4j.org
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package com.nesscomputing.syslog4j.test.net;

import java.net.SocketAddress;

import com.nesscomputing.syslog4j.Syslog;
import com.nesscomputing.syslog4j.SyslogConfigIF;
import com.nesscomputing.syslog4j.SyslogIF;
import com.nesscomputing.syslog4j.SyslogRuntimeException;
import com.nesscomputing.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import com.nesscomputing.syslog4j.impl.net.udp.UDPNetSyslogConfig;
import com.nesscomputing.syslog4j.server.SyslogServer;
import com.nesscomputing.syslog4j.server.SyslogServerConfigIF;
import com.nesscomputing.syslog4j.server.SyslogServerEventIF;
import com.nesscomputing.syslog4j.server.SyslogServerIF;
import com.nesscomputing.syslog4j.server.SyslogServerSessionEventHandlerIF;
import com.nesscomputing.syslog4j.server.impl.net.tcp.TCPNetSyslogServer;
import com.nesscomputing.syslog4j.server.impl.net.tcp.TCPNetSyslogServerConfig;
import com.nesscomputing.syslog4j.server.impl.net.udp.UDPNetSyslogServerConfig;
import com.nesscomputing.syslog4j.util.SyslogUtility;

import org.apache.log4j.Logger;

import junit.framework.TestCase;

public class SyslogServerSessionTest extends TestCase {
    protected static final Logger LOG = Logger.getLogger("test");


    public static class TCPSessionHandler implements SyslogServerSessionEventHandlerIF {
        public static int currentSession = 0;
        public static final String[] SESSIONS = { "one", "two", "three", "four" };
        public String id = null;
        public int eventCount[] = new int[4];
        public int closeCount[] = new int[4];
        public boolean initialized = false;
        public boolean destroyed = false;

        public TCPSessionHandler(String id) {
            this.id = id;

            for(int i=0; i<4; i++) {
                eventCount[i] = 0;
                closeCount[i] = 0;
            }
        }

        public void initialize(SyslogServerIF syslogServer) {
            this.initialized = true;
            LOG.info("initialized " + syslogServer.getProtocol());
        }

        public Object sessionOpened(SyslogServerIF syslogServer, SocketAddress socketAddress) {
            String session = SESSIONS[currentSession++];

            LOG.info("opened: " + id + "/" + session);

            return session;
        }

        protected int translate(String word) {
            if ("one".equals(word)) {
                return 0;

            } else if ("two".equals(word)) {
                return 1;

            } else if ("three".equals(word)) {
                return 2;

            } else if ("four".equals(word)) {
                return 3;
            }

            return -1;
        }

        public void event(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, SyslogServerEventIF event) {
            if (session != null) {
                int i = translate((String) session);

                if (i != -1) {
                    eventCount[i]++;
                    LOG.info(id + " " + session + " " + i + " " + eventCount[i]);
                }
            }

            LOG.info("event: " + id + "/" + session.toString() + "/" + event.getMessage());
        }

        public void exception(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, Exception exception) {
            // This section is not (yet) tested; a bit tricky to cause a SocketException -- but not impossible
            if (session != null) {
                LOG.info("exception: " + id + "/" + session.toString() + ": " + exception);

            } else {
                LOG.info("exception: " + id + ": " + exception);
            }
        }

        public void sessionClosed(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, boolean timeout) {
            if (session != null) {
                int i = translate((String) session);

                if (i != -1) {
                    closeCount[i]++;
                }
            }

            LOG.info("closed: " + id + "/" + session.toString());
        }

        public void destroy(SyslogServerIF syslogServer) {
            this.destroyed = true;
            LOG.info("destroyed " + syslogServer.getProtocol());
        }
    }

    public class UDPSessionHandler implements SyslogServerSessionEventHandlerIF {
        public int currentSession = 0;
        public String id = null;

        public boolean okay = true;
        public boolean initialized = false;
        public boolean destroyed = false;

        public UDPSessionHandler(String id) {
            this.id = id;
        }

        public void initialize(SyslogServerIF syslogServer) {
            this.initialized = true;
            LOG.info("initialized " + syslogServer.getProtocol());
        }

        public Object sessionOpened(SyslogServerIF syslogServer, SocketAddress socketAddress) {
            okay = false;

            return null;
        }

        public void event(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, SyslogServerEventIF event) {
            if (session != null) {
                okay = false;
            }

            LOG.info("event: " + id + "/" + event.getMessage());
        }

        public void exception(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, Exception exception) {
            if (session != null) {
                okay = false;
            }

            LOG.info("exception: " + id);
        }

        public void sessionClosed(Object session, SyslogServerIF syslogServer, SocketAddress socketAddress, boolean timeout) {
            if (session != null) {
                okay = false;
            }

            LOG.info("closed: " + id);
        }

        public void destroy(SyslogServerIF syslogServer) {
            this.destroyed = true;
            LOG.info("destroyed " + syslogServer.getProtocol());
        }
    }

    public void testTCPSession() {
        SyslogServerConfigIF serverConfig = new TCPNetSyslogServerConfig(9999);

        TCPSessionHandler handler1 = new TCPSessionHandler("a");
        serverConfig.addEventHandler(handler1);

        TCPSessionHandler handler2 = new TCPSessionHandler("b");
        serverConfig.addEventHandler(handler2);

        SyslogServerIF server = SyslogServer.createThreadedInstance("tcp_session",serverConfig);

        SyslogUtility.sleep(100);

        assertTrue(handler1.initialized);
        assertTrue(handler2.initialized);

        SyslogConfigIF config = new TCPNetSyslogConfig();
        config.setPort(9999);

        TCPNetSyslogServer tcpServer = (TCPNetSyslogServer) server;

        SyslogIF syslog1 = Syslog.createInstance("tcp_session_1",config);

        syslog1.info("1");
        syslog1.info("2");

        SyslogUtility.sleep(100);

        assertEquals(1,tcpServer.getSessions().size());

        SyslogIF syslog2 = Syslog.createInstance("tcp_session_2",config);

        syslog2.info("3");
        syslog2.info("4");

        syslog1.info("5");
        syslog1.info("6");

        syslog2.info("7");
        syslog2.info("8");

        SyslogUtility.sleep(100);

        assertEquals(2,tcpServer.getSessions().size());

        syslog1.shutdown();
        syslog2.shutdown();
        SyslogServer.destroyInstance("tcp_session");

        try {
            SyslogServer.getInstance("tcp_session");
            fail();

        } catch (SyslogRuntimeException sre) {
            //
        }

        assertEquals(4,handler1.eventCount[0]);
        assertEquals(0,handler1.eventCount[1]);
        assertEquals(4,handler1.eventCount[2]);
        assertEquals(0,handler1.eventCount[3]);

        assertEquals(0,handler2.eventCount[0]);
        assertEquals(4,handler2.eventCount[1]);
        assertEquals(0,handler2.eventCount[2]);
        assertEquals(4,handler2.eventCount[3]);

        assertEquals(1,handler1.closeCount[0]);
        assertEquals(0,handler1.closeCount[1]);
        assertEquals(1,handler1.closeCount[2]);
        assertEquals(0,handler1.closeCount[3]);

        assertEquals(0,handler2.closeCount[0]);
        assertEquals(1,handler2.closeCount[1]);
        assertEquals(0,handler2.closeCount[2]);
        assertEquals(1,handler2.closeCount[3]);

        SyslogUtility.sleep(100);

        assertTrue(handler1.destroyed);
        assertTrue(handler2.destroyed);
    }

    public void testUDPSession() {
        SyslogServerConfigIF serverConfig = new UDPNetSyslogServerConfig(9999);

        UDPSessionHandler handler1 = new UDPSessionHandler("a");
        serverConfig.addEventHandler(handler1);

        UDPSessionHandler handler2 = new UDPSessionHandler("b");
        serverConfig.addEventHandler(handler2);

        SyslogServer.createThreadedInstance("udp_session",serverConfig);

        SyslogUtility.sleep(100);

        assertTrue(handler1.initialized);
        assertTrue(handler2.initialized);

        SyslogConfigIF config = new UDPNetSyslogConfig();
        config.setPort(9999);

        SyslogIF syslog1 = Syslog.createInstance("udp_session_1",config);

        syslog1.info("1");
        syslog1.info("2");

        SyslogIF syslog2 = Syslog.createInstance("udp_session_2",config);

        syslog2.info("3");
        syslog2.info("4");

        SyslogUtility.sleep(100);

        syslog1.shutdown();
        syslog2.shutdown();
        SyslogServer.destroyInstance("udp_session");

        try {
            SyslogServer.getInstance("udp_session");
            fail();

        } catch (SyslogRuntimeException sre) {
            //
        }

        assertTrue(handler1.okay);
        assertTrue(handler2.okay);

        SyslogUtility.sleep(250);

        assertTrue(handler1.destroyed);
        assertTrue(handler2.destroyed);
    }
}
