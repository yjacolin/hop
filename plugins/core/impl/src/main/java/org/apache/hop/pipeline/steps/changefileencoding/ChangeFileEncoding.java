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

package org.apache.hop.pipeline.steps.changefileencoding;

import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.step.BaseStep;
import org.apache.hop.pipeline.step.StepDataInterface;
import org.apache.hop.pipeline.step.StepInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.step.StepMetaInterface;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Change file encoding *
 *
 * @author Samatar
 * @since 03-Juin-2008
 */

public class ChangeFileEncoding extends BaseStep implements StepInterface {
  private static Class<?> PKG = ChangeFileEncoding.class; // for i18n purposes, needed by Translator!!

  private ChangeFileEncodingMeta meta;
  private ChangeFileEncodingData data;

  public ChangeFileEncoding( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, PipelineMeta pipelineMeta,
                             Pipeline pipeline ) {
    super( stepMeta, stepDataInterface, copyNr, pipelineMeta, pipeline );
  }

  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws HopException {
    meta = (ChangeFileEncodingMeta) smi;
    data = (ChangeFileEncodingData) sdi;

    Object[] outputRow = getRow(); // Get row from input rowset & set row busy!
    if ( outputRow == null ) {
      // no more input to be expected...
      setOutputDone();
      return false;
    }

    if ( first ) {
      first = false;
      // get the RowMeta
      data.inputRowMeta = getInputRowMeta().clone();

      // Check is source filename field is provided
      if ( Utils.isEmpty( meta.getDynamicFilenameField() ) ) {
        logError( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.FilenameFieldMissing" ) );
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.FilenameFieldMissing" ) );
      }

      // Check is target filename field is provided
      if ( Utils.isEmpty( meta.getTargetFilenameField() ) ) {
        throw new HopException(
          BaseMessages.getString( PKG, "ChangeFileEncoding.Error.TargetFilenameFieldMissing" ) );
      }

      // cache the position of the field
      data.indexOfFileename = data.inputRowMeta.indexOfValue( meta.getDynamicFilenameField() );
      if ( data.indexOfFileename < 0 ) {
        // The field is unreachable !
        logError( BaseMessages.getString( PKG, "ChangeFileEncoding.Exception.CouldnotFindField" ) + "["
          + meta.getDynamicFilenameField() + "]" );
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Exception.CouldnotFindField",
          meta.getDynamicFilenameField() ) );
      }
      // cache the position of the field
      data.indexOfTargetFileename = data.inputRowMeta.indexOfValue( meta.getTargetFilenameField() );
      if ( data.indexOfTargetFileename < 0 ) {
        // The field is unreachable !
        logError( BaseMessages.getString( PKG, "ChangeFileEncoding.Exception.CouldnotFindField" ) + "["
          + meta.getTargetFilenameField() + "]" );
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Exception.CouldnotFindField",
          meta.getTargetFilenameField() ) );
      }

      // Check source encoding
      data.sourceEncoding = environmentSubstitute( meta.getSourceEncoding() );

      // if(Utils.isEmpty(data.sourceEncoding)) {
      // throw new HopException(BaseMessages.getString(PKG, "ChangeFileEncoding.Exception.SourceEncodingEmpty"));
      // }
      // Check target encoding
      data.targetEncoding = environmentSubstitute( meta.getTargetEncoding() );

      if ( Utils.isEmpty( data.targetEncoding ) ) {
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Exception.TargetEncodingEmpty" ) );
      }

      // End If first
    }

    try {
      // get source filename
      String sourceFilename = data.inputRowMeta.getString( outputRow, data.indexOfFileename );
      if ( Utils.isEmpty( sourceFilename ) ) {
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.SourceFileIsEmpty",
          meta.getDynamicFilenameField() ) );
      }

      // get target filename
      String targetFilename = data.inputRowMeta.getString( outputRow, data.indexOfTargetFileename );
      if ( Utils.isEmpty( targetFilename ) ) {
        throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.TargetFileIsEmpty",
          meta.getTargetFilenameField() ) );
      }

      data.sourceFile = HopVFS.getFileObject( sourceFilename );

      // Check if source file exists
      if ( !data.sourceFile.exists() ) {
        throw new HopException(
          BaseMessages.getString( PKG, "ChangeFileEncoding.Error.SourceFileNotExists", sourceFilename ) );
      }

      // Check if source file is a file
      if ( data.sourceFile.getType() != FileType.FILE ) {
        throw new HopException(
          BaseMessages.getString( PKG, "ChangeFileEncoding.Error.SourceFileNotAFile", sourceFilename ) );
      }

      // create directory only if not exists
      if ( !data.sourceFile.getParent().exists() ) {
        if ( meta.isCreateParentFolder() ) {
          data.sourceFile.getParent().createFolder();
        } else {
          throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.ParentFolderNotExist",
            data.sourceFile.getParent().toString() ) );
        }
      }

      // Change file encoding
      changeEncoding( sourceFilename, targetFilename );

      putRow( data.inputRowMeta, outputRow ); // copy row to output rowset(s);

      if ( isDetailed() ) {
        logDetailed( BaseMessages.getString( PKG, "ChangeFileEncoding.LineNumber",
          getLinesRead() + " : " + getInputRowMeta().getString( outputRow ) ) );
      }
    } catch ( Exception e ) {
      boolean sendToErrorRow = false;
      String errorMessage = null;

      if ( getStepMeta().isDoingErrorHandling() ) {
        sendToErrorRow = true;
        errorMessage = e.toString();
      } else {
        logError( BaseMessages.getString( PKG, "ChangeFileEncoding.ErrorInStepRunning" ) + e.getMessage() );
        setErrors( 1 );
        stopAll();
        setOutputDone(); // signal end to receiver(s)
        return false;
      }
      if ( sendToErrorRow ) {
        // Simply add this row to the error row
        putError( getInputRowMeta(), outputRow, 1, errorMessage, meta.getDynamicFilenameField(),
          "ChangeFileEncoding001" );
      }
    }

    return true;
  }

  private void changeEncoding( String sourceFilename, String targetFilename ) throws HopException {

    BufferedWriter buffWriter = null;
    BufferedReader buffReader = null;

    try {
      buffWriter =
        new BufferedWriter(
          new OutputStreamWriter( new FileOutputStream( targetFilename, false ), data.targetEncoding ) );
      if ( Utils.isEmpty( data.sourceEncoding ) ) {
        buffReader = new BufferedReader( new InputStreamReader( new FileInputStream( sourceFilename ) ) );
      } else {
        buffReader =
          new BufferedReader( new InputStreamReader( new FileInputStream( sourceFilename ), data.sourceEncoding ) );
      }

      char[] cBuf = new char[ 8192 ];
      int readSize = 0;
      while ( ( readSize = buffReader.read( cBuf ) ) != -1 ) {
        buffWriter.write( cBuf, 0, readSize );
      }

      // add filename to result filenames?
      if ( meta.addSourceResultFilenames() ) {
        // Add this to the result file names...
        ResultFile resultFile =
          new ResultFile( ResultFile.FILE_TYPE_GENERAL, data.sourceFile, getPipelineMeta().getName(), getStepname() );
        resultFile.setComment( BaseMessages.getString( PKG, "ChangeFileEncoding.Log.FileAddedResult" ) );
        addResultFile( resultFile );

        if ( isDetailed() ) {
          logDetailed(
            BaseMessages.getString( PKG, "ChangeFileEncoding.Log.FilenameAddResult", data.sourceFile.toString() ) );
        }
      }
      // add filename to result filenames?
      if ( meta.addTargetResultFilenames() ) {
        // Add this to the result file names...
        ResultFile resultFile =
          new ResultFile( ResultFile.FILE_TYPE_GENERAL, HopVFS.getFileObject( targetFilename ),
            getPipelineMeta().getName(), getStepname() );
        resultFile.setComment( BaseMessages.getString( PKG, "ChangeFileEncoding.Log.FileAddedResult" ) );
        addResultFile( resultFile );

        if ( isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "ChangeFileEncoding.Log.FilenameAddResult", targetFilename ) );
        }
      }

    } catch ( Exception e ) {
      throw new HopException( BaseMessages.getString( PKG, "ChangeFileEncoding.Error.CreatingFile" ), e );
    } finally {
      try {
        if ( buffWriter != null ) {
          buffWriter.flush();
          buffWriter.close();
        }
        if ( buffReader != null ) {
          buffReader.close();
        }
      } catch ( Exception e ) {
        // Ignore
      }
    }

  }

  public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
    meta = (ChangeFileEncodingMeta) smi;
    data = (ChangeFileEncodingData) sdi;

    if ( super.init( smi, sdi ) ) {

      return true;
    }
    return false;
  }

  public void dispose( StepMetaInterface smi, StepDataInterface sdi ) {
    meta = (ChangeFileEncodingMeta) smi;
    data = (ChangeFileEncodingData) sdi;
    if ( data.sourceFile != null ) {
      try {
        data.sourceFile.close();
      } catch ( Exception e ) {
        // ignore
      }

    }
    super.dispose( smi, sdi );
  }
}
