/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2019 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.pipeline.steps.fieldsplitter;

import org.apache.hop.core.Const;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.junit.rules.RestoreHopEngineEnvironment;
import org.apache.hop.pipeline.PipelineTestingUtil;
import org.apache.hop.pipeline.step.StepDataInterface;
import org.apache.hop.pipeline.steps.mock.StepMockHelper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Andrey Khayrutdinov
 */
@RunWith( PowerMockRunner.class )
public class FieldSplitter_EmptyStringVsNull_Test {
  private StepMockHelper<FieldSplitterMeta, StepDataInterface> helper;
  @ClassRule public static RestoreHopEngineEnvironment env = new RestoreHopEngineEnvironment();

  @BeforeClass
  public static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Before
  public void setUp() {
    helper = StepMockUtil.getStepMockHelper( FieldSplitterMeta.class, "FieldSplitter_EmptyStringVsNull_Test" );
  }

  @After
  public void cleanUp() {
    helper.cleanUp();
  }

  @Test
  public void emptyAndNullsAreNotDifferent() throws Exception {
    System.setProperty( Const.HOP_EMPTY_STRING_DIFFERS_FROM_NULL, "N" );
    List<Object[]> expected = Arrays.asList(
      new Object[] { "a", "", "a" },
      new Object[] { "b", null, "b" },
      new Object[] { null }
    );
    executeAndAssertResults( expected );
  }


  @Test
  public void emptyAndNullsAreDifferent() throws Exception {
    System.setProperty( Const.HOP_EMPTY_STRING_DIFFERS_FROM_NULL, "Y" );
    List<Object[]> expected = Arrays.asList(
      new Object[] { "a", "", "a" },
      new Object[] { "b", "", "b" },
      new Object[] { "", "", "" }
    );
    executeAndAssertResults( expected );
  }

  private void executeAndAssertResults( List<Object[]> expected ) throws Exception {
    FieldSplitterMeta meta = new FieldSplitterMeta();
    meta.allocate( 3 );
    meta.setFieldName( new String[] { "s1", "s2", "s3" } );
    meta.setFieldType( new int[] { ValueMetaInterface.TYPE_STRING, ValueMetaInterface.TYPE_STRING, ValueMetaInterface.TYPE_STRING } );
    meta.setSplitField( "string" );
    meta.setDelimiter( "," );

    FieldSplitterData data = new FieldSplitterData();

    FieldSplitter step = createAndInitStep( meta, data );

    RowMeta input = new RowMeta();
    input.addValueMeta( new ValueMetaString( "string" ) );
    step.setInputRowMeta( input );

    step = spy( step );
    doReturn( new String[] { "a, ,a" } )
      .doReturn( new String[] { "b,,b" } )
      .doReturn( new String[] { null } )
      .when( step ).getRow();

    List<Object[]> actual = PipelineTestingUtil.execute( step, meta, data, 3, false );
    PipelineTestingUtil.assertResult( expected, actual );
  }

  private FieldSplitter createAndInitStep( FieldSplitterMeta meta, FieldSplitterData data ) throws Exception {
    when( helper.stepMeta.getStepMetaInterface() ).thenReturn( meta );

    FieldSplitter step = new FieldSplitter( helper.stepMeta, data, 0, helper.pipelineMeta, helper.pipeline );
    step.init( meta, data );
    return step;
  }
}
