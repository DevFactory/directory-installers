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

import org.apache.directory.daemon.InstallationLayout;


/**
 * The command line main for the server.  Warning this used to be a simple test
 * case so there really is not much here.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class UberjarMain
{
    /**
     * Takes a single argument, the path to the installation home, which contains 
     * the configuration to load with server startup settings.
     *
     * @param args the arguments
     */
    public static void main( String[] args ) throws Exception
    {
        Service service = new Service();

        if ( args.length > 0 )
        {
            InstallationLayout layout = new InstallationLayout( args[0] );
            
            // assign the given directory path first
            String partitionDir = args[0];
            try
            {
                // check the validity of the installationlayout, if correct set it's partition dir to partitionDir
                layout.verifyInstallation();
                partitionDir = layout.getPartitionsDirectory().getAbsolutePath();
            }
            catch( Exception e )
            {
                // nothing to do
            }
            
            service.init( layout, new String[]{ partitionDir } );
        }
        else
        {
            service.init( null, null );
        }
    }
}
