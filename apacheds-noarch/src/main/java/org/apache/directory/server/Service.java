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
import org.apache.directory.server.changepw.ChangePasswordServer;
import org.apache.directory.server.configuration.ApacheDS;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.integration.http.HttpServer;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapServer;
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

    /** The LDAP server instance */
    private LdapServer ldapServer;

    /** The NTP server instance */
    private NtpServer ntpServer;

    /** The DNS server instance */
    //    private DnsServer dnsServer;

    /** The Change Password server instance */
    private ChangePasswordServer changePwdServer;

    /** The Kerberos server instance */
    private KdcServer kdcServer;

    private ApacheDS apacheDS;

    private HttpServer httpServer;

    private FileSystemXmlApplicationContext factory;


    public void init( InstallationLayout layout, String[] args ) throws Exception
    {
        // Initialize the LDAP server
        initLdap( layout, args );

        // Initialize the NTP server
        initNtp( layout, args );

        // Initialize the DNS server (Not ready yet)
        // initDns( layout, args );

        // Initialize the DHCP server (Not ready yet)
        // initDhcp( layout, args );

        // Initialize the ChangePwd server (Not ready yet)
        initChangePwd( layout, args );

        // Initialize the Kerberos server
        initKerberos( layout, args );

        // initialize the jetty http server
        initHttpServer();
    }


    /**
     * Initialize the LDAP server
     */
    private void initLdap( InstallationLayout layout, String[] args ) throws Exception
    {
        LOG.info( "Starting the LDAP server" );

        printBanner( BANNER_LDAP );
        long startTime = System.currentTimeMillis();

        if ( ( args != null ) && ( args.length > 0 ) && new File( args[0] ).exists() ) // hack that takes server.xml file argument
        {
            LOG.info( "server: loading settings from ", args[0] );
            factory = new FileSystemXmlApplicationContext( new File( args[0] ).toURI().toURL().toString() );
            ldapServer = ( LdapServer ) factory.getBean( "ldapServer" );
            apacheDS = ( ApacheDS ) factory.getBean( "apacheDS" );
        }
        else
        {
            LOG.info( "server: using default settings ..." );
            DefaultDirectoryServiceFactory.DEFAULT.init( "default" );
            DirectoryService directoryService = DefaultDirectoryServiceFactory.DEFAULT.getDirectoryService();
            directoryService.startup();
            ldapServer = new LdapServer();
            ldapServer.setDirectoryService( directoryService );
            TcpTransport tcpTransportSsl = new TcpTransport( 10636 );
            tcpTransportSsl.enableSSL( true );
            ldapServer.setTransports( new TcpTransport( 10389 ), tcpTransportSsl );
            apacheDS = new ApacheDS( ldapServer );
        }

        if ( layout != null )
        {
            ldapServer.getDirectoryService().setWorkingDirectory( layout.getPartitionsDirectory() );
        }

        // And start the server now
        apacheDS.startup();

        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "LDAP server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }
    }


    /**
     * Initialize the NTP server
     */
    private void initNtp( InstallationLayout layout, String[] args ) throws Exception
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
            LOG
                .info( "Cannot find any reference to the NTP Server in the server.xml file : the server won't be started" );
            return;
        }

        System.out.println( "Starting the NTP server" );
        LOG.info( "Starting the NTP server" );

        printBanner( BANNER_NTP );
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
    //    private void initDns( InstanceLayout layout, String[] args ) throws Exception
    //    {
    //        if ( factory == null )
    //        {
    //            return;
    //        }
    //
    //        try
    //        {
    //            dnsServer = ( DnsServer ) factory.getBean( "dnsServer" );
    //        }
    //        catch ( Exception e )
    //        {
    //            LOG.info( "Cannot find any reference to the DNS Server in the server.xml file : the server won't be started" );
    //            return;
    //        }
    //        
    //        System.out.println( "Starting the DNS server" );
    //        LOG.info( "Starting the DNS server" );
    //        
    //        printBanner( BANNER_DNS );
    //        long startTime = System.currentTimeMillis();
    //
    //        dnsServer.start();
    //        System.out.println( "DNS Server started" );
    //
    //        if ( LOG.isInfoEnabled() )
    //        {
    //            LOG.info( "DNS server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
    //        }
    //    }

    /**
     * Initialize the KERBEROS server
     */
    private void initKerberos( InstallationLayout layout, String[] args ) throws Exception
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
            LOG
                .info( "Cannot find any reference to the Kerberos Server in the server.xml file : the server won't be started" );
            return;
        }

        System.out.println( "Starting the Kerberos server" );
        LOG.info( "Starting the Kerberos server" );

        printBanner( BANNER_KERBEROS );
        long startTime = System.currentTimeMillis();

        kdcServer.start();

        System.out.println( "Kerberos server started" );

        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "Kerberos server: started in {} milliseconds", ( System.currentTimeMillis() - startTime ) + "" );
        }
    }


    /**
     * Initialize the Change Password server
     */
    private void initChangePwd( InstallationLayout layout, String[] args ) throws Exception
    {
        if ( factory == null )
        {
            return;
        }

        try
        {
            changePwdServer = ( ChangePasswordServer ) factory.getBean( "changePasswordServer" );
        }
        catch ( Exception e )
        {
            LOG
                .info( "Cannot find any reference to the Change Password Server in the server.xml file : the server won't be started" );
            return;
        }

        System.out.println( "Starting the Change Password server" );
        LOG.info( "Starting the Change Password server" );

        printBanner( BANNER_CHANGE_PWD );
        long startTime = System.currentTimeMillis();

        changePwdServer.start();

        System.out.println( "Change Password server started" );
        if ( LOG.isInfoEnabled() )
        {
            LOG.info( "Change Password server: started in {} milliseconds", ( System.currentTimeMillis() - startTime )
                + "" );
        }
    }


    private void initHttpServer() throws Exception
    {
        if ( factory == null )
        {
            return;
        }

        try
        {
            httpServer = ( HttpServer ) factory.getBean( "httpServer" );
        }
        catch ( Exception e )
        {
            LOG
                .info( "Cannot find any reference to the HTTP Server in the server.xml file : the server won't be started" );
            return;
        }

        if ( httpServer != null )
        {
            httpServer.start();
        }
    }


    public DirectoryService getDirectoryService()
    {
        return ldapServer.getDirectoryService();
    }


    public void synch() throws Exception
    {
        ldapServer.getDirectoryService().sync();
    }


    public void start()
    {
        try
        {
            ldapServer.start();
        }
        catch ( Exception e )
        {
            LOG.error( "Cannot start the server : " + e.getMessage() );
        }
    }


    public void stop( String[] args ) throws Exception
    {

        if ( factory != null )
        {
            factory.close();
        }

        // Stops the server
        ldapServer.stop();

        // We now have to stop the underlaying DirectoryService
        ldapServer.getDirectoryService().shutdown();
    }


    public void destroy()
    {
    }

    private static final String BANNER_LDAP = "           _                     _          ____  ____   \n"
        + "          / \\   _ __    ___  ___| |__   ___|  _ \\/ ___|  \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ | | \\___ \\  \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |_| |___) | \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|____/|____/  \n"
        + "               |_|                                       \n";

    private static final String BANNER_NTP = "           _                     _          _   _ _____ _ __    \n"
        + "          / \\   _ __    ___  ___| |__   ___| \\ | |_  __| '_ \\   \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ .\\| | | | | |_) |  \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |\\  | | | | .__/   \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|_| \\_| |_| |_|      \n"
        + "               |_|                                              \n";

    private static final String BANNER_KERBEROS = "           _                     _          _  __ ____   ___    \n"
        + "          / \\   _ __    ___  ___| |__   ___| |/ /|  _ \\ / __|   \n"
        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ ' / | | | / /      \n"
        + "        / ___ \\| |_) | (_| | (__| | | |  __/ . \\ | |_| \\ \\__    \n"
        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|_|\\_\\|____/ \\___|   \n"
        + "               |_|                                              \n";

    //    private static final String BANNER_DNS =
    //          "           _                     _          ____  _   _ ____    \n"
    //        + "          / \\   _ __    ___  ___| |__   ___|  _ \\| \\ | / ___|   \n"
    //        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ | | |  \\| \\__  \\   \n"
    //        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |_| | . ' |___) |  \n"
    //        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|____/|_|\\__|____/   \n"
    //        + "               |_|                                              \n";
    //
    //    
    //    private static final String BANNER_DHCP =
    //          "           _                     _          ____  _   _  ___ ____  \n"
    //        + "          / \\   _ __    ___  ___| |__   ___|  _ \\| | | |/ __|  _ \\ \n"
    //        + "         / _ \\ | '_ \\ / _` |/ __| '_ \\ / _ \\ | | | |_| / /  | |_) )\n"
    //        + "        / ___ \\| |_) | (_| | (__| | | |  __/ |_| |  _  \\ \\__|  __/ \n"
    //        + "       /_/   \\_\\ .__/ \\__,_|\\___|_| |_|\\___|____/|_| |_|\\___|_|    \n"
    //        + "               |_|                                                 \n";

    private static final String BANNER_CHANGE_PWD = "         ___                              ___ __  __ __  ______    \n"
        + "        / __|_       ___ _ __   ____  ___|  _ \\ \\ \\ / / / |  _ \\   \n"
        + "       / /  | |__  / _` | '  \\ / ___\\/ _ \\ |_) \\ \\ / /\\/ /| | | |  \n"
        + "       \\ \\__| '_  \\ (_| | |\\  | |___ | __/  __/ \\ ' /   / | |_| |  \n"
        + "        \\___|_| |_|\\__,_|_| |_|\\__. |\\___| |     \\_/ \\_/  |____/   \n"
        + "                                  |_|    |_|                       \n";


    /**
     * Print the banner for a server
     */
    public static void printBanner( String bannerConstant )
    {
        System.out.println( bannerConstant );
    }

}
