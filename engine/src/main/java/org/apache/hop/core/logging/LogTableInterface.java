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

package org.apache.hop.core.logging;

import org.apache.hop.pipeline.step.StepMeta;
import org.w3c.dom.Node;

import java.util.List;

public interface LogTableInterface extends LogTableCoreInterface {

  /**
   * @return The log table meta-data in XML format.
   */
  public String getXML();

  /**
   * Load the information for this logging table from the job XML node
   *
   * @param jobNode the node to load from
   * @param steps   the steps to reference (or null)
   */
  public void loadXML( Node jobNode, List<StepMeta> steps );

}
