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

package org.apache.hop.pipeline.steps.filestoresult;

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.annotations.Step;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.step.*;
import org.w3c.dom.Node;

import java.util.List;

/**
 * @author matt
 * @since 26-may-2006
 */

@Step(
        id = "FilesToResult",
        image = "ui/images/FTR.svg",
        i18nPackageName = "i18n:org.apache.hop.pipeline.steps.filestoresult",
        name = "BaseStep.TypeLongDesc.FilesToResult",
        description = "BaseStep.TypeTooltipDesc.FilesToResult",
        categoryDescription = "i18n:org.apache.hop.pipeline.step:BaseStep.Category.Job",
        documentationUrl = ""
)
public class FilesToResultMeta extends BaseStepMeta implements StepMetaInterface {
  private static Class<?> PKG = FilesToResultMeta.class; // for i18n purposes, needed by Translator!!

  private String filenameField;

  private int fileType;

  /**
   * @return Returns the fieldname that contains the filename.
   */
  public String getFilenameField() {
    return filenameField;
  }

  /**
   * @param filenameField set the fieldname that contains the filename.
   */
  public void setFilenameField( String filenameField ) {
    this.filenameField = filenameField;
  }

  /**
   * @return Returns the fileType.
   * @see ResultFile
   */
  public int getFileType() {
    return fileType;
  }

  /**
   * @param fileType The fileType to set.
   * @see ResultFile
   */
  public void setFileType( int fileType ) {
    this.fileType = fileType;
  }

  public FilesToResultMeta() {
    super(); // allocate BaseStepMeta
  }

  public void loadXML( Node stepnode, IMetaStore metaStore ) throws HopXMLException {
    readData( stepnode );
  }

  public Object clone() {
    Object retval = super.clone();
    return retval;
  }

  public String getXML() {
    StringBuilder xml = new StringBuilder();

    xml.append( XMLHandler.addTagValue( "filename_field", filenameField ) );
    xml.append( XMLHandler.addTagValue( "file_type", ResultFile.getTypeCode( fileType ) ) );

    return xml.toString();
  }

  private void readData( Node stepnode ) {
    filenameField = XMLHandler.getTagValue( stepnode, "filename_field" );
    fileType = ResultFile.getType( XMLHandler.getTagValue( stepnode, "file_type" ) );
  }

  public void setDefault() {
    filenameField = null;
    fileType = ResultFile.FILE_TYPE_GENERAL;
  }

  public void getFields( RowMetaInterface rowMeta, String origin, RowMetaInterface[] info, StepMeta nextStep,
                         VariableSpace space, IMetaStore metaStore ) throws HopStepException {
    // Default: nothing changes to rowMeta
  }

  public void check( List<CheckResultInterface> remarks, PipelineMeta pipelineMeta, StepMeta stepMeta,
                     RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info, VariableSpace space,
                     IMetaStore metaStore ) {
    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      CheckResult cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages.getString(
          PKG, "FilesToResultMeta.CheckResult.StepReceivingInfoFromOtherSteps" ), stepMeta );
      remarks.add( cr );
    } else {
      CheckResult cr =
        new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString(
          PKG, "FilesToResultMeta.CheckResult.NoInputReceivedError" ), stepMeta );
      remarks.add( cr );
    }
  }

  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, PipelineMeta tr,
                                Pipeline pipeline ) {
    return new FilesToResult( stepMeta, stepDataInterface, cnr, tr, pipeline );
  }

  public StepDataInterface getStepData() {
    return new FilesToResultData();
  }

  @Override
  public String getDialogClassName(){
    return FilesToResultDialog.class.getName();
  }
}
