/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.www;

import org.apache.hop.core.Result;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.junit.rules.RestoreHopEngineEnvironment;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.steps.loadsave.validator.FieldLoadSaveValidator;
import org.apache.hop.www.SlaveServerJobStatusTest.LoggingStringLoadSaveValidator;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlaveServerPipelineStatusTest {
  @ClassRule public static RestoreHopEngineEnvironment env = new RestoreHopEngineEnvironment();

  @Test
  public void testStaticFinal() {
    assertEquals( "pipeline-status", SlaveServerPipelineStatus.XML_TAG );
  }

  @Test
  public void testNoDate() throws HopException {
    String pipelineName = "testNullDate";
    String id = UUID.randomUUID().toString();
    String status = Pipeline.STRING_FINISHED;
    SlaveServerPipelineStatus ts = new SlaveServerPipelineStatus( pipelineName, id, status );
    String resultXML = ts.getXML();
    Node newPipelineStatus = XMLHandler.getSubNode( XMLHandler.loadXMLString( resultXML ), SlaveServerPipelineStatus.XML_TAG );

    assertEquals( "The XML document should match after rebuilding from XML", resultXML,
      SlaveServerPipelineStatus.fromXML( resultXML ).getXML() );
    assertEquals( "There should be one \"log_date\" node in the XML", 1,
      XMLHandler.countNodes( newPipelineStatus, "log_date" ) );
    assertTrue( "The \"log_date\" node should have a null value",
      Utils.isEmpty( XMLHandler.getTagValue( newPipelineStatus, "log_date" ) ) );
  }

  @Test
  public void testWithDate() throws HopException {
    String pipelineName = "testWithDate";
    String id = UUID.randomUUID().toString();
    String status = Pipeline.STRING_FINISHED;
    Date logDate = new Date();
    SlaveServerPipelineStatus ts = new SlaveServerPipelineStatus( pipelineName, id, status );
    ts.setLogDate( logDate );
    String resultXML = ts.getXML();
    Node newPipelineStatus = XMLHandler.getSubNode( XMLHandler.loadXMLString( resultXML ), SlaveServerPipelineStatus.XML_TAG );

    assertEquals( "The XML document should match after rebuilding from XML", resultXML,
      SlaveServerPipelineStatus.fromXML( resultXML ).getXML() );
    assertEquals( "There should be one \"log_date\" node in the XML", 1,
      XMLHandler.countNodes( newPipelineStatus, "log_date" ) );
    assertEquals( "The \"log_date\" node should match the original value", XMLHandler.date2string( logDate ),
      XMLHandler.getTagValue( newPipelineStatus, "log_date" ) );
  }

  @Test
  public void testSerialization() throws HopException {
    // TODO Add StepStatusList
    List<String> attributes = Arrays.asList( "PipelineName", "Id", "StatusDescription", "ErrorDescription",
      "LogDate", "Paused", "FirstLoggingLineNr", "LastLoggingLineNr", "LoggingString" );
    Map<String, FieldLoadSaveValidator<?>> attributeMap = new HashMap<String, FieldLoadSaveValidator<?>>();
    attributeMap.put( "LoggingString", new LoggingStringLoadSaveValidator() );

    SlaveServerPipelineStatusLoadSaveTester tester = new SlaveServerPipelineStatusLoadSaveTester( SlaveServerPipelineStatus.class, attributes, attributeMap );

    tester.testSerialization();
  }

  @Test
  public void testGetXML() throws HopException {
    SlaveServerPipelineStatus pipelineStatus = new SlaveServerPipelineStatus();
    RowMetaAndData rowMetaAndData = new RowMetaAndData();
    String testData = "testData";
    rowMetaAndData.addValue( new ValueMetaString(), testData );
    List<RowMetaAndData> rows = new ArrayList<>();
    rows.add( rowMetaAndData );
    Result result = new Result();
    result.setRows( rows );
    pipelineStatus.setResult( result );
    //PDI-15781
    Assert.assertFalse( pipelineStatus.getXML().contains( testData ) );
    //PDI-17061
    Assert.assertTrue( pipelineStatus.getXML( true ).contains( testData ) );
  }
}
