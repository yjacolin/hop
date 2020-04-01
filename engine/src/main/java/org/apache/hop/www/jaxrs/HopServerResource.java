/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2017 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.www.jaxrs;

import org.apache.hop.job.Job;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.www.HopServerObjectEntry;
import org.apache.hop.www.HopServerSingleton;
import org.apache.hop.www.SlaveServerConfig;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

@Path( "/carte" )
public class HopServerResource {

  public HopServerResource() {
  }

  public static Pipeline getPipeline( String id ) {
    return HopServerSingleton.getInstance().getPipelineMap().getPipeline( getCarteObjectEntry( id ) );
  }

  public static Job getJob( String id ) {
    return HopServerSingleton.getInstance().getJobMap().getJob( getCarteObjectEntry( id ) );
  }

  public static HopServerObjectEntry getCarteObjectEntry( String id ) {
    List<HopServerObjectEntry> pipelineList =
      HopServerSingleton.getInstance().getPipelineMap().getPipelineObjects();
    for ( HopServerObjectEntry entry : pipelineList ) {
      if ( entry.getId().equals( id ) ) {
        return entry;
      }
    }
    List<HopServerObjectEntry> jobList = HopServerSingleton.getInstance().getJobMap().getJobObjects();
    for ( HopServerObjectEntry entry : jobList ) {
      if ( entry.getId().equals( id ) ) {
        return entry;
      }
    }
    return null;
  }

  @GET
  @Path( "/systemInfo" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public ServerStatus getSystemInfo() {
    ServerStatus serverStatus = new ServerStatus();
    serverStatus.setStatusDescription( "Online" );
    return serverStatus;
  }

  @GET
  @Path( "/configDetails" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public List<NVPair> getConfigDetails() {
    SlaveServerConfig serverConfig = HopServerSingleton.getInstance().getPipelineMap().getSlaveServerConfig();
    List<NVPair> list = new ArrayList<NVPair>();
    list.add( new NVPair( "maxLogLines", "" + serverConfig.getMaxLogLines() ) );
    list.add( new NVPair( "maxLogLinesAge", "" + serverConfig.getMaxLogTimeoutMinutes() ) );
    list.add( new NVPair( "maxObjectsAge", "" + serverConfig.getObjectTimeoutMinutes() ) );
    list.add( new NVPair( "configFile", "" + serverConfig.getFilename() ) );
    return list;
  }

  @GET
  @Path( "/pipelines" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public List<HopServerObjectEntry> getPipelines() {
    List<HopServerObjectEntry> pipelineEntries = HopServerSingleton.getInstance().getPipelineMap().getPipelineObjects();
    return pipelineEntries;
  }

  @GET
  @Path( "/pipelines/detailed" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public List<PipelineStatus> getPipelineDetails() {
    List<HopServerObjectEntry> pipelineEntries =
      HopServerSingleton.getInstance().getPipelineMap().getPipelineObjects();

    List<PipelineStatus> details = new ArrayList<PipelineStatus>();

    PipelineResource pipelineRes = new PipelineResource();
    for ( HopServerObjectEntry entry : pipelineEntries ) {
      details.add( pipelineRes.getPipelineStatus( entry.getId() ) );
    }
    return details;
  }

  @GET
  @Path( "/jobs" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public List<HopServerObjectEntry> getJobs() {
    List<HopServerObjectEntry> jobEntries = HopServerSingleton.getInstance().getJobMap().getJobObjects();
    return jobEntries;
  }

  @GET
  @Path( "/jobs/detailed" )
  @Produces( { MediaType.APPLICATION_JSON } )
  public List<JobStatus> getJobsDetails() {
    List<HopServerObjectEntry> jobEntries = HopServerSingleton.getInstance().getJobMap().getJobObjects();

    List<JobStatus> details = new ArrayList<JobStatus>();

    JobResource jobRes = new JobResource();
    for ( HopServerObjectEntry entry : jobEntries ) {
      details.add( jobRes.getJobStatus( entry.getId() ) );
    }
    return details;
  }

}
