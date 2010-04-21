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
import java.util.List;

import org.apache.directory.daemon.DaemonApplication;
import org.apache.directory.daemon.InstallationLayout;
import org.apache.directory.server.changepw.ChangePasswordServer;
import org.apache.directory.server.config.ConfigPartitionReader;
import org.apache.directory.server.config.LdifConfigExtractor;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.schema.SchemaPartition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.integration.http.HttpServer;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ntp.NtpServer;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.schema.registries.SchemaLoader;
import org.apache.directory.shared.ldap.util.ExceptionUtils;
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

    private HttpServer httpServer;

    private LdifPartition schemaLdifPartition;

    private SchemaManager schemaManager;

    private LdifPartition configPartition;

    private ConfigPartitionReader cpReader;


    public void init( InstallationLayout layout, String[] args ) throws Exception
    {
        if ( args == null )
        {
            args = new String[1];
            args[0] = System.getProperty( "java.io.tmpdir" ) + File.separator + "server-work";
        }

        File partitionsDir = new File( args[0] );
        if ( !partitionsDir.exists() )
        {
            LOG.info( "partition directory doesn't exist, creating {}", partitionsDir.getAbsolutePath() );
            partitionsDir.mkdirs();
        }

        LOG.info( "using partition dir {}", partitionsDir.getAbsolutePath() );
        initSchemaLdifPartition( partitionsDir );
        initConfigPartition( partitionsDir );

        cpReader = new ConfigPartitionReader( configPartition );

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
     * initialize the schema partition by loading the schema LDIF files
     * 
     * @throws Exception in case of any problems while extracting and writing the schema files
     */
    private void initSchemaLdifPartition( File partitionsDir ) throws Exception
    {
        // Init the LdifPartition
        schemaLdifPartition = new LdifPartition();
        schemaLdifPartition.setWorkingDirectory( partitionsDir.getPath() + "/schema" );

        // Extract the schema on disk (a brand new one) and load the registries
        File schemaRepository = new File( partitionsDir, "schema" );

        if ( schemaRepository.exists() )
        {
            LOG.info( "schema partition already exists, skipping schema extraction" );
        }
        else
        {
            SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor( partitionsDir );
            extractor.extractOrCopy();
        }

        SchemaLoader loader = new LdifSchemaLoader( schemaRepository );
        schemaManager = new DefaultSchemaManager( loader );

        // We have to load the schema now, otherwise we won't be able
        // to initialize the Partitions, as we won't be able to parse 
        // and normalize their suffix DN
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if ( errors.size() != 0 )
        {
            throw new Exception( I18n.err( I18n.ERR_317, ExceptionUtils.printErrors( errors ) ) );
        }
    }


    /**
     * 
     * initializes a LDIF partition for configuration
     * 
     * @param partitionsDir the directory where all the partitions' data is stored
     * @throws Exception in case of any issues while extracting the schema
     */
    private void initConfigPartition( File partitionsDir ) throws Exception
    {

        File configRepository = new File( partitionsDir, "config" );

        if ( configRepository.exists() )
        {
            LOG.info( "config partition already exists, skipping default config extraction" );
        }
        else
        {
            LdifConfigExtractor.extract( partitionsDir, true );
        }

        configPartition = new LdifPartition();
        configPartition.setId( "config" );
        configPartition.setSuffix( "ou=config" );
        configPartition.setSchemaManager( schemaManager );
        configPartition.setWorkingDirectory( partitionsDir.getPath() + "/config" );
        configPartition.setPartitionDir( new File( configPartition.getWorkingDirectory() ) );

        configPartition.initialize();
    }


    /**
     * Initialize the LDAP server
     */
    private void initLdap( InstallationLayout layout, String[] args ) throws Exception
    {
        LOG.info( "Starting the LDAP server" );

        printBanner( BANNER_LDAP );
        long startTime = System.currentTimeMillis();

        DirectoryService directoryService = cpReader.getDirectoryService();
        directoryService.setSchemaManager( schemaManager );

        SchemaPartition schemaPartition = directoryService.getSchemaService().getSchemaPartition();
        schemaPartition.setWrappedPartition( schemaLdifPartition );
        schemaPartition.setSchemaManager( schemaManager );

        directoryService.addPartition( configPartition );

        // this is a chicken-egg issue to have the working directory in the configuration so it should always be passed as
        // a command line arg
        directoryService.setWorkingDirectory( new File( args[0] ) );

        ldapServer = cpReader.getLdapServer();
        ldapServer.setDirectoryService( directoryService );

        directoryService.startup();

        // And start the server now
        start();
        
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
        ntpServer = cpReader.getNtpServer();
        if ( ntpServer == null )
        {
            LOG
                .info( "Cannot find any reference to the NTP Server in the configuration : the server won't be started" );
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
    //            LOG.info( "Cannot find any reference to the DNS Server in the configuration : the server won't be started" );
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

        kdcServer = cpReader.getKdcServer();
        if( kdcServer == null )
        {
            LOG
            .info( "Cannot find any reference to the Kerberos Server in the configuration : the server won't be started" );
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

        changePwdServer = cpReader.getChangePwdServer();
        if ( changePwdServer == null )
        {
            LOG
                .info( "Cannot find any reference to the Change Password Server in the configuration : the server won't be started" );
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

        httpServer = cpReader.getHttpServer();
        if ( httpServer == null )
        {
            LOG
                .info( "Cannot find any reference to the HTTP Server in the configuration : the server won't be started" );
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
