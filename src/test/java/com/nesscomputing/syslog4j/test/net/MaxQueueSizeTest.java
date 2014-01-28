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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

import com.nesscomputing.syslog4j.Syslog;
import com.nesscomputing.syslog4j.SyslogBackLogHandlerIF;
import com.nesscomputing.syslog4j.SyslogConstants;
import com.nesscomputing.syslog4j.SyslogIF;
import com.nesscomputing.syslog4j.SyslogLevel;
import com.nesscomputing.syslog4j.SyslogRuntimeException;
import com.nesscomputing.syslog4j.impl.backlog.NullSyslogBackLogHandler;
import com.nesscomputing.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import com.nesscomputing.syslog4j.impl.net.tcp.TCPNetSyslogConfigIF;
import com.nesscomputing.syslog4j.util.SyslogUtility;

import org.apache.log4j.Logger;

import junit.framework.TestCase;

public class MaxQueueSizeTest extends TestCase {
    protected static final Logger LOG = Logger.getLogger("test");

    public static class BackLogCounter implements SyslogBackLogHandlerIF {
        public int count = 0;

        @Override
        public void down(SyslogIF syslog, String reason) {
            //
        }

        @Override
        public void initialize() throws SyslogRuntimeException {
            count = 0;
        }

        @Override
        public void log(SyslogIF syslog, SyslogLevel level, String message, String reason) throws SyslogRuntimeException {
            LOG.info(message + " " + reason);
            count++;
        }

        @Override
        public void up(SyslogIF syslog) {
            //
        }
    }

    public static class FakeSyslogServer implements Runnable {
        public int port = 0;
        public int catchCount = 0;

        public boolean shutdown = false;
        public int count = 0;

        public FakeSyslogServer(int port, int catchCount) {
            this.port = port;
            this.catchCount = catchCount;
        }

        public void run() {
            ServerSocket serverSocket = null;

            ServerSocketFactory factory = ServerSocketFactory.getDefault();

            try {
                serverSocket = factory.createServerSocket(this.port);

                Socket socket = serverSocket.accept();

                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                for(int i=0; i<catchCount; i++) {
                    String line = br.readLine();

                    LOG.info("Received: " + line);
                    count++;
                }

                while(!shutdown) {
                    SyslogUtility.sleep(10);
                }

                String line = br.readLine();
                while(line != null) {
                    LOG.info("Received: " + line);
                    count++;

                    line = br.readLine();
                }

                socket.close();

                serverSocket.close();

            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    public void testMaxQueueSize() {
        int catchCount = 5;
        int maxQueueSize = 5;
        int messagesToSend = 15;
        int port = 7777;

        FakeSyslogServer server = new FakeSyslogServer(port,catchCount);
        Thread thread = new Thread(server);
        thread.start();

        BackLogCounter counter = new BackLogCounter();

        TCPNetSyslogConfigIF syslogConfig = new TCPNetSyslogConfig();
        syslogConfig.setPort(port);
        assertEquals(syslogConfig.getMaxQueueSize(),SyslogConstants.MAX_QUEUE_SIZE_DEFAULT);
        syslogConfig.setMaxQueueSize(maxQueueSize);
        syslogConfig.addBackLogHandler(counter);
        syslogConfig.addBackLogHandler(NullSyslogBackLogHandler.INSTANCE);

        SyslogIF syslog = Syslog.createInstance("maxQueueSizeTest",syslogConfig);

        for(int i=1; i<=messagesToSend; i++) {
            syslog.log(SyslogLevel.INFO,"test line " + i);
        }

        SyslogUtility.sleep(500);

        server.shutdown = true;

        SyslogUtility.sleep(500);

        LOG.info("Sent Messages:       " + messagesToSend);
        LOG.info("Received Messages:   " + server.count);
        LOG.info("Backlogged Messages: " + counter.count);

        assertEquals(messagesToSend,(server.count+counter.count));
    }
}
