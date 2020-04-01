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

package org.apache.hop.pipeline.steps.filterrows;

import org.apache.hop.core.RowSet;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.pipeline.step.BaseStepData;
import org.apache.hop.pipeline.step.StepDataInterface;

/**
 * @author Matt
 * @since 24-jan-2005
 */
public class FilterRowsData extends BaseStepData implements StepDataInterface {

  public RowMetaInterface outputRowMeta;
  public RowSet trueRowSet;
  public RowSet falseRowSet;
  public boolean chosesTargetSteps;
  public String trueStepname;
  public String falseStepname;

  public FilterRowsData() {
    super();
  }

}
