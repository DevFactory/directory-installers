/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server;


import java.io.File;

import org.apache.directory.daemon.DaemonApplication;
import org.apache.directory.daemon.InstallationLayout;
import org.apache.directory.server.configuration.ApacheDS;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapService;
import org.apache.directory.server.ntp.NtpServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.xbean.spring.context.FileSystemXmlApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * DirectoryServer bean used by both the daemon code and by the ServerMain here.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class Service implements DaemonApplication
{
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( Service.class );
    
    private Thread workerThread;
    private SynchWorker worker = new SynchWorker();
    
    /** The LDAP server instance */ 
    private ApacheDS apacheDS;
    
    /** The NTP server instance */
    private NtpServer ntpServer;
    
    /** The DNS server instance */
    private DnsServer dnsServer;
    
    /** The Kerberos server instance */
    private KdcServer kdcServer;
    
    private FileSystemXmlApplicationContext factory;


    public void init( InstallationLayout install, String[] args ) throws Exception
    {
        // Initialize the LDAP server
        initLdap( install, args );
        
        // Initialize the NTP server
        initNtp( install, args );
        
        // Initialize the DNS server (Not ready yet)
        // initDns( install, args );
        
        // Initialize the DHCP server (Not ready yet)
        //initDhcp( install, args );
        
        // Initialize the ChangePwd server (Not ready yet)
        //initChangePwd( install, args );
        
        // Initialize the Kerberos server
        initKerberos( install, args );
    }
    
    
    /**
     * Initialize the LDAP server
     */
    private void initLdap( InstallationLayout install, String[] args ) throws Exception
    {
        System.out.println( "Starting the LDAP server" );
        LOG.info( "Starting the LDAP server" );
        
        printBannerLDAP();
        long startTime = System.currentTimeMillis();

        if ( args.length > 0 && new File( args[0] ).exists() ) // hack that takes server.xml file argument
        {
            LOG.info( "server: loading settings from ", args[0] );
            factory = new FileSystemXmlApplicationContext( new File( args[0] ).toURI().toURL().toString() );
            apacheDS = ( ApacheDS ) factory.getBean( "apacheDS" );
        }
        else
        {
            LOG.info( "server: using default settings ..." );
            DirectoryService directoryService = new DefaultDirectoryService();
            directoryService.startup();
            LdapService ldapService = new LdapService();
            ldapService.setDirectoryService( directoryService );
            ldapService.setTcpTransport( new TcpTransport( 10389 ) );
            ldapService.start();
            LdapService ldapsService = new LdapService();
            ldapService.setTcpTransport( new TcpTransport( 10686 ) );
            ldapsService.setEnableLdaps( true );
            ldapsService.setDirectoryService( directoryService );
            ldapsService.start();
            apacheDS = new ApacheDS( directoryService, ldapService, ldapsService );
        }

        if ( install != null )
        {
            apacheDS.getDirectoryService().setWorkingDirectory( install.getPartitionsDirectory() );
        }

        apacheDS.startup();

        if ( apacheDS.getSynchPeriodMillis() > 0 )
        {
            workerThread = new Thread( worker, "SynchWorkerThread" );
        }
        
        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "LDAP server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }

        System.out.println( "LDAP server started" );
    }

    
    /**
     * Initialize the NTP server
     */
    private void initNtp( InstallationLayout install, String[] args ) throws Exception
    {
        if ( factory == null )
        {
            return;
        }

        try
        {
            ntpServer = ( NtpServer ) factory.getBean( "ntpServer" );
        }
        catch ( Exception e )
        {
            LOG.info( "Cannot find any reference to the NTP Server in the server.xml file : the server won't be started" );
            return;
        }
        
        System.out.println( "Starting the NTP server" );
        LOG.info( "Starting the NTP server" );
        
        printBannerNTP();
        long startTime = System.currentTimeMillis();

        ntpServer.start();
        System.out.println( "NTP Server started" );

        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "NTP server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }
    }


    /**
     * Initialize the DNS server
     */
    private void initDns( InstallationLayout install, String[] args ) throws Exception
    {
        if ( factory == null )
        {
            return;
        }

        try
        {
            dnsServer = ( DnsServer ) factory.getBean( "dnsServer" );
        }
        catch ( Exception e )
        {
            LOG.info( "Cannot find any reference to the DNS Server in the server.xml file : the server won't be started" );
            return;
        }
        
        System.out.println( "Starting the DNS server" );
        LOG.info( "Starting the DNS server" );
        
        printBannerDNS();
        long startTime = System.currentTimeMillis();

        dnsServer.start();
        System.out.println( "DNS Server started" );

        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "DNS server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }
    }


    /**
     * Initialize the KERBEROS server
     */
    private void initKerberos( InstallationLayout install, String[] args ) throws Exception
    {
        if ( factory == null )
        {
            return;
        }

        try
        {
            kdcServer = ( KdcServer ) factory.getBean( "kdcServer" );
        }
        catch ( Exception e )
        {
            LOG.info( "Cannot find any reference to the Kerberos Server in the server.xml file : the server won't be started" );
            return;
        }
        
        System.out.println( "Starting the Kerberos server" );
        LOG.info( "Starting the Kerberos server" );
        
        printBannerKERBEROS();
        long startTime = System.currentTimeMillis();

        kdcServer.start();

        System.out.println( "Kerberos server started" );
        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "Kerberos server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }
    }
    

    public DirectoryService getDirectoryService() {
        return apacheDS.getDirectoryService();
    }


    public void synch() throws Exception
    {
        apacheDS.getDirectoryService().sync();
    }


    public void start()
    {
        if ( workerThread != null )
        {
            workerThread.start();
        }
    }


    public void stop( String[] args ) throws Exception
    {
        if ( workerThread != null )
        {
            worker.stop = true;
            synchronized ( worker.lock )
            {
                worker.lock.notify();
            }
    
            while ( workerThread.isAlive() )
            {
                LOG.info( "Waiting for SynchWorkerThread to die." );
                workerThread.join( 500 );
            }
        }

        if (factory != null)
        {
            factory.close();
        }
        apacheDS.shutdown();
    }


    public void destroy()
    {
    }

    
    class SynchWorker implements Runnable
    {
        final Object lock = new Object();
        boolean stop;


        public void run()
        {
            while ( !stop )
            {
                synchronized ( lock )
                {
                    try
                    {
                        lock.wait( apacheDS.getSynchPeriodMillis() );
                    }
                    catch ( InterruptedException e )
                    {
                        LOG.warn( "SynchWorker failed to wait on lock.", e );
                    }
                }

                try
                {
                    synch();
                }
                catch ( Exception e )
                {
                    LOG.error( "SynchWorker failed to synch directory.", e );
                }
            }
        }
    }

    public static final String BANNER_LDAP = 
          "           _                     _          ____  ____   \n"
        + "          / \\   _ __    ___  ___| |__   ___|  _ \\/ ___|  \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ | | \\___ \\  \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |_| |___) | \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|____/|____/  \n"
        + "               |_|                                       \n";


    public static final String BANNER_NTP =
          "           _                     _          _   _ _____ _ __    \n"
        + "          / \\   _ __    ___  ___| |__   ___| \\ | |_  __| '_ \\   \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ .\\| | | | | |_) |  \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |\\  | | | | .__/   \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|_| \\_| |_| |_|      \n"
        + "               |_|                                              \n";


    public static final String BANNER_KERBEROS = 
          "           _                     _          _  __ ____   ___    \n"
        + "          / \\   _ __    ___  ___| |__   ___| |/ /|  _ \\ / __|   \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ ' / | | | / /      \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ . \\ | |_| \\ \\__    \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|_|\\_\\|____/ \\___|   \n"
        + "               |_|                                              \n";


    public static final String BANNER_DNS =
          "           _                     _          ____  _   _ ____    \n"
        + "          / \\   _ __    ___  ___| |__   ___|  _ \\| \\ | / ___|   \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ | | |  \\| \\__  \\   \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |_| | . ' |___) |  \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|____/|_|\\__|____/   \n"
        + "               |_|                                              \n";


    /**
     * Print the LDAP banner
     */
    public static void printBannerLDAP()
    {
        System.out.println( BANNER_LDAP );
    }


    /**
     * Print the NTP banner
     */
    public static void printBannerNTP()
    {
        System.out.println( BANNER_NTP );
    }


    /**
     * Print the Kerberos banner
     */
    public static void printBannerKERBEROS()
    {
        System.out.println( BANNER_KERBEROS );
    }


    /**
     * Print the DNS banner
     */
    public static void printBannerDNS()
    {
        System.out.println( BANNER_DNS );
    }
}
