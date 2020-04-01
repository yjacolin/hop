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

package org.apache.hop.pipeline.steps.blockuntilstepsfinish;

import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.step.BaseStep;
import org.apache.hop.pipeline.step.BaseStepData.StepExecutionStatus;
import org.apache.hop.pipeline.step.StepDataInterface;
import org.apache.hop.pipeline.step.StepInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.step.StepMetaInterface;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block all incoming rows until defined steps finish processing rows.
 *
 * @author Samatar
 * @since 30-06-2008
 */

public class BlockUntilStepsFinish extends BaseStep implements StepInterface {
  private static Class<?> PKG = BlockUntilStepsFinishMeta.class; // for i18n purposes, needed by Translator!!

  private BlockUntilStepsFinishMeta meta;
  private BlockUntilStepsFinishData data;

  public BlockUntilStepsFinish( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr,
                                PipelineMeta pipelineMeta, Pipeline pipeline ) {
    super( stepMeta, stepDataInterface, copyNr, pipelineMeta, pipeline );
  }

  public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws HopException {
    meta = (BlockUntilStepsFinishMeta) smi;
    data = (BlockUntilStepsFinishData) sdi;

    if ( first ) {
      first = false;
      String[] stepnames = null;
      int stepnrs = 0;
      if ( meta.getStepName() != null && meta.getStepName().length > 0 ) {
        stepnames = meta.getStepName();
        stepnrs = stepnames.length;
      } else {
        throw new HopException( BaseMessages.getString( PKG, "BlockUntilStepsFinish.Error.NotSteps" ) );
      }
      // Get target stepnames
      String[] targetSteps = getPipelineMeta().getNextStepNames( getStepMeta() );

      data.stepInterfaces = new ConcurrentHashMap<Integer, StepInterface>();
      for ( int i = 0; i < stepnrs; i++ ) {
        // We can not get metrics from current step
        if ( stepnames[ i ].equals( getStepname() ) ) {
          throw new HopException( "You can not wait for step [" + stepnames[ i ] + "] to finish!" );
        }
        if ( targetSteps != null ) {
          // We can not metrics from the target steps
          for ( int j = 0; j < targetSteps.length; j++ ) {
            if ( stepnames[ i ].equals( targetSteps[ j ] ) ) {
              throw new HopException( "You can not get metrics for the target step [" + targetSteps[ j ] + "]!" );
            }
          }
        }

        int CopyNr = Const.toInt( meta.getStepCopyNr()[ i ], 0 );
        StepInterface step = getDispatcher().findBaseSteps( stepnames[ i ] ).get( CopyNr );
        if ( step == null ) {
          throw new HopException( "Erreur finding step [" + stepnames[ i ] + "] nr copy=" + CopyNr + "!" );
        }

        data.stepInterfaces.put( i, getDispatcher().findBaseSteps( stepnames[ i ] ).get( CopyNr ) );
      }
    } // end if first

    // Wait until all specified steps have finished!
    while ( data.continueLoop && !isStopped() ) {
      data.continueLoop = false;
      Iterator<Entry<Integer, StepInterface>> it = data.stepInterfaces.entrySet().iterator();
      while ( it.hasNext() ) {
        Entry<Integer, StepInterface> e = it.next();
        StepInterface step = e.getValue();
        if ( step.getStatus() != StepExecutionStatus.STATUS_FINISHED ) {
          // This step is still running...
          data.continueLoop = true;
        } else {
          // We have done with this step.
          // remove it from the map
          data.stepInterfaces.remove( e.getKey() );
          if ( log.isDetailed() ) {
            logDetailed( "Finished running step [" + step.getStepname() + "(" + step.getCopy() + ")]." );
          }
        }
      }

      if ( data.continueLoop ) {
        try {
          Thread.sleep( 200 );
        } catch ( Exception e ) {
          // ignore
        }
      }
    }

    // All steps we are waiting for are ended
    // let's now free all incoming rows
    Object[] r = getRow();

    if ( r == null ) {
      // no more input to be expected...
      setOutputDone();
      return false;
    }

    putRow( getInputRowMeta(), r );

    return true;
  }

  public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
    meta = (BlockUntilStepsFinishMeta) smi;
    data = (BlockUntilStepsFinishData) sdi;

    if ( super.init( smi, sdi ) ) {
      return true;
    }
    return false;
  }

}
