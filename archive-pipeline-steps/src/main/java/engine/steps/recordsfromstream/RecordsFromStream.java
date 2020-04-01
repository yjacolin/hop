/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2017 by Hitachi Vantara : http://www.pentaho.com
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

package org.apache.hop.pipeline.steps.recordsfromstream;

import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.step.StepDataInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.steps.rowsfromresult.RowsFromResult;

public class RecordsFromStream extends RowsFromResult {
  public RecordsFromStream( final StepMeta stepMeta,
                            final StepDataInterface stepDataInterface, final int copyNr,
                            final PipelineMeta pipelineMeta, final Pipeline pipeline ) {
    super( stepMeta, stepDataInterface, copyNr, pipelineMeta, pipeline );
  }
}
