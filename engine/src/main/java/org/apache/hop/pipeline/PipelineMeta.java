//CHECKSTYLE:FileLength:OFF
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

package org.apache.hop.pipeline;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.Counter;
import org.apache.hop.core.DBCache;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.ProgressMonitorListener;
import org.apache.hop.core.Props;
import org.apache.hop.core.Result;
import org.apache.hop.core.SQLStatement;
import org.apache.hop.core.attributes.AttributesUtil;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopDatabaseException;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.exception.HopMissingPluginsException;
import org.apache.hop.core.exception.HopRowException;
import org.apache.hop.core.exception.HopStepException;
import org.apache.hop.core.exception.HopXMLException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ChannelLogTable;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.logging.LogStatus;
import org.apache.hop.core.logging.LogTableInterface;
import org.apache.hop.core.logging.LoggingObjectInterface;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.MetricsLogTable;
import org.apache.hop.core.logging.PerformanceLogTable;
import org.apache.hop.core.logging.PipelineLogTable;
import org.apache.hop.core.logging.StepLogTable;
import org.apache.hop.core.parameters.NamedParamsDefault;
import org.apache.hop.core.reflection.StringSearchResult;
import org.apache.hop.core.reflection.StringSearcher;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.row.ValueMetaInterface;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.VariableSpace;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.core.xml.XMLFormatter;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.core.xml.XMLInterface;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metastore.api.IMetaStore;
import org.apache.hop.partition.PartitionSchema;
import org.apache.hop.pipeline.steps.missing.Missing;
import org.apache.hop.resource.ResourceDefinition;
import org.apache.hop.resource.ResourceExportInterface;
import org.apache.hop.resource.ResourceNamingInterface;
import org.apache.hop.resource.ResourceReference;
import org.apache.hop.pipeline.step.BaseStep;
import org.apache.hop.pipeline.step.StepErrorMeta;
import org.apache.hop.pipeline.step.StepIOMetaInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.step.StepMetaChangeListenerInterface;
import org.apache.hop.pipeline.step.StepMetaInterface;
import org.apache.hop.pipeline.step.StepPartitioningMeta;
import org.apache.hop.pipeline.step.errorhandling.StreamInterface;
import org.apache.hop.pipeline.steps.jobexecutor.JobExecutorMeta;
import org.apache.hop.pipeline.steps.mapping.MappingMeta;
import org.apache.hop.pipeline.steps.singlethreader.SingleThreaderMeta;
import org.apache.hop.pipeline.steps.pipelineexecutor.PipelineExecutorMeta;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class defines information about a pipeline and offers methods to save and load it from XML as
 * well as methods to alter a pipeline by adding/removing databases, steps, hops, etc.
 *
 * @author Matt Casters
 * @since 20-jun-2003
 */
public class PipelineMeta extends AbstractMeta
  implements XMLInterface, Comparator<PipelineMeta>, Comparable<PipelineMeta>, Cloneable, ResourceExportInterface,
  LoggingObjectInterface, IHasFilename {

  /**
   * The package name, used for internationalization of messages.
   */
  private static Class<?> PKG = Pipeline.class; // for i18n purposes, needed by Translator!!

  /**
   * A constant specifying the tag value for the XML node of the pipeline.
   */
  public static final String XML_TAG = "pipeline";


  public static final int BORDER_INDENT = 20;
  /**
   * The list of steps associated with the pipeline.
   */
  protected List<StepMeta> steps;

  /**
   * The list of hops associated with the pipeline.
   */
  protected List<PipelineHopMeta> hops;

  /**
   * The list of dependencies associated with the pipeline.
   */
  protected List<PipelineDependency> dependencies;

  /**
   * The list of partition schemas associated with the pipeline.
   */
  private List<PartitionSchema> partitionSchemas;

  /**
   * The version string for the pipeline.
   */
  protected String pipelineVersion;

  /**
   * The status of the pipeline.
   */
  protected int pipelineStatus;

  /**
   * The pipeline logging table associated with the pipeline.
   */
  protected PipelineLogTable pipelineLogTable;

  /**
   * The performance logging table associated with the pipeline.
   */
  protected PerformanceLogTable performanceLogTable;

  /**
   * The step logging table associated with the pipeline.
   */
  protected StepLogTable stepLogTable;

  /**
   * The metricslogging table associated with the pipeline.
   */
  protected MetricsLogTable metricsLogTable;

  /**
   * The size of the current rowset.
   */
  protected int sizeRowset;

  /**
   * The meta-data for the database connection associated with "max date" auditing information.
   */
  protected DatabaseMeta maxDateConnection;

  /**
   * The table name associated with "max date" auditing information.
   */
  protected String maxDateTable;

  /**
   * The field associated with "max date" auditing information.
   */
  protected String maxDateField;

  /**
   * The amount by which to increase the "max date" value.
   */
  protected double maxDateOffset;

  /**
   * The maximum date difference used for "max date" auditing and limiting job sizes.
   */
  protected double maxDateDifference;

  /**
   * A table of named counters.
   *
   * @deprecated Moved to Pipeline
   */
  @Deprecated
  protected Hashtable<String, Counter> counters;

  /**
   * Indicators for changes in steps, databases, hops, and notes.
   */
  protected boolean changed_steps, changed_hops;

  /**
   * The database cache.
   */
  protected DBCache dbCache;

  /**
   * The time (in nanoseconds) to wait when the input buffer is empty.
   */
  protected int sleepTimeEmpty;

  /**
   * The time (in nanoseconds) to wait when the input buffer is full.
   */
  protected int sleepTimeFull;

  /**
   * The previous result.
   */
  protected Result previousResult;

  /**
   * Whether the pipeline is using unique connections.
   */
  protected boolean usingUniqueConnections;

  /**
   * Flag to indicate thread management usage. Set to default to false from version 2.5.0 on. Before that it was enabled
   * by default.
   */
  protected boolean usingThreadPriorityManagment;

  /**
   * Whether the pipeline is capturing step performance snap shots.
   */
  protected boolean capturingStepPerformanceSnapShots;

  /**
   * The step performance capturing delay.
   */
  protected long stepPerformanceCapturingDelay;

  /**
   * The step performance capturing size limit.
   */
  protected String stepPerformanceCapturingSizeLimit;

  /**
   * The steps fields cache.
   */
  protected Map<String, RowMetaInterface> stepsFieldsCache;

  /**
   * The loop cache.
   */
  protected Map<String, Boolean> loopCache;

  /**
   * The previous step cache
   */
  protected Map<String, List<StepMeta>> previousStepCache;

  /**
   * The log channel interface.
   */
  protected LogChannelInterface log;

  /**
   * The list of StepChangeListeners
   */
  protected List<StepMetaChangeListenerInterface> stepChangeListeners;

  protected byte[] keyForSessionKey;
  boolean isKeyPrivate;
  private ArrayList<Missing> missingPipeline;

  /**
   * The PipelineType enum describes the various types of pipelines in terms of execution, including Normal,
   * Serial Single-Threaded, and Single-Threaded.
   */
  public enum PipelineType {

    /**
     * A normal pipeline.
     */
    Normal( "Normal", BaseMessages.getString( PKG, "PipelineMeta.PipelineType.Normal" ) ),

    /**
     * A single-threaded pipeline.
     */
    SingleThreaded( "SingleThreaded", BaseMessages
      .getString( PKG, "PipelineMeta.PipelineType.SingleThreaded" ) );

    /**
     * The code corresponding to the pipeline type.
     */
    private final String code;

    /**
     * The description of the pipeline type.
     */
    private final String description;

    /**
     * Instantiates a new pipeline type.
     *
     * @param code        the code
     * @param description the description
     */
    PipelineType( String code, String description ) {
      this.code = code;
      this.description = description;
    }

    /**
     * Gets the code corresponding to the pipeline type.
     *
     * @return the code
     */
    public String getCode() {
      return code;
    }

    /**
     * Gets the description of the pipeline type.
     *
     * @return the description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets the pipeline type by code.
     *
     * @param pipelineTypeCode the pipeline type code
     * @return the pipeline type by code
     */
    public static PipelineType getPipelineTypeByCode( String pipelineTypeCode ) {
      if ( pipelineTypeCode != null ) {
        for ( PipelineType type : values() ) {
          if ( type.code.equalsIgnoreCase( pipelineTypeCode ) ) {
            return type;
          }
        }
      }
      return Normal;
    }

    /**
     * Gets the pipeline types descriptions.
     *
     * @return the pipeline types descriptions
     */
    public static String[] getPipelineTypesDescriptions() {
      String[] desc = new String[ values().length ];
      for ( int i = 0; i < values().length; i++ ) {
        desc[ i ] = values()[ i ].getDescription();
      }
      return desc;
    }
  }

  /**
   * The pipeline type.
   */
  protected PipelineType pipelineType;

  // //////////////////////////////////////////////////////////////////////////

  /**
   * A list of localized strings corresponding to string descriptions of the undo/redo actions.
   */
  public static final String[] desc_type_undo = {
    "",
    BaseMessages.getString( PKG, "PipelineMeta.UndoTypeDesc.UndoChange" ),
    BaseMessages.getString( PKG, "PipelineMeta.UndoTypeDesc.UndoNew" ),
    BaseMessages.getString( PKG, "PipelineMeta.UndoTypeDesc.UndoDelete" ),
    BaseMessages.getString( PKG, "PipelineMeta.UndoTypeDesc.UndoPosition" ) };

  /**
   * A constant specifying the tag value for the XML node of the pipeline information.
   */
  protected static final String XML_TAG_INFO = "info";

  /**
   * A constant specifying the tag value for the XML node of the order of steps.
   */
  public static final String XML_TAG_ORDER = "order";

  /**
   * A constant specifying the tag value for the XML node of the notes.
   */
  public static final String XML_TAG_NOTEPADS = "notepads";

  /**
   * A constant specifying the tag value for the XML node of the pipeline parameters.
   */
  public static final String XML_TAG_PARAMETERS = "parameters";

  /**
   * A constant specifying the tag value for the XML node of the pipeline dependencies.
   */
  protected static final String XML_TAG_DEPENDENCIES = "dependencies";

  /**
   * A constant specifying the tag value for the XML node of the pipeline's partition schemas.
   */
  public static final String XML_TAG_PARTITIONSCHEMAS = "partitionschemas";

  /**
   * A constant specifying the tag value for the XML node of the steps' error-handling information.
   */
  public static final String XML_TAG_STEP_ERROR_HANDLING = "step_error_handling";

  /**
   * Builds a new empty pipeline. The pipeline will have default logging capability and no variables, and
   * all internal meta-data is cleared to defaults.
   */
  public PipelineMeta() {
    clear();
    initializeVariablesFrom( null );
  }

  /**
   * Builds a new empty pipeline with a set of variables to inherit from.
   *
   * @param parent the variable space to inherit from
   */
  public PipelineMeta( VariableSpace parent ) {
    clear();
    initializeVariablesFrom( parent );
  }


  /**
   * Compares two pipeline on name and filename.
   * The comparison algorithm is as follows:<br/>
   * <ol>
   * <li>The first pipeline's filename is checked first; if it has none, the pipeline is generated
   * If the second pipeline is not generated, -1 is returned.</li>
   * <li>If the pipelines are both generated, the pipelines' names are compared. If the first
   * pipeline has no name and the second one does, a -1 is returned.
   * If the opposite is true, 1 is returned.</li>
   * <li>If they both have names they are compared as strings. If the result is non-zero it is returned. Otherwise the
   * repository directories are compared using the same technique of checking empty values and then performing a string
   * comparison, returning any non-zero result.</li>
   * </ol>
   *
   * @param t1 the first pipeline to compare
   * @param t2 the second pipeline to compare
   * @return 0 if the two pipelines are equal, 1 or -1 depending on the values (see description above)
   */
  @Override
  public int compare( PipelineMeta t1, PipelineMeta t2 ) {
    return super.compare( t1, t2 );
  }

  /**
   * Compares this pipeline's meta-data to the specified pipeline's meta-data. This method simply calls
   * compare(this, o)
   *
   * @param o the o
   * @return the int
   * @see #compare(PipelineMeta, PipelineMeta)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo( PipelineMeta o ) {
    return compare( this, o );
  }

  /**
   * Checks whether this pipeline's meta-data object is equal to the specified object. If the specified object is
   * not an instance of PipelineMeta, false is returned. Otherwise the method returns whether a call to compare() indicates
   * equality (i.e. compare(this, (PipelineMeta)obj)==0).
   *
   * @param obj the obj
   * @return true, if successful
   * @see #compare(PipelineMeta, PipelineMeta)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals( Object obj ) {
    if ( !( obj instanceof PipelineMeta ) ) {
      return false;
    }

    return compare( this, (PipelineMeta) obj ) == 0;
  }

  /**
   * Clones the pipeline meta-data object.
   *
   * @return a clone of the pipeline meta-data object
   * @see java.lang.Object#clone()
   */
  @Override
  public Object clone() {
    return realClone( true );
  }

  /**
   * Perform a real clone of the pipeline meta-data object, including cloning all lists and copying all values. If
   * the doClear parameter is true, the clone will be cleared of ALL values before the copy. If false, only the copied
   * fields will be cleared.
   *
   * @param doClear Whether to clear all of the clone's data before copying from the source object
   * @return a real clone of the calling object
   */
  public Object realClone( boolean doClear ) {

    try {
      PipelineMeta pipelineMeta = (PipelineMeta) super.clone();
      if ( doClear ) {
        pipelineMeta.clear();
      } else {
        // Clear out the things we're replacing below
        pipelineMeta.steps = new ArrayList<>();
        pipelineMeta.hops = new ArrayList<>();
        pipelineMeta.notes = new ArrayList<>();
        pipelineMeta.dependencies = new ArrayList<>();
        pipelineMeta.partitionSchemas = new ArrayList<>();
        pipelineMeta.namedParams = new NamedParamsDefault();
        pipelineMeta.stepChangeListeners = new ArrayList<>();
      }
      for ( StepMeta step : steps ) {
        pipelineMeta.addStep( (StepMeta) step.clone() );
      }
      // PDI-15799: Step references are original yet. Set them to the clones.
      for ( StepMeta step : pipelineMeta.getSteps() ) {
        final StepMetaInterface stepMetaInterface = step.getStepMetaInterface();
        if ( stepMetaInterface != null ) {
          final StepIOMetaInterface stepIOMeta = stepMetaInterface.getStepIOMeta();
          if ( stepIOMeta != null ) {
            for ( StreamInterface stream : stepIOMeta.getInfoStreams() ) {
              String streamStepName = stream.getStepname();
              if ( streamStepName != null ) {
                StepMeta streamStepMeta = pipelineMeta.findStep( streamStepName );
                stream.setStepMeta( streamStepMeta );
              }
            }
          }
        }
      }
      for ( PipelineHopMeta hop : hops ) {
        pipelineMeta.addPipelineHop( (PipelineHopMeta) hop.clone() );
      }
      for ( NotePadMeta note : notes ) {
        pipelineMeta.addNote( (NotePadMeta) note.clone() );
      }
      for ( PipelineDependency dep : dependencies ) {
        pipelineMeta.addDependency( (PipelineDependency) dep.clone() );
      }
      for ( PartitionSchema schema : partitionSchemas ) {
        pipelineMeta.getPartitionSchemas().add( (PartitionSchema) schema.clone() );
      }
      for ( String key : listParameters() ) {
        pipelineMeta.addParameterDefinition( key, getParameterDefault( key ), getParameterDescription( key ) );
      }

      return pipelineMeta;
    } catch ( Exception e ) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Clears the pipeline's meta-data, including the lists of databases, steps, hops, notes, dependencies,
   * partition schemas, slave servers, and cluster schemas. Logging information and timeouts are reset to defaults, and
   * recent connection info is cleared.
   */
  @Override
  public void clear() {
    steps = new ArrayList<>();
    hops = new ArrayList<>();
    dependencies = new ArrayList<>();
    partitionSchemas = new ArrayList<>();
    namedParams = new NamedParamsDefault();
    stepChangeListeners = new ArrayList<>();

    pipelineStatus = -1;
    pipelineVersion = null;

    pipelineLogTable = PipelineLogTable.getDefault( this, metaStore, steps );
    performanceLogTable = PerformanceLogTable.getDefault( this, metaStore );
    stepLogTable = StepLogTable.getDefault( this, metaStore );
    metricsLogTable = MetricsLogTable.getDefault( this, metaStore );

    sizeRowset = Const.ROWS_IN_ROWSET;
    sleepTimeEmpty = Const.TIMEOUT_GET_MILLIS;
    sleepTimeFull = Const.TIMEOUT_PUT_MILLIS;

    maxDateConnection = null;
    maxDateTable = null;
    maxDateField = null;
    maxDateOffset = 0.0;

    maxDateDifference = 0.0;

    undo = new ArrayList<>();
    max_undo = Const.MAX_UNDO;
    undo_position = -1;

    super.clear();

    // LOAD THE DATABASE CACHE!
    dbCache = DBCache.getInstance();

    // Thread priority:
    // - set to false in version 2.5.0
    // - re-enabling in version 3.0.1 to prevent excessive locking (PDI-491)
    //
    usingThreadPriorityManagment = true;

    // The performance monitoring options
    //
    capturingStepPerformanceSnapShots = false;
    stepPerformanceCapturingDelay = 1000; // every 1 seconds
    stepPerformanceCapturingSizeLimit = "100"; // maximum 100 data points

    stepsFieldsCache = new HashMap<>();
    loopCache = new HashMap<>();
    previousStepCache = new HashMap<>();
    pipelineType = PipelineType.Normal;

    log = LogChannel.GENERAL;
  }

  /**
   * Add a new step to the pipeline. Also marks that the pipeline's steps have changed.
   *
   * @param stepMeta The meta-data for the step to be added.
   */
  public void addStep( StepMeta stepMeta ) {
    steps.add( stepMeta );
    stepMeta.setParentPipelineMeta( this );
    StepMetaInterface iface = stepMeta.getStepMetaInterface();
    if ( iface instanceof StepMetaChangeListenerInterface ) {
      addStepChangeListener( (StepMetaChangeListenerInterface) iface );
    }
    changed_steps = true;
    clearCaches();
  }

  /**
   * Add a new step to the pipeline if that step didn't exist yet. Otherwise, replace the step. This method also
   * marks that the pipeline's steps have changed.
   *
   * @param stepMeta The meta-data for the step to be added.
   */
  public void addOrReplaceStep( StepMeta stepMeta ) {
    int index = steps.indexOf( stepMeta );
    if ( index < 0 ) {
      index = steps.add( stepMeta ) ? 0 : index;
    } else {
      StepMeta previous = getStep( index );
      previous.replaceMeta( stepMeta );
    }
    stepMeta.setParentPipelineMeta( this );
    StepMetaInterface iface = stepMeta.getStepMetaInterface();
    if ( index != -1 && iface instanceof StepMetaChangeListenerInterface ) {
      addStepChangeListener( index, (StepMetaChangeListenerInterface) iface );
    }
    changed_steps = true;
    clearCaches();
  }

  /**
   * Add a new hop to the pipeline. The hop information (source and target steps, e.g.) should be configured in
   * the PipelineHopMeta object before calling addPipelineHop(). Also marks that the pipeline's hops have changed.
   *
   * @param hi The hop meta-data to be added.
   */
  public void addPipelineHop( PipelineHopMeta hi ) {
    hops.add( hi );
    changed_hops = true;
    clearCaches();
  }

  /**
   * Add a new dependency to the pipeline.
   *
   * @param td The pipeline dependency to be added.
   */
  public void addDependency( PipelineDependency td ) {
    dependencies.add( td );
  }

  /**
   * Add a new step to the pipeline at the specified index. This method sets the step's parent pipeline to
   * the this pipeline, and marks that the pipelines' steps have changed.
   *
   * @param p        The index into the step list
   * @param stepMeta The step to be added.
   */
  public void addStep( int p, StepMeta stepMeta ) {
    steps.add( p, stepMeta );
    stepMeta.setParentPipelineMeta( this );
    changed_steps = true;
    StepMetaInterface iface = stepMeta.getStepMetaInterface();
    if ( iface instanceof StepMetaChangeListenerInterface ) {
      addStepChangeListener( p, (StepMetaChangeListenerInterface) stepMeta.getStepMetaInterface() );
    }
    clearCaches();
  }

  /**
   * Add a new hop to the pipeline on a certain location (i.e. the specified index). Also marks that the
   * pipeline's hops have changed.
   *
   * @param p  the index into the hop list
   * @param hi The hop to be added.
   */
  public void addPipelineHop( int p, PipelineHopMeta hi ) {
    try {
      hops.add( p, hi );
    } catch ( IndexOutOfBoundsException e ) {
      hops.add( hi );
    }
    changed_hops = true;
    clearCaches();
  }

  /**
   * Add a new dependency to the pipeline on a certain location (i.e. the specified index).
   *
   * @param p  The index into the dependencies list.
   * @param td The pipeline dependency to be added.
   */
  public void addDependency( int p, PipelineDependency td ) {
    dependencies.add( p, td );
  }

  /**
   * Get a list of defined steps in this pipeline.
   *
   * @return an ArrayList of defined steps.
   */
  public List<StepMeta> getSteps() {
    return steps;
  }

  /**
   * Retrieves a step on a certain location (i.e. the specified index).
   *
   * @param i The index into the steps list.
   * @return The desired step's meta-data.
   */
  public StepMeta getStep( int i ) {
    return steps.get( i );
  }

  /**
   * Get a list of defined hops in this pipeline.
   *
   * @return a list of defined hops.
   */
  public List<PipelineHopMeta> getPipelineHops() {
    return Collections.unmodifiableList( hops );
  }

  /**
   * Retrieves a hop on a certain location (i.e. the specified index).
   *
   * @param i The index into the hops list.
   * @return The desired hop's meta-data.
   */
  public PipelineHopMeta getPipelineHop( int i ) {
    return hops.get( i );
  }

  /**
   * Retrieves a dependency on a certain location (i.e. the specified index).
   *
   * @param i The index into the dependencies list.
   * @return The dependency object.
   */
  public PipelineDependency getDependency( int i ) {
    return dependencies.get( i );
  }

  /**
   * Removes a step from the pipeline on a certain location (i.e. the specified index). Also marks that the
   * pipeline's steps have changed.
   *
   * @param i The index
   */
  public void removeStep( int i ) {
    if ( i < 0 || i >= steps.size() ) {
      return;
    }

    StepMeta removeStep = steps.get( i );
    StepMetaInterface iface = removeStep.getStepMetaInterface();
    if ( iface instanceof StepMetaChangeListenerInterface ) {
      removeStepChangeListener( (StepMetaChangeListenerInterface) iface );
    }

    steps.remove( i );

    if ( removeStep.getStepMetaInterface() instanceof Missing ) {
      removeMissingPipeline( (Missing) removeStep.getStepMetaInterface() );
    }

    changed_steps = true;
    clearCaches();
  }

  /**
   * Removes a hop from the pipeline on a certain location (i.e. the specified index). Also marks that the
   * pipeline's hops have changed.
   *
   * @param i The index into the hops list
   */
  public void removePipelineHop( int i ) {
    if ( i < 0 || i >= hops.size() ) {
      return;
    }

    hops.remove( i );
    changed_hops = true;
    clearCaches();
  }

  /**
   * Removes a hop from the pipeline. Also marks that the
   * pipeline's hops have changed.
   *
   * @param hop The hop to remove from the list of hops
   */
  public void removePipelineHop( PipelineHopMeta hop ) {
    hops.remove( hop );
    changed_hops = true;
    clearCaches();
  }

  /**
   * Removes a dependency from the pipeline on a certain location (i.e. the specified index).
   *
   * @param i The location
   */
  public void removeDependency( int i ) {
    if ( i < 0 || i >= dependencies.size() ) {
      return;
    }
    dependencies.remove( i );
  }

  /**
   * Clears all the dependencies from the pipeline.
   */
  public void removeAllDependencies() {
    dependencies.clear();
  }

  /**
   * Gets the number of steps in the pipeline.
   *
   * @return The number of steps in the pipeline.
   */
  public int nrSteps() {
    return steps.size();
  }

  /**
   * Gets the number of hops in the pipeline.
   *
   * @return The number of hops in the pipeline.
   */
  public int nrPipelineHops() {
    return hops.size();
  }

  /**
   * Gets the number of dependencies in the pipeline.
   *
   * @return The number of dependencies in the pipeline.
   */
  public int nrDependencies() {
    return dependencies.size();
  }

  /**
   * Gets the number of stepChangeListeners in the pipeline.
   *
   * @return The number of stepChangeListeners in the pipeline.
   */
  public int nrStepChangeListeners() {
    return stepChangeListeners.size();
  }

  /**
   * Changes the content of a step on a certain position. This is accomplished by setting the step's metadata at the
   * specified index to the specified meta-data object. The new step's parent pipeline is updated to be this
   * pipeline.
   *
   * @param i        The index into the steps list
   * @param stepMeta The step meta-data to set
   */
  public void setStep( int i, StepMeta stepMeta ) {
    StepMetaInterface iface = stepMeta.getStepMetaInterface();
    if ( iface instanceof StepMetaChangeListenerInterface ) {
      addStepChangeListener( i, (StepMetaChangeListenerInterface) stepMeta.getStepMetaInterface() );
    }
    steps.set( i, stepMeta );
    stepMeta.setParentPipelineMeta( this );
    clearCaches();
  }

  /**
   * Changes the content of a hop on a certain position. This is accomplished by setting the hop's metadata at the
   * specified index to the specified meta-data object.
   *
   * @param i  The index into the hops list
   * @param hi The hop meta-data to set
   */
  public void setPipelineHop( int i, PipelineHopMeta hi ) {
    hops.set( i, hi );
    clearCaches();
  }

  /**
   * Gets the list of used steps, which are the steps that are connected by hops.
   *
   * @return a list with all the used steps
   */
  public List<StepMeta> getUsedSteps() {
    List<StepMeta> list = new ArrayList<>();

    for ( StepMeta stepMeta : steps ) {
      if ( isStepUsedInPipelineHops( stepMeta ) ) {
        list.add( stepMeta );
      }
    }
    if ( list.isEmpty() && getSteps().size() == 1 ) {
      list = getSteps();
    }

    return list;
  }

  /**
   * Searches the list of steps for a step with a certain name.
   *
   * @param name The name of the step to look for
   * @return The step information or null if no nothing was found.
   */
  public StepMeta findStep( String name ) {
    return findStep( name, null );
  }

  /**
   * Searches the list of steps for a step with a certain name while excluding one step.
   *
   * @param name    The name of the step to look for
   * @param exclude The step information to exclude.
   * @return The step information or null if nothing was found.
   */
  public StepMeta findStep( String name, StepMeta exclude ) {
    if ( name == null ) {
      return null;
    }

    int excl = -1;
    if ( exclude != null ) {
      excl = indexOfStep( exclude );
    }

    for ( int i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      if ( i != excl && stepMeta.getName().equalsIgnoreCase( name ) ) {
        return stepMeta;
      }
    }
    return null;
  }

  /**
   * Searches the list of hops for a hop with a certain name.
   *
   * @param name The name of the hop to look for
   * @return The hop information or null if nothing was found.
   */
  public PipelineHopMeta findPipelineHop( String name ) {
    int i;

    for ( i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.toString().equalsIgnoreCase( name ) ) {
        return hi;
      }
    }
    return null;
  }

  /**
   * Search all hops for a hop where a certain step is at the start.
   *
   * @param fromstep The step at the start of the hop.
   * @return The hop or null if no hop was found.
   */
  public PipelineHopMeta findPipelineHopFrom( StepMeta fromstep ) {
    int i;
    for ( i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.getFromStep() != null && hi.getFromStep().equals( fromstep ) ) { // return the first
        return hi;
      }
    }
    return null;
  }

  public List<PipelineHopMeta> findAllPipelineHopFrom( StepMeta fromstep ) {
    return hops.stream()
      .filter( hop -> hop.getFromStep() != null && hop.getFromStep().equals( fromstep ) )
      .collect( Collectors.toList() );
  }

  /**
   * Find a certain hop in the pipeline.
   *
   * @param hi The hop information to look for.
   * @return The hop or null if no hop was found.
   */
  public PipelineHopMeta findPipelineHop( PipelineHopMeta hi ) {
    return findPipelineHop( hi.getFromStep(), hi.getToStep() );
  }

  /**
   * Search all hops for a hop where a certain step is at the start and another is at the end.
   *
   * @param from The step at the start of the hop.
   * @param to   The step at the end of the hop.
   * @return The hop or null if no hop was found.
   */
  public PipelineHopMeta findPipelineHop( StepMeta from, StepMeta to ) {
    return findPipelineHop( from, to, false );
  }

  /**
   * Search all hops for a hop where a certain step is at the start and another is at the end.
   *
   * @param from        The step at the start of the hop.
   * @param to          The step at the end of the hop.
   * @param disabledToo the disabled too
   * @return The hop or null if no hop was found.
   */
  public PipelineHopMeta findPipelineHop( StepMeta from, StepMeta to, boolean disabledToo ) {
    for ( int i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.isEnabled() || disabledToo ) {
        if ( hi.getFromStep() != null && hi.getToStep() != null && hi.getFromStep().equals( from ) && hi.getToStep()
          .equals( to ) ) {
          return hi;
        }
      }
    }
    return null;
  }

  /**
   * Search all hops for a hop where a certain step is at the end.
   *
   * @param tostep The step at the end of the hop.
   * @return The hop or null if no hop was found.
   */
  public PipelineHopMeta findPipelineHopTo( StepMeta tostep ) {
    int i;
    for ( i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.getToStep() != null && hi.getToStep().equals( tostep ) ) { // Return the first!
        return hi;
      }
    }
    return null;
  }

  /**
   * Determines whether or not a certain step is informative. This means that the previous step is sending information
   * to this step, but only informative. This means that this step is using the information to process the actual stream
   * of data. We use this in StreamLookup, TableInput and other types of steps.
   *
   * @param this_step The step that is receiving information.
   * @param prev_step The step that is sending information
   * @return true if prev_step if informative for this_step.
   */
  public boolean isStepInformative( StepMeta this_step, StepMeta prev_step ) {
    String[] infoSteps = this_step.getStepMetaInterface().getStepIOMeta().getInfoStepnames();
    if ( infoSteps == null ) {
      return false;
    }
    for ( int i = 0; i < infoSteps.length; i++ ) {
      if ( prev_step.getName().equalsIgnoreCase( infoSteps[ i ] ) ) {
        return true;
      }
    }

    return false;
  }

  /**
   * Counts the number of previous steps for a step name.
   *
   * @param stepname The name of the step to start from
   * @return The number of preceding steps.
   * @deprecated
   */
  @Deprecated
  public int findNrPrevSteps( String stepname ) {
    return findNrPrevSteps( findStep( stepname ), false );
  }

  /**
   * Counts the number of previous steps for a step name taking into account whether or not they are informational.
   *
   * @param stepname The name of the step to start from
   * @param info     true if only the informational steps are desired, false otherwise
   * @return The number of preceding steps.
   * @deprecated
   */
  @Deprecated
  public int findNrPrevSteps( String stepname, boolean info ) {
    return findNrPrevSteps( findStep( stepname ), info );
  }

  /**
   * Find the number of steps that precede the indicated step.
   *
   * @param stepMeta The source step
   * @return The number of preceding steps found.
   */
  public int findNrPrevSteps( StepMeta stepMeta ) {
    return findNrPrevSteps( stepMeta, false );
  }

  /**
   * Find the previous step on a certain location (i.e. the specified index).
   *
   * @param stepname The source step name
   * @param nr       the index into the step list
   * @return The preceding step found.
   * @deprecated
   */
  @Deprecated
  public StepMeta findPrevStep( String stepname, int nr ) {
    return findPrevStep( findStep( stepname ), nr );
  }

  /**
   * Find the previous step on a certain location taking into account the steps being informational or not.
   *
   * @param stepname The name of the step
   * @param nr       The index into the step list
   * @param info     true if only the informational steps are desired, false otherwise
   * @return The step information
   * @deprecated
   */
  @Deprecated
  public StepMeta findPrevStep( String stepname, int nr, boolean info ) {
    return findPrevStep( findStep( stepname ), nr, info );
  }

  /**
   * Find the previous step on a certain location (i.e. the specified index).
   *
   * @param stepMeta The source step information
   * @param nr       the index into the hops list
   * @return The preceding step found.
   */
  public StepMeta findPrevStep( StepMeta stepMeta, int nr ) {
    return findPrevStep( stepMeta, nr, false );
  }

  /**
   * Count the number of previous steps on a certain location taking into account the steps being informational or not.
   *
   * @param stepMeta The name of the step
   * @param info     true if only the informational steps are desired, false otherwise
   * @return The number of preceding steps
   * @deprecated please use method findPreviousSteps
   */
  @Deprecated
  public int findNrPrevSteps( StepMeta stepMeta, boolean info ) {
    int count = 0;
    int i;

    for ( i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.getToStep() != null && hi.isEnabled() && hi.getToStep().equals( stepMeta ) ) {
        // Check if this previous step isn't informative (StreamValueLookup)
        // We don't want fields from this stream to show up!
        if ( info || !isStepInformative( stepMeta, hi.getFromStep() ) ) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Find the previous step on a certain location taking into account the steps being informational or not.
   *
   * @param stepMeta The step
   * @param nr       The index into the hops list
   * @param info     true if we only want the informational steps.
   * @return The preceding step information
   * @deprecated please use method findPreviousSteps
   */
  @Deprecated
  public StepMeta findPrevStep( StepMeta stepMeta, int nr, boolean info ) {
    int count = 0;
    int i;

    for ( i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.getToStep() != null && hi.isEnabled() && hi.getToStep().equals( stepMeta ) ) {
        if ( info || !isStepInformative( stepMeta, hi.getFromStep() ) ) {
          if ( count == nr ) {
            return hi.getFromStep();
          }
          count++;
        }
      }
    }
    return null;
  }

  /**
   * Get the list of previous steps for a certain reference step. This includes the info steps.
   *
   * @param stepMeta The reference step
   * @return The list of the preceding steps, including the info steps.
   */
  public List<StepMeta> findPreviousSteps( StepMeta stepMeta ) {
    return findPreviousSteps( stepMeta, true );
  }

  /**
   * Get the previous steps on a certain location taking into account the steps being informational or not.
   *
   * @param stepMeta The name of the step
   * @param info     true if we only want the informational steps.
   * @return The list of the preceding steps
   */
  public List<StepMeta> findPreviousSteps( StepMeta stepMeta, boolean info ) {
    if ( stepMeta == null ) {
      return new ArrayList<>();
    }

    String cacheKey = getStepMetaCacheKey( stepMeta, info );
    List<StepMeta> previousSteps = previousStepCache.get( cacheKey );
    if ( previousSteps == null ) {
      previousSteps = new ArrayList<>();
      for ( PipelineHopMeta hi : hops ) {
        if ( hi.getToStep() != null && hi.isEnabled() && hi.getToStep().equals( stepMeta ) ) {
          // Check if this previous step isn't informative (StreamValueLookup)
          // We don't want fields from this stream to show up!
          if ( info || !isStepInformative( stepMeta, hi.getFromStep() ) ) {
            previousSteps.add( hi.getFromStep() );
          }
        }
      }
      previousStepCache.put( cacheKey, previousSteps );
    }
    return previousSteps;
  }

  /**
   * Get the informational steps for a certain step. An informational step is a step that provides information for
   * lookups, etc.
   *
   * @param stepMeta The name of the step
   * @return An array of the informational steps found
   */
  public StepMeta[] getInfoStep( StepMeta stepMeta ) {
    String[] infoStepName = stepMeta.getStepMetaInterface().getStepIOMeta().getInfoStepnames();
    if ( infoStepName == null ) {
      return null;
    }

    StepMeta[] infoStep = new StepMeta[ infoStepName.length ];
    for ( int i = 0; i < infoStep.length; i++ ) {
      infoStep[ i ] = findStep( infoStepName[ i ] );
    }

    return infoStep;
  }

  /**
   * Find the the number of informational steps for a certain step.
   *
   * @param stepMeta The step
   * @return The number of informational steps found.
   */
  public int findNrInfoSteps( StepMeta stepMeta ) {
    if ( stepMeta == null ) {
      return 0;
    }

    int count = 0;

    for ( int i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi == null || hi.getToStep() == null ) {
        log.logError( BaseMessages.getString( PKG, "PipelineMeta.Log.DestinationOfHopCannotBeNull" ) );
      }
      if ( hi != null && hi.getToStep() != null && hi.isEnabled() && hi.getToStep().equals( stepMeta ) ) {
        // Check if this previous step isn't informative (StreamValueLookup)
        // We don't want fields from this stream to show up!
        if ( isStepInformative( stepMeta, hi.getFromStep() ) ) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Find the informational fields coming from an informational step into the step specified.
   *
   * @param stepname The name of the step
   * @return A row containing fields with origin.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getPrevInfoFields( String stepname ) throws HopStepException {
    return getPrevInfoFields( findStep( stepname ) );
  }

  /**
   * Find the informational fields coming from an informational step into the step specified.
   *
   * @param stepMeta The receiving step
   * @return A row containing fields with origin.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getPrevInfoFields( StepMeta stepMeta ) throws HopStepException {
    for ( int i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;
      PipelineHopMeta hi = getPipelineHop( i );

      if ( hi.isEnabled() && hi.getToStep().equals( stepMeta ) ) {
        StepMeta infoStep = hi.getFromStep();
        if ( isStepInformative( stepMeta, infoStep ) ) {
          RowMetaInterface row = getPrevStepFields( infoStep );
          return getThisStepFields( infoStep, stepMeta, row );
        }
      }
    }
    return new RowMeta();
  }

  /**
   * Find the number of succeeding steps for a certain originating step.
   *
   * @param stepMeta The originating step
   * @return The number of succeeding steps.
   * @deprecated use {@link #getNextSteps(StepMeta)}
   */
  @Deprecated
  public int findNrNextSteps( StepMeta stepMeta ) {
    int count = 0;
    int i;
    for ( i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.isEnabled() && hi.getFromStep().equals( stepMeta ) ) {
        count++;
      }
    }
    return count;
  }

  /**
   * Find the succeeding step at a location for an originating step.
   *
   * @param stepMeta The originating step
   * @param nr       The location
   * @return The step found.
   * @deprecated use {@link #getNextSteps(StepMeta)}
   */
  @Deprecated
  public StepMeta findNextStep( StepMeta stepMeta, int nr ) {
    int count = 0;
    int i;

    for ( i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.isEnabled() && hi.getFromStep().equals( stepMeta ) ) {
        if ( count == nr ) {
          return hi.getToStep();
        }
        count++;
      }
    }
    return null;
  }

  /**
   * Retrieve an array of preceding steps for a certain destination step. This includes the info steps.
   *
   * @param stepMeta The destination step
   * @return An array containing the preceding steps.
   */
  public StepMeta[] getPrevSteps( StepMeta stepMeta ) {
    List<StepMeta> prevSteps = previousStepCache.get( getStepMetaCacheKey( stepMeta, true ) );
    if ( prevSteps == null ) {
      prevSteps = new ArrayList<>();
      for ( int i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;
        PipelineHopMeta hopMeta = getPipelineHop( i );
        if ( hopMeta.isEnabled() && hopMeta.getToStep().equals( stepMeta ) ) {
          prevSteps.add( hopMeta.getFromStep() );
        }
      }
    }

    return prevSteps.toArray( new StepMeta[ prevSteps.size() ] );
  }

  /**
   * Retrieve an array of succeeding step names for a certain originating step name.
   *
   * @param stepname The originating step name
   * @return An array of succeeding step names
   */
  public String[] getPrevStepNames( String stepname ) {
    return getPrevStepNames( findStep( stepname ) );
  }

  /**
   * Retrieve an array of preceding steps for a certain destination step.
   *
   * @param stepMeta The destination step
   * @return an array of preceding step names.
   */
  public String[] getPrevStepNames( StepMeta stepMeta ) {
    StepMeta[] prevStepMetas = getPrevSteps( stepMeta );
    String[] retval = new String[ prevStepMetas.length ];
    for ( int x = 0; x < prevStepMetas.length; x++ ) {
      retval[ x ] = prevStepMetas[ x ].getName();
    }

    return retval;
  }

  /**
   * Retrieve an array of succeeding steps for a certain originating step.
   *
   * @param stepMeta The originating step
   * @return an array of succeeding steps.
   * @deprecated use findNextSteps instead
   */
  @Deprecated
  public StepMeta[] getNextSteps( StepMeta stepMeta ) {
    List<StepMeta> nextSteps = new ArrayList<>();
    for ( int i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.isEnabled() && hi.getFromStep().equals( stepMeta ) ) {
        nextSteps.add( hi.getToStep() );
      }
    }

    return nextSteps.toArray( new StepMeta[ nextSteps.size() ] );
  }

  /**
   * Retrieve a list of succeeding steps for a certain originating step.
   *
   * @param stepMeta The originating step
   * @return an array of succeeding steps.
   */
  public List<StepMeta> findNextSteps( StepMeta stepMeta ) {
    List<StepMeta> nextSteps = new ArrayList<>();
    for ( int i = 0; i < nrPipelineHops(); i++ ) { // Look at all the hops;

      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.isEnabled() && hi.getFromStep().equals( stepMeta ) ) {
        nextSteps.add( hi.getToStep() );
      }
    }

    return nextSteps;
  }

  /**
   * Retrieve an array of succeeding step names for a certain originating step.
   *
   * @param stepMeta The originating step
   * @return an array of succeeding step names.
   */
  public String[] getNextStepNames( StepMeta stepMeta ) {
    StepMeta[] nextStepMeta = getNextSteps( stepMeta );
    String[] retval = new String[ nextStepMeta.length ];
    for ( int x = 0; x < nextStepMeta.length; x++ ) {
      retval[ x ] = nextStepMeta[ x ].getName();
    }

    return retval;
  }

  /**
   * Find the step that is located on a certain point on the canvas, taking into account the icon size.
   *
   * @param x        the x-coordinate of the point queried
   * @param y        the y-coordinate of the point queried
   * @param iconsize the iconsize
   * @return The step information if a step is located at the point. Otherwise, if no step was found: null.
   */
  public StepMeta getStep( int x, int y, int iconsize ) {
    int i, s;
    s = steps.size();
    for ( i = s - 1; i >= 0; i-- ) { // Back to front because drawing goes from start to end
      StepMeta stepMeta = steps.get( i );
      if ( partOfPipelineHop( stepMeta ) ) { // Only consider steps from active or inactive hops!
        Point p = stepMeta.getLocation();
        if ( p != null ) {
          if ( x >= p.x && x <= p.x + iconsize && y >= p.y && y <= p.y + iconsize + 20 ) {
            return stepMeta;
          }
        }
      }
    }
    return null;
  }

  /**
   * Determines whether or not a certain step is part of a hop.
   *
   * @param stepMeta The step queried
   * @return true if the step is part of a hop.
   */
  public boolean partOfPipelineHop( StepMeta stepMeta ) {
    int i;
    for ( i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.getFromStep() == null || hi.getToStep() == null ) {
        return false;
      }
      if ( hi.getFromStep().equals( stepMeta ) || hi.getToStep().equals( stepMeta ) ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the fields that are emitted by a certain step name.
   *
   * @param stepname The stepname of the step to be queried.
   * @return A row containing the fields emitted.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getStepFields( String stepname ) throws HopStepException {
    StepMeta stepMeta = findStep( stepname );
    if ( stepMeta != null ) {
      return getStepFields( stepMeta );
    } else {
      return null;
    }
  }

  /**
   * Returns the fields that are emitted by a certain step.
   *
   * @param stepMeta The step to be queried.
   * @return A row containing the fields emitted.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getStepFields( StepMeta stepMeta ) throws HopStepException {
    return getStepFields( stepMeta, null );
  }

  /**
   * Gets the fields for each of the specified steps and merges them into a single set
   *
   * @param stepMeta the step meta
   * @return an interface to the step fields
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getStepFields( StepMeta[] stepMeta ) throws HopStepException {
    RowMetaInterface fields = new RowMeta();

    for ( int i = 0; i < stepMeta.length; i++ ) {
      RowMetaInterface flds = getStepFields( stepMeta[ i ] );
      if ( flds != null ) {
        fields.mergeRowMeta( flds, stepMeta[ i ].getName() );
      }
    }
    return fields;
  }

  /**
   * Returns the fields that are emitted by a certain step.
   *
   * @param stepMeta The step to be queried.
   * @param monitor  The progress monitor for progress dialog. (null if not used!)
   * @return A row containing the fields emitted.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getStepFields( StepMeta stepMeta, ProgressMonitorListener monitor ) throws HopStepException {
    setMetaStoreOnMappingSteps();
    return getStepFields( stepMeta, null, monitor );
  }

  /**
   * Returns the fields that are emitted by a certain step.
   *
   * @param stepMeta   The step to be queried.
   * @param targetStep the target step
   * @param monitor    The progress monitor for progress dialog. (null if not used!)
   * @return A row containing the fields emitted.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getStepFields( StepMeta stepMeta, StepMeta targetStep, ProgressMonitorListener monitor ) throws HopStepException {
    RowMetaInterface row = new RowMeta();

    if ( stepMeta == null ) {
      return row;
    }

    String fromToCacheEntry = stepMeta.getName() + ( targetStep != null ? ( "-" + targetStep.getName() ) : "" );
    RowMetaInterface rowMeta = stepsFieldsCache.get( fromToCacheEntry );
    if ( rowMeta != null ) {
      return rowMeta;
    }

    // See if the step is sending ERROR rows to the specified target step.
    //
    if ( targetStep != null && stepMeta.isSendingErrorRowsToStep( targetStep ) ) {
      // The error rows are the same as the input rows for
      // the step but with the selected error fields added
      //
      row = getPrevStepFields( stepMeta );

      // Add to this the error fields...
      StepErrorMeta stepErrorMeta = stepMeta.getStepErrorMeta();
      row.addRowMeta( stepErrorMeta.getErrorFields() );

      // Store this row in the cache
      //
      stepsFieldsCache.put( fromToCacheEntry, row );

      return row;
    }

    // Resume the regular program...

    List<StepMeta> prevSteps = findPreviousSteps( stepMeta, false );

    int nrPrevious = prevSteps.size();

    if ( log.isDebug() ) {
      log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.FromStepALookingAtPreviousStep", stepMeta.getName(),
        String.valueOf( nrPrevious ) ) );
    }
    for ( int i = 0; i < prevSteps.size(); i++ ) {
      StepMeta prevStepMeta = prevSteps.get( i );

      if ( monitor != null ) {
        monitor.subTask(
          BaseMessages.getString( PKG, "PipelineMeta.Monitor.CheckingStepTask.Title", prevStepMeta.getName() ) );
      }

      RowMetaInterface add = getStepFields( prevStepMeta, stepMeta, monitor );
      if ( add == null ) {
        add = new RowMeta();
      }
      if ( log.isDebug() ) {
        log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.FoundFieldsToAdd" ) + add.toString() );
      }
      if ( i == 0 ) {
        row.addRowMeta( add );
      } else {
        // See if the add fields are not already in the row
        for ( int x = 0; x < add.size(); x++ ) {
          ValueMetaInterface v = add.getValueMeta( x );
          ValueMetaInterface s = row.searchValueMeta( v.getName() );
          if ( s == null ) {
            row.addValueMeta( v );
          }
        }
      }
    }

    // Finally, see if we need to add/modify/delete fields with this step "name"
    rowMeta = getThisStepFields( stepMeta, targetStep, row, monitor );

    // Store this row in the cache
    //
    stepsFieldsCache.put( fromToCacheEntry, rowMeta );

    return rowMeta;
  }

  /**
   * Find the fields that are entering a step with a certain name.
   *
   * @param stepname The name of the step queried
   * @return A row containing the fields (w/ origin) entering the step
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getPrevStepFields( String stepname ) throws HopStepException {
    return getPrevStepFields( findStep( stepname ) );
  }

  /**
   * Find the fields that are entering a certain step.
   *
   * @param stepMeta The step queried
   * @return A row containing the fields (w/ origin) entering the step
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getPrevStepFields( StepMeta stepMeta ) throws HopStepException {
    return getPrevStepFields( stepMeta, null );
  }

  /**
   * Find the fields that are entering a certain step.
   *
   * @param stepMeta The step queried
   * @param monitor  The progress monitor for progress dialog. (null if not used!)
   * @return A row containing the fields (w/ origin) entering the step
   * @throws HopStepException the kettle step exception
   */


  public RowMetaInterface getPrevStepFields( StepMeta stepMeta, ProgressMonitorListener monitor ) throws HopStepException {
    return getPrevStepFields( stepMeta, null, monitor );
  }

  public RowMetaInterface getPrevStepFields(
    StepMeta stepMeta, final String stepName, ProgressMonitorListener monitor )
    throws HopStepException {
    clearStepFieldsCachce();
    RowMetaInterface row = new RowMeta();

    if ( stepMeta == null ) {
      return null;
    }
    List<StepMeta> prevSteps = findPreviousSteps( stepMeta );
    int nrPrevSteps = prevSteps.size();
    if ( log.isDebug() ) {
      log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.FromStepALookingAtPreviousStep", stepMeta.getName(),
        String.valueOf( nrPrevSteps ) ) );
    }
    StepMeta prevStepMeta = null;
    for ( int i = 0; i < nrPrevSteps; i++ ) {
      prevStepMeta = prevSteps.get( i );
      if ( stepName != null && !stepName.equalsIgnoreCase( prevStepMeta.getName() ) ) {
        continue;
      }

      if ( monitor != null ) {
        monitor.subTask(
          BaseMessages.getString( PKG, "PipelineMeta.Monitor.CheckingStepTask.Title", prevStepMeta.getName() ) );
      }

      RowMetaInterface add = getStepFields( prevStepMeta, stepMeta, monitor );

      if ( log.isDebug() ) {
        log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.FoundFieldsToAdd2" ) + add.toString() );
      }
      if ( i == 0 ) {
        // we expect all input streams to be of the same layout!

        row.addRowMeta( add ); // recursive!
      } else {
        // See if the add fields are not already in the row
        for ( int x = 0; x < add.size(); x++ ) {
          ValueMetaInterface v = add.getValueMeta( x );
          ValueMetaInterface s = row.searchValueMeta( v.getName() );
          if ( s == null ) {
            row.addValueMeta( v );
          }
        }
      }
    }
    return row;
  }

  /**
   * Return the fields that are emitted by a step with a certain name.
   *
   * @param stepname The name of the step that's being queried.
   * @param row      A row containing the input fields or an empty row if no input is required.
   * @return A Row containing the output fields.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getThisStepFields( String stepname, RowMetaInterface row ) throws HopStepException {
    return getThisStepFields( findStep( stepname ), null, row );
  }

  /**
   * Returns the fields that are emitted by a step.
   *
   * @param stepMeta : The StepMeta object that's being queried
   * @param nextStep : if non-null this is the next step that's call back to ask what's being sent
   * @param row      : A row containing the input fields or an empty row if no input is required.
   * @return A Row containing the output fields.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getThisStepFields( StepMeta stepMeta, StepMeta nextStep, RowMetaInterface row ) throws HopStepException {
    return getThisStepFields( stepMeta, nextStep, row, null );
  }

  /**
   * Returns the fields that are emitted by a step.
   *
   * @param stepMeta : The StepMeta object that's being queried
   * @param nextStep : if non-null this is the next step that's call back to ask what's being sent
   * @param row      : A row containing the input fields or an empty row if no input is required.
   * @param monitor  the monitor
   * @return A Row containing the output fields.
   * @throws HopStepException the kettle step exception
   */
  public RowMetaInterface getThisStepFields( StepMeta stepMeta, StepMeta nextStep, RowMetaInterface row,
                                             ProgressMonitorListener monitor ) throws HopStepException {
    // Then this one.
    if ( log.isDebug() ) {
      log.logDebug( BaseMessages
        .getString( PKG, "PipelineMeta.Log.GettingFieldsFromStep", stepMeta.getName(), stepMeta.getStepID() ) );
    }
    String name = stepMeta.getName();

    if ( monitor != null ) {
      monitor.subTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.GettingFieldsFromStepTask.Title", name ) );
    }

    StepMetaInterface stepint = stepMeta.getStepMetaInterface();
    RowMetaInterface[] inform = null;
    StepMeta[] lu = getInfoStep( stepMeta );
    if ( Utils.isEmpty( lu ) ) {
      inform = new RowMetaInterface[] { stepint.getTableFields(), };
    } else {
      inform = new RowMetaInterface[ lu.length ];
      for ( int i = 0; i < lu.length; i++ ) {
        inform[ i ] = getStepFields( lu[ i ] );
      }
    }

    setMetaStoreOnMappingSteps();

    // Go get the fields...
    //
    RowMetaInterface before = row.clone();
    RowMetaInterface[] clonedInfo = cloneRowMetaInterfaces( inform );
    if ( !isSomethingDifferentInRow( before, row ) ) {
      stepint.getFields( before, name, clonedInfo, nextStep, this, metaStore );
      // pass the clone object to prevent from spoiling data by other steps
      row = before;
    }

    return row;
  }

  private boolean isSomethingDifferentInRow( RowMetaInterface before, RowMetaInterface after ) {
    if ( before.size() != after.size() ) {
      return true;
    }
    for ( int i = 0; i < before.size(); i++ ) {
      ValueMetaInterface beforeValueMeta = before.getValueMeta( i );
      ValueMetaInterface afterValueMeta = after.getValueMeta( i );
      if ( stringsDifferent( beforeValueMeta.getName(), afterValueMeta.getName() ) ) {
        return true;
      }
      if ( beforeValueMeta.getType() != afterValueMeta.getType() ) {
        return true;
      }
      if ( beforeValueMeta.getLength() != afterValueMeta.getLength() ) {
        return true;
      }
      if ( beforeValueMeta.getPrecision() != afterValueMeta.getPrecision() ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getOrigin(), afterValueMeta.getOrigin() ) ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getComments(), afterValueMeta.getComments() ) ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getConversionMask(), afterValueMeta.getConversionMask() ) ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getStringEncoding(), afterValueMeta.getStringEncoding() ) ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getDecimalSymbol(), afterValueMeta.getDecimalSymbol() ) ) {
        return true;
      }
      if ( stringsDifferent( beforeValueMeta.getGroupingSymbol(), afterValueMeta.getGroupingSymbol() ) ) {
        return true;
      }
    }
    return false;
  }

  private boolean stringsDifferent( String one, String two ) {
    if ( one == null && two == null ) {
      return false;
    }
    if ( one == null && two != null ) {
      return true;
    }
    if ( one != null && two == null ) {
      return true;
    }
    return !one.equals( two );
  }

  /**
   * Set the MetaStore on the Mapping step. That way the mapping step can determine the output fields for
   * metastore referencing mappings... This is the exception to the rule so we don't pass this through the getFields()
   * method. TODO: figure out a way to make this more generic.
   */
  private void setMetaStoreOnMappingSteps() {

    for ( StepMeta step : steps ) {
      if ( step.getStepMetaInterface() instanceof MappingMeta ) {
        ( (MappingMeta) step.getStepMetaInterface() ).setMetaStore( metaStore );
      }
      if ( step.getStepMetaInterface() instanceof SingleThreaderMeta ) {
        ( (SingleThreaderMeta) step.getStepMetaInterface() ).setMetaStore( metaStore );
      }
      if ( step.getStepMetaInterface() instanceof JobExecutorMeta ) {
        ( (JobExecutorMeta) step.getStepMetaInterface() ).setMetaStore( metaStore );
      }
      if ( step.getStepMetaInterface() instanceof PipelineExecutorMeta ) {
        ( (PipelineExecutorMeta) step.getStepMetaInterface() ).setMetaStore( metaStore );
      }
    }
  }

  /**
   * Checks if the pipeline is using the specified partition schema.
   *
   * @param partitionSchema the partition schema
   * @return true if the pipeline is using the partition schema, false otherwise
   */
  public boolean isUsingPartitionSchema( PartitionSchema partitionSchema ) {
    // Loop over all steps and see if the partition schema is used.
    for ( int i = 0; i < nrSteps(); i++ ) {
      StepPartitioningMeta stepPartitioningMeta = getStep( i ).getStepPartitioningMeta();
      if ( stepPartitioningMeta != null ) {
        PartitionSchema check = stepPartitioningMeta.getPartitionSchema();
        if ( check != null && check.equals( partitionSchema ) ) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Finds the location (index) of the specified hop.
   *
   * @param hi The hop queried
   * @return The location of the hop, or -1 if nothing was found.
   */
  public int indexOfPipelineHop( PipelineHopMeta hi ) {
    return hops.indexOf( hi );
  }

  /**
   * Finds the location (index) of the specified step.
   *
   * @param stepMeta The step queried
   * @return The location of the step, or -1 if nothing was found.
   */
  public int indexOfStep( StepMeta stepMeta ) {
    return steps.indexOf( stepMeta );
  }

  /**
   * Gets the XML representation of this pipeline.
   *
   * @return the XML representation of this pipeline
   * @throws HopException if any errors occur during generation of the XML
   * @see org.apache.hop.core.xml.XMLInterface#getXML()
   */
  @Override
  public String getXML() throws HopException {
    return getXML( true, true, true, true, true, true );
  }

  /**
   * Gets the XML representation of this pipeline, including or excluding step, database, slave server, cluster,
   * or partition information as specified by the parameters
   *
   * @param includeSteps           whether to include step data
   * @param includeNamedParameters whether to include named parameters data
   * @param includeLog             whether to include log data
   * @param includeDependencies    whether to include dependencies data
   * @param includeNotePads        whether to include notepads data
   * @param includeAttributeGroups whether to include attributes map data
   * @return the XML representation of this pipeline
   * @throws HopException if any errors occur during generation of the XML
   */
  public String getXML( boolean includeSteps,
                        boolean includeNamedParameters, boolean includeLog, boolean includeDependencies,
                        boolean includeNotePads, boolean includeAttributeGroups ) throws HopException {

    Props props = null;
    if ( Props.isInitialized() ) {
      props = Props.getInstance();
    }

    StringBuilder retval = new StringBuilder( 800 );

    retval.append( XMLHandler.openTag( XML_TAG ) ).append( Const.CR );

    retval.append( "  " ).append( XMLHandler.openTag( XML_TAG_INFO ) ).append( Const.CR );

    retval.append( "    " ).append( XMLHandler.addTagValue( "name", name ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "description", description ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "extended_description", extendedDescription ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "pipeline_version", pipelineVersion ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "pipeline_type", pipelineType.getCode() ) );

    if ( pipelineStatus >= 0 ) {
      retval.append( "    " ).append( XMLHandler.addTagValue( "pipeline_status", pipelineStatus ) );
    }

    if ( includeNamedParameters ) {
      retval.append( "    " ).append( XMLHandler.openTag( XML_TAG_PARAMETERS ) ).append( Const.CR );
      String[] parameters = listParameters();
      for ( int idx = 0; idx < parameters.length; idx++ ) {
        retval.append( "      " ).append( XMLHandler.openTag( "parameter" ) ).append( Const.CR );
        retval.append( "        " ).append( XMLHandler.addTagValue( "name", parameters[ idx ] ) );
        retval.append( "        " )
          .append( XMLHandler.addTagValue( "default_value", getParameterDefault( parameters[ idx ] ) ) );
        retval.append( "        " )
          .append( XMLHandler.addTagValue( "description", getParameterDescription( parameters[ idx ] ) ) );
        retval.append( "      " ).append( XMLHandler.closeTag( "parameter" ) ).append( Const.CR );
      }
      retval.append( "    " ).append( XMLHandler.closeTag( XML_TAG_PARAMETERS ) ).append( Const.CR );
    }

    if ( includeLog ) {
      retval.append( "    " ).append( XMLHandler.openTag( "log" ) ).append( Const.CR );

      // Add the metadata for the various logging tables
      //
      retval.append( pipelineLogTable.getXML() );
      retval.append( performanceLogTable.getXML() );
      retval.append( channelLogTable.getXML() );
      retval.append( stepLogTable.getXML() );
      retval.append( metricsLogTable.getXML() );

      retval.append( "    " ).append( XMLHandler.closeTag( "log" ) ).append( Const.CR );
    }

    retval.append( "    " ).append( XMLHandler.openTag( "maxdate" ) ).append( Const.CR );
    retval.append( "      " )
      .append( XMLHandler.addTagValue( "connection", maxDateConnection == null ? "" : maxDateConnection.getName() ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "table", maxDateTable ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "field", maxDateField ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "offset", maxDateOffset ) );
    retval.append( "      " ).append( XMLHandler.addTagValue( "maxdiff", maxDateDifference ) );
    retval.append( "    " ).append( XMLHandler.closeTag( "maxdate" ) ).append( Const.CR );

    retval.append( "    " ).append( XMLHandler.addTagValue( "size_rowset", sizeRowset ) );

    retval.append( "    " ).append( XMLHandler.addTagValue( "sleep_time_empty", sleepTimeEmpty ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "sleep_time_full", sleepTimeFull ) );

    retval.append( "    " ).append( XMLHandler.addTagValue( "unique_connections", usingUniqueConnections ) );

    retval.append( "    " ).append( XMLHandler.addTagValue( "using_thread_priorities", usingThreadPriorityManagment ) );

    // Performance monitoring
    //
    retval.append( "    " )
      .append( XMLHandler.addTagValue( "capture_step_performance", capturingStepPerformanceSnapShots ) );
    retval.append( "    " )
      .append( XMLHandler.addTagValue( "step_performance_capturing_delay", stepPerformanceCapturingDelay ) );
    retval.append( "    " )
      .append( XMLHandler.addTagValue( "step_performance_capturing_size_limit", stepPerformanceCapturingSizeLimit ) );

    if ( includeDependencies ) {
      retval.append( "    " ).append( XMLHandler.openTag( XML_TAG_DEPENDENCIES ) ).append( Const.CR );
      for ( int i = 0; i < nrDependencies(); i++ ) {
        PipelineDependency td = getDependency( i );
        retval.append( td.getXML() );
      }
      retval.append( "    " ).append( XMLHandler.closeTag( XML_TAG_DEPENDENCIES ) ).append( Const.CR );
    }

    retval.append( "    " ).append( XMLHandler.addTagValue( "created_user", createdUser ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "created_date", XMLHandler.date2string( createdDate ) ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "modified_user", modifiedUser ) );
    retval.append( "    " ).append( XMLHandler.addTagValue( "modified_date", XMLHandler.date2string( modifiedDate ) ) );

    try {
      retval.append( "    " ).append( XMLHandler.addTagValue( "key_for_session_key", keyForSessionKey ) );
    } catch ( Exception ex ) {
      log.logError( "Unable to decode key", ex );
    }
    retval.append( "    " ).append( XMLHandler.addTagValue( "is_key_private", isKeyPrivate ) );

    retval.append( "  " ).append( XMLHandler.closeTag( XML_TAG_INFO ) ).append( Const.CR );

    if ( includeNotePads ) {
      retval.append( "  " ).append( XMLHandler.openTag( XML_TAG_NOTEPADS ) ).append( Const.CR );
      if ( notes != null ) {
        for ( int i = 0; i < nrNotes(); i++ ) {
          NotePadMeta ni = getNote( i );
          retval.append( ni.getXML() );
        }
      }
      retval.append( "  " ).append( XMLHandler.closeTag( XML_TAG_NOTEPADS ) ).append( Const.CR );
    }

    if ( includeSteps ) {
      retval.append( "  " ).append( XMLHandler.openTag( XML_TAG_ORDER ) ).append( Const.CR );
      for ( int i = 0; i < nrPipelineHops(); i++ ) {
        PipelineHopMeta pipelineHopMeta = getPipelineHop( i );
        retval.append( pipelineHopMeta.getXML() );
      }
      retval.append( "  " ).append( XMLHandler.closeTag( XML_TAG_ORDER ) ).append( Const.CR );

      /* The steps... */
      for ( int i = 0; i < nrSteps(); i++ ) {
        StepMeta stepMeta = getStep( i );
        retval.append( stepMeta.getXML() );
      }

      /* The error handling metadata on the steps */
      retval.append( "  " ).append( XMLHandler.openTag( XML_TAG_STEP_ERROR_HANDLING ) ).append( Const.CR );
      for ( int i = 0; i < nrSteps(); i++ ) {
        StepMeta stepMeta = getStep( i );

        if ( stepMeta.getStepErrorMeta() != null ) {
          retval.append( stepMeta.getStepErrorMeta().getXML() );
        }
      }
      retval.append( "  " ).append( XMLHandler.closeTag( XML_TAG_STEP_ERROR_HANDLING ) ).append( Const.CR );
    }

    // Also store the attribute groups
    //
    if ( includeAttributeGroups ) {
      retval.append( AttributesUtil.getAttributesXml( attributesMap ) );
    }
    retval.append( XMLHandler.closeTag( XML_TAG ) ).append( Const.CR );

    return XMLFormatter.format( retval.toString() );
  }

  /**
   * Parses a file containing the XML that describes the pipeline.
   *
   * @param fname                The filename
   * @param metaStore            the metadata store to reference (or null if there is none)
   * @param setInternalVariables true if you want to set the internal variables based on this pipeline information
   * @param parentVariableSpace  the parent variable space to use during PipelineMeta construction
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public PipelineMeta( String fname, IMetaStore metaStore, boolean setInternalVariables, VariableSpace parentVariableSpace )
    throws HopXMLException, HopMissingPluginsException {
    // if fname is not provided, there's not much we can do, throw an exception
    if ( StringUtils.isBlank( fname ) ) {
      throw new HopXMLException( BaseMessages.getString( PKG, "PipelineMeta.Exception.MissingXMLFilePath" ) );
    }

    if ( metaStore == null ) {
      throw new HopXMLException( "MetaStore references can't be null. When loading a pipeline Hop needs to be able to reference external metadata objects" );
    }

    this.metaStore = metaStore;

    // OK, try to load using the VFS stuff...
    Document doc = null;
    try {
      final FileObject pipelineFile = HopVFS.getFileObject( fname, parentVariableSpace );
      if ( !pipelineFile.exists() ) {
        throw new HopXMLException( BaseMessages.getString( PKG, "PipelineMeta.Exception.InvalidXMLPath", fname ) );
      }
      doc = XMLHandler.loadXMLFile( pipelineFile );
    } catch ( HopXMLException ke ) {
      // if we have a HopXMLException, simply re-throw it
      throw ke;
    } catch ( HopException | FileSystemException e ) {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "PipelineMeta.Exception.ErrorOpeningOrValidatingTheXMLFile", fname ), e );
    }

    if ( doc != null ) {
      // Root node:
      Node pipelineNode = XMLHandler.getSubNode( doc, XML_TAG );

      if ( pipelineNode == null ) {
        throw new HopXMLException( BaseMessages.getString(
          PKG, "PipelineMeta.Exception.NotValidPipelineXML", fname ) );
      }

      // Load from this node...
      loadXML( pipelineNode, fname, metaStore, setInternalVariables, parentVariableSpace );

    } else {
      throw new HopXMLException( BaseMessages.getString(
        PKG, "PipelineMeta.Exception.ErrorOpeningOrValidatingTheXMLFile", fname ) );
    }
  }

  /**
   * Instantiates a new pipeline meta-data object.
   *
   * @param xmlStream            the XML input stream from which to read the pipeline definition
   * @param setInternalVariables whether to set internal variables as a result of the creation
   * @param parentVariableSpace  the parent variable space
   * @throws HopXMLException            if any errors occur during parsing of the specified stream
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public PipelineMeta( InputStream xmlStream, boolean setInternalVariables, VariableSpace parentVariableSpace )
    throws HopXMLException, HopMissingPluginsException {
    Document doc = XMLHandler.loadXMLFile( xmlStream, null, false, false );
    Node pipelineNode = XMLHandler.getSubNode( doc, XML_TAG );
    loadXML( pipelineNode, setInternalVariables, parentVariableSpace );
  }

  /**
   * Parse a file containing the XML that describes the pipeline.
   *
   * @param pipelineNode The XML node to load from
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public PipelineMeta( Node pipelineNode ) throws HopXMLException, HopMissingPluginsException {
    loadXML( pipelineNode, false );
  }

  /**
   * Parses an XML DOM (starting at the specified Node) that describes the pipeline.
   *
   * @param pipelineNode            The XML node to load from
   * @param setInternalVariables true if you want to set the internal variables based on this pipeline information
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public void loadXML( Node pipelineNode, boolean setInternalVariables ) throws HopXMLException,
    HopMissingPluginsException {
    loadXML( pipelineNode, setInternalVariables, null );
  }

  /**
   * Parses an XML DOM (starting at the specified Node) that describes the pipeline.
   *
   * @param pipelineNode            The XML node to load from
   * @param setInternalVariables true if you want to set the internal variables based on this pipeline information
   * @param parentVariableSpace  the parent variable space to use during PipelineMeta construction
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public void loadXML( Node pipelineNode, boolean setInternalVariables, VariableSpace parentVariableSpace ) throws HopXMLException, HopMissingPluginsException {
    loadXML( pipelineNode, null, setInternalVariables, parentVariableSpace );
  }

  /**
   * Parses an XML DOM (starting at the specified Node) that describes the pipeline.
   *
   * @param pipelineNode            The XML node to load from
   * @param fname                The filename
   * @param setInternalVariables true if you want to set the internal variables based on this pipeline information
   * @param parentVariableSpace  the parent variable space to use during PipelineMeta construction
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public void loadXML( Node pipelineNode, String fname, boolean setInternalVariables, VariableSpace parentVariableSpace )
    throws HopXMLException, HopMissingPluginsException {
    loadXML( pipelineNode, fname, null, setInternalVariables, parentVariableSpace );
  }

  /**
   * Parses an XML DOM (starting at the specified Node) that describes the pipeline.
   *
   * @param pipelineNode            The XML node to load from
   * @param fname                The filename
   * @param setInternalVariables true if you want to set the internal variables based on this pipeline information
   * @param parentVariableSpace  the parent variable space to use during PipelineMeta construction
   * @throws HopXMLException            if any errors occur during parsing of the specified file
   * @throws HopMissingPluginsException in case missing plugins were found (details are in the exception in that case)
   */
  public void loadXML( Node pipelineNode, String fname, IMetaStore metaStore, boolean setInternalVariables, VariableSpace parentVariableSpace )
    throws HopXMLException, HopMissingPluginsException {

    HopMissingPluginsException
      missingPluginsException =
      new HopMissingPluginsException(
        BaseMessages.getString( PKG, "PipelineMeta.MissingPluginsFoundWhileLoadingPipeline.Exception" ) );

    this.metaStore = metaStore; // Remember this as the primary meta store.

    try {

      Props props = null;
      if ( Props.isInitialized() ) {
        props = Props.getInstance();
      }

      initializeVariablesFrom( parentVariableSpace );

      try {
        // Clear the pipeline
        clear();

        // Set the filename here so it can be used in variables for ALL aspects of the pipeline FIX: PDI-8890
        //
        setFilename( fname );

        // Read the notes...
        Node notepadsnode = XMLHandler.getSubNode( pipelineNode, XML_TAG_NOTEPADS );
        int nrnotes = XMLHandler.countNodes( notepadsnode, NotePadMeta.XML_TAG );
        for ( int i = 0; i < nrnotes; i++ ) {
          Node notepadnode = XMLHandler.getSubNodeByNr( notepadsnode, NotePadMeta.XML_TAG, i );
          NotePadMeta ni = new NotePadMeta( notepadnode );
          notes.add( ni );
        }

        // Handle Steps
        int s = XMLHandler.countNodes( pipelineNode, StepMeta.XML_TAG );

        if ( log.isDebug() ) {
          log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.ReadingSteps" ) + s + " steps..." );
        }
        for ( int i = 0; i < s; i++ ) {
          Node stepnode = XMLHandler.getSubNodeByNr( pipelineNode, StepMeta.XML_TAG, i );

          if ( log.isDebug() ) {
            log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.LookingAtStep" ) + i );
          }

          StepMeta stepMeta = new StepMeta( stepnode, metaStore );
          stepMeta.setParentPipelineMeta( this ); // for tracing, retain hierarchy

          if ( stepMeta.isMissing() ) {
            addMissingPipeline( (Missing) stepMeta.getStepMetaInterface() );
          }
          addOrReplaceStep( stepMeta );
        }

        // Read the error handling code of the steps...
        //
        Node errorHandlingNode = XMLHandler.getSubNode( pipelineNode, XML_TAG_STEP_ERROR_HANDLING );
        int nrErrorHandlers = XMLHandler.countNodes( errorHandlingNode, StepErrorMeta.XML_ERROR_TAG );
        for ( int i = 0; i < nrErrorHandlers; i++ ) {
          Node stepErrorMetaNode = XMLHandler.getSubNodeByNr( errorHandlingNode, StepErrorMeta.XML_ERROR_TAG, i );
          StepErrorMeta stepErrorMeta = new StepErrorMeta( this, stepErrorMetaNode, steps );
          if ( stepErrorMeta.getSourceStep() != null ) {
            stepErrorMeta.getSourceStep().setStepErrorMeta( stepErrorMeta ); // a bit of a trick, I know.
          }
        }

        // Have all StreamValueLookups, etc. reference the correct source steps...
        //
        for ( int i = 0; i < nrSteps(); i++ ) {
          StepMeta stepMeta = getStep( i );
          StepMetaInterface sii = stepMeta.getStepMetaInterface();
          if ( sii != null ) {
            sii.searchInfoAndTargetSteps( steps );
          }
        }

        // Handle Hops
        //
        Node ordernode = XMLHandler.getSubNode( pipelineNode, XML_TAG_ORDER );
        int n = XMLHandler.countNodes( ordernode, PipelineHopMeta.XML_HOP_TAG );

        if ( log.isDebug() ) {
          log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.WeHaveHops" ) + n + " hops..." );
        }
        for ( int i = 0; i < n; i++ ) {
          if ( log.isDebug() ) {
            log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.LookingAtHop" ) + i );
          }
          Node hopnode = XMLHandler.getSubNodeByNr( ordernode, PipelineHopMeta.XML_HOP_TAG, i );

          PipelineHopMeta hopinf = new PipelineHopMeta( hopnode, steps );
          hopinf.setErrorHop( isErrorNode( errorHandlingNode, hopnode ) );
          addPipelineHop( hopinf );
        }

        //
        // get pipeline info:
        //
        Node infonode = XMLHandler.getSubNode( pipelineNode, XML_TAG_INFO );

        // Name
        //
        setName( XMLHandler.getTagValue( infonode, "name" ) );

        // description
        //
        description = XMLHandler.getTagValue( infonode, "description" );

        // extended description
        //
        extendedDescription = XMLHandler.getTagValue( infonode, "extended_description" );

        // pipeline version
        //
        pipelineVersion = XMLHandler.getTagValue( infonode, "pipeline_version" );

        // pipeline status
        //
        pipelineStatus = Const.toInt( XMLHandler.getTagValue( infonode, "pipeline_status" ), -1 );

        String pipelineTypeCode = XMLHandler.getTagValue( infonode, "pipeline_type" );
        pipelineType = PipelineType.getPipelineTypeByCode( pipelineTypeCode );

        // Read logging table information
        //
        Node logNode = XMLHandler.getSubNode( infonode, "log" );
        if ( logNode != null ) {

          // Backward compatibility...
          //
          Node pipelineLogNode = XMLHandler.getSubNode( logNode, PipelineLogTable.XML_TAG );
          if ( pipelineLogNode == null ) {
            // Load the XML
            //
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_READ )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "read" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_WRITTEN )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "write" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_INPUT )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "input" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_OUTPUT )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "output" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_UPDATED )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "update" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_REJECTED )
              .setSubject( findStep( XMLHandler.getTagValue( infonode, "log", "rejected" ) ) );

            pipelineLogTable.setConnectionName( XMLHandler.getTagValue( infonode, "log", "connection" ) );
            pipelineLogTable.setSchemaName( XMLHandler.getTagValue( infonode, "log", "schema" ) );
            pipelineLogTable.setTableName( XMLHandler.getTagValue( infonode, "log", "table" ) );
            pipelineLogTable.findField( PipelineLogTable.ID.ID_BATCH )
              .setEnabled( "Y".equalsIgnoreCase( XMLHandler.getTagValue( infonode, "log", "use_batchid" ) ) );
            pipelineLogTable.findField( PipelineLogTable.ID.LOG_FIELD )
              .setEnabled( "Y".equalsIgnoreCase( XMLHandler.getTagValue( infonode, "log", "USE_LOGFIELD" ) ) );
            pipelineLogTable.setLogSizeLimit( XMLHandler.getTagValue( infonode, "log", "size_limit_lines" ) );
            pipelineLogTable.setLogInterval( XMLHandler.getTagValue( infonode, "log", "interval" ) );
            pipelineLogTable.findField( PipelineLogTable.ID.CHANNEL_ID ).setEnabled( false );
            pipelineLogTable.findField( PipelineLogTable.ID.LINES_REJECTED ).setEnabled( false );
            performanceLogTable.setConnectionName( pipelineLogTable.getConnectionName() );
            performanceLogTable.setTableName( XMLHandler.getTagValue( infonode, "log", "step_performance_table" ) );
          } else {
            pipelineLogTable.loadXML( pipelineLogNode, steps );
          }
          Node perfLogNode = XMLHandler.getSubNode( logNode, PerformanceLogTable.XML_TAG );
          if ( perfLogNode != null ) {
            performanceLogTable.loadXML( perfLogNode, steps );
          }
          Node channelLogNode = XMLHandler.getSubNode( logNode, ChannelLogTable.XML_TAG );
          if ( channelLogNode != null ) {
            channelLogTable.loadXML( channelLogNode, steps );
          }
          Node stepLogNode = XMLHandler.getSubNode( logNode, StepLogTable.XML_TAG );
          if ( stepLogNode != null ) {
            stepLogTable.loadXML( stepLogNode, steps );
          }
          Node metricsLogNode = XMLHandler.getSubNode( logNode, MetricsLogTable.XML_TAG );
          if ( metricsLogNode != null ) {
            metricsLogTable.loadXML( metricsLogNode, steps );
          }
        }

        // Maxdate range options...
        String maxdatcon = XMLHandler.getTagValue( infonode, "maxdate", "connection" );
        maxDateConnection = findDatabase( maxdatcon );
        maxDateTable = XMLHandler.getTagValue( infonode, "maxdate", "table" );
        maxDateField = XMLHandler.getTagValue( infonode, "maxdate", "field" );
        String offset = XMLHandler.getTagValue( infonode, "maxdate", "offset" );
        maxDateOffset = Const.toDouble( offset, 0.0 );
        String mdiff = XMLHandler.getTagValue( infonode, "maxdate", "maxdiff" );
        maxDateDifference = Const.toDouble( mdiff, 0.0 );

        // Check the dependencies as far as dates are concerned...
        // We calculate BEFORE we run the MAX of these dates
        // If the date is larger then enddate, startdate is set to MIN_DATE
        //
        Node depsNode = XMLHandler.getSubNode( infonode, XML_TAG_DEPENDENCIES );
        int nrDeps = XMLHandler.countNodes( depsNode, PipelineDependency.XML_TAG );

        for ( int i = 0; i < nrDeps; i++ ) {
          Node depNode = XMLHandler.getSubNodeByNr( depsNode, PipelineDependency.XML_TAG, i );

          PipelineDependency pipelineDependency = new PipelineDependency( depNode, getDatabases() );
          if ( pipelineDependency.getDatabase() != null && pipelineDependency.getFieldname() != null ) {
            addDependency( pipelineDependency );
          }
        }

        // Read the named parameters.
        Node paramsNode = XMLHandler.getSubNode( infonode, XML_TAG_PARAMETERS );
        int nrParams = XMLHandler.countNodes( paramsNode, "parameter" );

        for ( int i = 0; i < nrParams; i++ ) {
          Node paramNode = XMLHandler.getSubNodeByNr( paramsNode, "parameter", i );

          String paramName = XMLHandler.getTagValue( paramNode, "name" );
          String defaultValue = XMLHandler.getTagValue( paramNode, "default_value" );
          String descr = XMLHandler.getTagValue( paramNode, "description" );

          addParameterDefinition( paramName, defaultValue, descr );
        }

        // Read the partitioning schemas
        //
        Node partSchemasNode = XMLHandler.getSubNode( infonode, XML_TAG_PARTITIONSCHEMAS );
        int nrPartSchemas = XMLHandler.countNodes( partSchemasNode, PartitionSchema.XML_TAG );
        for ( int i = 0; i < nrPartSchemas; i++ ) {
          Node partSchemaNode = XMLHandler.getSubNodeByNr( partSchemasNode, PartitionSchema.XML_TAG, i );
          PartitionSchema partitionSchema = new PartitionSchema( partSchemaNode );

          partitionSchemas.add( partitionSchema );
        }

        String srowset = XMLHandler.getTagValue( infonode, "size_rowset" );
        sizeRowset = Const.toInt( srowset, Const.ROWS_IN_ROWSET );
        sleepTimeEmpty =
          Const.toInt( XMLHandler.getTagValue( infonode, "sleep_time_empty" ), Const.TIMEOUT_GET_MILLIS );
        sleepTimeFull = Const.toInt( XMLHandler.getTagValue( infonode, "sleep_time_full" ), Const.TIMEOUT_PUT_MILLIS );
        usingUniqueConnections = "Y".equalsIgnoreCase( XMLHandler.getTagValue( infonode, "unique_connections" ) );

        usingThreadPriorityManagment = !"N".equalsIgnoreCase( XMLHandler.getTagValue( infonode, "using_thread_priorities" ) );

        // Performance monitoring for steps...
        //
        capturingStepPerformanceSnapShots =
          "Y".equalsIgnoreCase( XMLHandler.getTagValue( infonode, "capture_step_performance" ) );
        stepPerformanceCapturingDelay =
          Const.toLong( XMLHandler.getTagValue( infonode, "step_performance_capturing_delay" ), 1000 );
        stepPerformanceCapturingSizeLimit = XMLHandler.getTagValue( infonode, "step_performance_capturing_size_limit" );

        // Created user/date
        createdUser = XMLHandler.getTagValue( infonode, "created_user" );
        String createDate = XMLHandler.getTagValue( infonode, "created_date" );
        if ( createDate != null ) {
          createdDate = XMLHandler.stringToDate( createDate );
        }

        // Changed user/date
        modifiedUser = XMLHandler.getTagValue( infonode, "modified_user" );
        String modDate = XMLHandler.getTagValue( infonode, "modified_date" );
        if ( modDate != null ) {
          modifiedDate = XMLHandler.stringToDate( modDate );
        }

        if ( log.isDebug() ) {
          log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.NumberOfStepsReaded" ) + nrSteps() );
          log.logDebug( BaseMessages.getString( PKG, "PipelineMeta.Log.NumberOfHopsReaded" ) + nrPipelineHops() );
        }
        sortSteps();

        // Load the attribute groups map
        //
        attributesMap = AttributesUtil.loadAttributes( XMLHandler.getSubNode( pipelineNode, AttributesUtil.XML_TAG ) );

        keyForSessionKey = XMLHandler.stringToBinary( XMLHandler.getTagValue( infonode, "key_for_session_key" ) );
        isKeyPrivate = "Y".equals( XMLHandler.getTagValue( infonode, "is_key_private" ) );

      } catch ( HopXMLException xe ) {
        throw new HopXMLException( BaseMessages.getString( PKG, "PipelineMeta.Exception.ErrorReadingPipeline" ),
          xe );
      } catch ( HopException e ) {
        throw new HopXMLException( e );
      } finally {
        initializeVariablesFrom( null );
        if ( setInternalVariables ) {
          setInternalHopVariables();
        }

        ExtensionPointHandler.callExtensionPoint( log, HopExtensionPoint.PipelineMetaLoaded.id, this );
      }
    } catch ( Exception e ) {
      // See if we have missing plugins to report, those take precedence!
      //
      if ( !missingPluginsException.getMissingPluginDetailsList().isEmpty() ) {
        throw missingPluginsException;
      } else {
        throw new HopXMLException( BaseMessages.getString( PKG, "PipelineMeta.Exception.ErrorReadingPipeline" ),
          e );
      }
    } finally {
      if ( !missingPluginsException.getMissingPluginDetailsList().isEmpty() ) {
        throw missingPluginsException;
      }
    }
    clearChanged();
  }

  public byte[] getKey() {
    return keyForSessionKey;
  }

  public void setKey( byte[] key ) {
    this.keyForSessionKey = key;
  }

  public boolean isPrivateKey() {
    return isKeyPrivate;
  }

  public void setPrivateKey( boolean privateKey ) {
    this.isKeyPrivate = privateKey;
  }

  /**
   * Gets a List of all the steps that are used in at least one active hop. These steps will be used to execute the
   * pipeline. The others will not be executed.<br/>
   * Update 3.0 : we also add those steps that are not linked to another hop, but have at least one remote input or
   * output step defined.
   *
   * @param all true if you want to get ALL the steps from the pipeline, false otherwise
   * @return A List of steps
   */
  public List<StepMeta> getPipelineHopSteps( boolean all ) {
    List<StepMeta> st = new ArrayList<>();
    int idx;

    for ( int x = 0; x < nrPipelineHops(); x++ ) {
      PipelineHopMeta hi = getPipelineHop( x );
      if ( hi.isEnabled() || all ) {
        idx = st.indexOf( hi.getFromStep() ); // FROM
        if ( idx < 0 ) {
          st.add( hi.getFromStep() );
        }

        idx = st.indexOf( hi.getToStep() ); // TO
        if ( idx < 0 ) {
          st.add( hi.getToStep() );
        }
      }
    }

    // Also, add the steps that need to be painted, but are not part of a hop
    for ( int x = 0; x < nrSteps(); x++ ) {
      StepMeta stepMeta = getStep( x );
      if ( !isStepUsedInPipelineHops( stepMeta ) ) {
        st.add( stepMeta );
      }
    }

    return st;
  }

  /**
   * Checks if a step has been used in a hop or not.
   *
   * @param stepMeta The step queried.
   * @return true if a step is used in a hop (active or not), false otherwise
   */
  public boolean isStepUsedInPipelineHops( StepMeta stepMeta ) {
    PipelineHopMeta fr = findPipelineHopFrom( stepMeta );
    PipelineHopMeta to = findPipelineHopTo( stepMeta );
    return fr != null || to != null;
  }

  /**
   * Checks if any selected step has been used in a hop or not.
   *
   * @return true if a step is used in a hop (active or not), false otherwise
   */
  public boolean isAnySelectedStepUsedInPipelineHops() {
    List<StepMeta> selectedSteps = getSelectedSteps();
    int i = 0;
    while ( i < selectedSteps.size() ) {
      StepMeta stepMeta = selectedSteps.get( i );
      if ( isStepUsedInPipelineHops( stepMeta ) ) {
        return true;
      }
      i++;
    }
    return false;
  }

  /**
   * Clears the different changed flags of the pipeline.
   */
  @Override
  public void clearChanged() {
    changed_steps = false;
    changed_hops = false;

    for ( int i = 0; i < nrSteps(); i++ ) {
      getStep( i ).setChanged( false );
      if ( getStep( i ).getStepPartitioningMeta() != null ) {
        getStep( i ).getStepPartitioningMeta().hasChanged( false );
      }
    }
    for ( int i = 0; i < nrPipelineHops(); i++ ) {
      getPipelineHop( i ).setChanged( false );
    }
    for ( int i = 0; i < partitionSchemas.size(); i++ ) {
      partitionSchemas.get( i ).setChanged( false );
    }

    super.clearChanged();
  }

  /**
   * Checks whether or not the steps have changed.
   *
   * @return true if the steps have been changed, false otherwise
   */
  public boolean haveStepsChanged() {
    if ( changed_steps ) {
      return true;
    }

    for ( int i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      if ( stepMeta.hasChanged() ) {
        return true;
      }
      if ( stepMeta.getStepPartitioningMeta() != null && stepMeta.getStepPartitioningMeta().hasChanged() ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether or not any of the hops have been changed.
   *
   * @return true if a hop has been changed, false otherwise
   */
  public boolean haveHopsChanged() {
    if ( changed_hops ) {
      return true;
    }

    for ( int i = 0; i < nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = getPipelineHop( i );
      if ( hi.hasChanged() ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether or not any of the partitioning schemas have been changed.
   *
   * @return true if the partitioning schemas have been changed, false otherwise
   */
  public boolean havePartitionSchemasChanged() {
    for ( int i = 0; i < partitionSchemas.size(); i++ ) {
      PartitionSchema ps = partitionSchemas.get( i );
      if ( ps.hasChanged() ) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks whether or not the pipeline has changed.
   *
   * @return true if the pipeline has changed, false otherwise
   */
  @Override
  public boolean hasChanged() {
    if ( super.hasChanged() ) {
      return true;
    }
    if ( haveStepsChanged() ) {
      return true;
    }
    if ( haveHopsChanged() ) {
      return true;
    }
    return havePartitionSchemasChanged();
  }

  private boolean isErrorNode( Node errorHandingNode, Node checkNode ) {
    if ( errorHandingNode != null ) {
      NodeList errors = errorHandingNode.getChildNodes();

      Node nodeHopFrom = XMLHandler.getSubNode( checkNode, PipelineHopMeta.XML_FROM_TAG );
      Node nodeHopTo = XMLHandler.getSubNode( checkNode, PipelineHopMeta.XML_TO_TAG );

      int i = 0;
      while ( i < errors.getLength() ) {

        Node errorNode = errors.item( i );

        if ( !StepErrorMeta.XML_ERROR_TAG.equals( errorNode.getNodeName() ) ) {
          i++;
          continue;
        }

        Node errorSourceNode = XMLHandler.getSubNode( errorNode, StepErrorMeta.XML_SOURCE_STEP_TAG );
        Node errorTagetNode = XMLHandler.getSubNode( errorNode, StepErrorMeta.XML_TARGET_STEP_TAG );

        String sourceContent = errorSourceNode.getTextContent().trim();
        String tagetContent = errorTagetNode.getTextContent().trim();

        if ( sourceContent.equals( nodeHopFrom.getTextContent().trim() )
          && tagetContent.equals( nodeHopTo.getTextContent().trim() ) ) {
          return true;
        }
        i++;
      }
    }
    return false;
  }

  /**
   * See if there are any loops in the pipeline, starting at the indicated step. This works by looking at all the
   * previous steps. If you keep going backward and find the step, there is a loop. Both the informational and the
   * normal steps need to be checked for loops!
   *
   * @param stepMeta The step position to start looking
   * @return true if a loop has been found, false if no loop is found.
   */
  public boolean hasLoop( StepMeta stepMeta ) {
    clearLoopCache();
    return hasLoop( stepMeta, null );
  }

  /**
   * @deprecated use {@link #hasLoop(StepMeta, StepMeta)}}
   */
  @Deprecated
  public boolean hasLoop( StepMeta stepMeta, StepMeta lookup, boolean info ) {
    return hasLoop( stepMeta, lookup, new HashSet<StepMeta>() );
  }

  /**
   * Checks for loop.
   *
   * @param stepMeta the stepmeta
   * @param lookup   the lookup
   * @return true, if successful
   */

  public boolean hasLoop( StepMeta stepMeta, StepMeta lookup ) {
    return hasLoop( stepMeta, lookup, new HashSet<StepMeta>() );
  }

  /**
   * See if there are any loops in the pipeline, starting at the indicated step. This works by looking at all the
   * previous steps. If you keep going backward and find the original step again, there is a loop.
   *
   * @param stepMeta       The step position to start looking
   * @param lookup         The original step when wandering around the pipeline.
   * @param checkedEntries Already checked entries
   * @return true if a loop has been found, false if no loop is found.
   */
  private boolean hasLoop( StepMeta stepMeta, StepMeta lookup, HashSet<StepMeta> checkedEntries ) {
    String cacheKey =
      stepMeta.getName() + " - " + ( lookup != null ? lookup.getName() : "" );

    Boolean hasLoop = loopCache.get( cacheKey );

    if ( hasLoop != null ) {
      return hasLoop;
    }

    hasLoop = false;

    checkedEntries.add( stepMeta );

    List<StepMeta> prevSteps = findPreviousSteps( stepMeta, true );
    int nr = prevSteps.size();
    for ( int i = 0; i < nr; i++ ) {
      StepMeta prevStepMeta = prevSteps.get( i );
      if ( prevStepMeta != null && ( prevStepMeta.equals( lookup )
        || ( !checkedEntries.contains( prevStepMeta ) && hasLoop( prevStepMeta, lookup == null ? stepMeta : lookup, checkedEntries ) ) ) ) {
        hasLoop = true;
        break;
      }
    }

    loopCache.put( cacheKey, hasLoop );
    return hasLoop;
  }

  /**
   * Mark all steps in the pipeline as selected.
   */
  public void selectAll() {
    int i;
    for ( i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      stepMeta.setSelected( true );
    }
    for ( i = 0; i < nrNotes(); i++ ) {
      NotePadMeta ni = getNote( i );
      ni.setSelected( true );
    }

    setChanged();
    notifyObservers( "refreshGraph" );
  }

  /**
   * Clear the selection of all steps.
   */
  public void unselectAll() {
    int i;
    for ( i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      stepMeta.setSelected( false );
    }
    for ( i = 0; i < nrNotes(); i++ ) {
      NotePadMeta ni = getNote( i );
      ni.setSelected( false );
    }
  }

  /**
   * Get an array of all the selected step locations.
   *
   * @return The selected step locations.
   */
  public Point[] getSelectedStepLocations() {
    List<Point> points = new ArrayList<>();

    for ( StepMeta stepMeta : getSelectedSteps() ) {
      Point p = stepMeta.getLocation();
      points.add( new Point( p.x, p.y ) ); // explicit copy of location
    }

    return points.toArray( new Point[ points.size() ] );
  }

  /**
   * Get an array of all the selected note locations.
   *
   * @return The selected note locations.
   */
  public Point[] getSelectedNoteLocations() {
    List<Point> points = new ArrayList<>();

    for ( NotePadMeta ni : getSelectedNotes() ) {
      Point p = ni.getLocation();
      points.add( new Point( p.x, p.y ) ); // explicit copy of location
    }

    return points.toArray( new Point[ points.size() ] );
  }

  /**
   * Gets a list of the selected steps.
   *
   * @return A list of all the selected steps.
   */
  public List<StepMeta> getSelectedSteps() {
    List<StepMeta> selection = new ArrayList<>();
    for ( StepMeta stepMeta : steps ) {
      if ( stepMeta.isSelected() ) {
        selection.add( stepMeta );
      }

    }
    return selection;
  }

  /**
   * Gets an array of all the selected step names.
   *
   * @return An array of all the selected step names.
   */
  public String[] getSelectedStepNames() {
    List<StepMeta> selection = getSelectedSteps();
    String[] retval = new String[ selection.size() ];
    for ( int i = 0; i < retval.length; i++ ) {
      StepMeta stepMeta = selection.get( i );
      retval[ i ] = stepMeta.getName();
    }
    return retval;
  }

  /**
   * Gets an array of the locations of an array of steps.
   *
   * @param steps An array of steps
   * @return an array of the locations of an array of steps
   */
  public int[] getStepIndexes( List<StepMeta> steps ) {
    int[] retval = new int[ steps.size() ];

    for ( int i = 0; i < steps.size(); i++ ) {
      retval[ i ] = indexOfStep( steps.get( i ) );
    }

    return retval;
  }

  /**
   * Gets the maximum size of the canvas by calculating the maximum location of a step.
   *
   * @return Maximum coordinate of a step in the pipeline + (100,100) for safety.
   */
  public Point getMaximum() {
    int maxx = 0, maxy = 0;
    for ( int i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      Point loc = stepMeta.getLocation();
      if ( loc.x > maxx ) {
        maxx = loc.x;
      }
      if ( loc.y > maxy ) {
        maxy = loc.y;
      }
    }
    for ( int i = 0; i < nrNotes(); i++ ) {
      NotePadMeta notePadMeta = getNote( i );
      Point loc = notePadMeta.getLocation();
      if ( loc.x + notePadMeta.width > maxx ) {
        maxx = loc.x + notePadMeta.width;
      }
      if ( loc.y + notePadMeta.height > maxy ) {
        maxy = loc.y + notePadMeta.height;
      }
    }

    return new Point( maxx + 100, maxy + 100 );
  }

  /**
   * Gets the minimum point on the canvas of a pipeline.
   *
   * @return Minimum coordinate of a step in the pipeline
   */
  public Point getMinimum() {
    int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE;
    for ( int i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      Point loc = stepMeta.getLocation();
      if ( loc.x < minx ) {
        minx = loc.x;
      }
      if ( loc.y < miny ) {
        miny = loc.y;
      }
    }
    for ( int i = 0; i < nrNotes(); i++ ) {
      NotePadMeta notePadMeta = getNote( i );
      Point loc = notePadMeta.getLocation();
      if ( loc.x < minx ) {
        minx = loc.x;
      }
      if ( loc.y < miny ) {
        miny = loc.y;
      }
    }

    if ( minx > BORDER_INDENT && minx != Integer.MAX_VALUE ) {
      minx -= BORDER_INDENT;
    } else {
      minx = 0;
    }
    if ( miny > BORDER_INDENT && miny != Integer.MAX_VALUE ) {
      miny -= BORDER_INDENT;
    } else {
      miny = 0;
    }

    return new Point( minx, miny );
  }

  /**
   * Gets the names of all the steps.
   *
   * @return An array of step names.
   */
  public String[] getStepNames() {
    String[] retval = new String[ nrSteps() ];

    for ( int i = 0; i < nrSteps(); i++ ) {
      retval[ i ] = getStep( i ).getName();
    }

    return retval;
  }

  /**
   * Gets all the steps as an array.
   *
   * @return An array of all the steps in the pipeline.
   */
  public StepMeta[] getStepsArray() {
    StepMeta[] retval = new StepMeta[ nrSteps() ];

    for ( int i = 0; i < nrSteps(); i++ ) {
      retval[ i ] = getStep( i );
    }

    return retval;
  }

  /**
   * Looks in the pipeline to find a step in a previous location starting somewhere.
   *
   * @param startStep  The starting step
   * @param stepToFind The step to look for backward in the pipeline
   * @return true if we can find the step in an earlier location in the pipeline.
   */
  public boolean findPrevious( StepMeta startStep, StepMeta stepToFind ) {
    String key = startStep.getName() + " - " + stepToFind.getName();
    Boolean result = loopCache.get( key );
    if ( result != null ) {
      return result;
    }

    // Normal steps
    //
    List<StepMeta> previousSteps = findPreviousSteps( startStep, false );
    for ( int i = 0; i < previousSteps.size(); i++ ) {
      StepMeta stepMeta = previousSteps.get( i );
      if ( stepMeta.equals( stepToFind ) ) {
        loopCache.put( key, true );
        return true;
      }

      boolean found = findPrevious( stepMeta, stepToFind ); // Look further back in the tree.
      if ( found ) {
        loopCache.put( key, true );
        return true;
      }
    }

    // Info steps
    List<StepMeta> infoSteps = findPreviousSteps( startStep, true );
    for ( int i = 0; i < infoSteps.size(); i++ ) {
      StepMeta stepMeta = infoSteps.get( i );
      if ( stepMeta.equals( stepToFind ) ) {
        loopCache.put( key, true );
        return true;
      }

      boolean found = findPrevious( stepMeta, stepToFind ); // Look further back in the tree.
      if ( found ) {
        loopCache.put( key, true );
        return true;
      }
    }

    loopCache.put( key, false );
    return false;
  }

  /**
   * Puts the steps in alphabetical order.
   */
  public void sortSteps() {
    try {
      Collections.sort( steps );
    } catch ( Exception e ) {
      log.logError( BaseMessages.getString( PKG, "PipelineMeta.Exception.ErrorOfSortingSteps" ) + e );
      log.logError( Const.getStackTracker( e ) );
    }
  }

  /**
   * Sorts all the hops in the pipeline.
   */
  public void sortHops() {
    Collections.sort( hops );
  }

  /**
   * The previous count.
   */
  private long prevCount;

  /**
   * Puts the steps in a more natural order: from start to finish. For the moment, we ignore splits and joins. Splits
   * and joins can't be listed sequentially in any case!
   *
   * @return a map containing all the previous steps per step
   */
  public Map<StepMeta, Map<StepMeta, Boolean>> sortStepsNatural() {
    long startTime = System.currentTimeMillis();

    prevCount = 0;

    // First create a map where all the previous steps of another step are kept...
    //
    final Map<StepMeta, Map<StepMeta, Boolean>> stepMap = new HashMap<>();

    // Also cache the previous steps
    //
    final Map<StepMeta, List<StepMeta>> previousCache = new HashMap<>();

    // Cache calculation of steps before another
    //
    Map<StepMeta, Map<StepMeta, Boolean>> beforeCache = new HashMap<>();

    for ( StepMeta stepMeta : steps ) {
      // What are the previous steps? (cached version for performance)
      //
      List<StepMeta> prevSteps = previousCache.get( stepMeta );
      if ( prevSteps == null ) {
        prevSteps = findPreviousSteps( stepMeta );
        prevCount++;
        previousCache.put( stepMeta, prevSteps );
      }

      // Now get the previous steps recursively, store them in the step map
      //
      for ( StepMeta prev : prevSteps ) {
        Map<StepMeta, Boolean> beforePrevMap = updateFillStepMap( previousCache, beforeCache, stepMeta, prev );
        stepMap.put( stepMeta, beforePrevMap );

        // Store it also in the beforeCache...
        //
        beforeCache.put( prev, beforePrevMap );
      }
    }

    Collections.sort( steps, new Comparator<StepMeta>() {

      @Override
      public int compare( StepMeta o1, StepMeta o2 ) {

        Map<StepMeta, Boolean> beforeMap = stepMap.get( o1 );
        if ( beforeMap != null ) {
          if ( beforeMap.get( o2 ) == null ) {
            return -1;
          } else {
            return 1;
          }
        } else {
          return o1.getName().compareToIgnoreCase( o2.getName() );
        }
      }
    } );

    long endTime = System.currentTimeMillis();
    log.logBasic(
      BaseMessages.getString( PKG, "PipelineMeta.Log.TimeExecutionStepSort", ( endTime - startTime ), prevCount ) );

    return stepMap;
  }

  /**
   * Fills a map with all steps previous to the given step. This method uses a caching technique, so if a map is
   * provided that contains the specified previous step, it is immediately returned to avoid unnecessary processing.
   * Otherwise, the previous steps are determined and added to the map recursively, and a cache is constructed for later
   * use.
   *
   * @param previousCache    the previous cache, must be non-null
   * @param beforeCache      the before cache, must be non-null
   * @param originStepMeta   the origin step meta
   * @param previousStepMeta the previous step meta
   * @return the map
   */
  private Map<StepMeta, Boolean> updateFillStepMap( Map<StepMeta, List<StepMeta>> previousCache,
                                                    Map<StepMeta, Map<StepMeta, Boolean>> beforeCache, StepMeta originStepMeta, StepMeta previousStepMeta ) {

    // See if we have a hash map to store step occurrence (located before the step)
    //
    Map<StepMeta, Boolean> beforeMap = beforeCache.get( previousStepMeta );
    if ( beforeMap == null ) {
      beforeMap = new HashMap<>();
    } else {
      return beforeMap; // Nothing left to do here!
    }

    // Store the current previous step in the map
    //
    beforeMap.put( previousStepMeta, Boolean.TRUE );

    // Figure out all the previous steps as well, they all need to go in there...
    //
    List<StepMeta> prevSteps = previousCache.get( previousStepMeta );
    if ( prevSteps == null ) {
      prevSteps = findPreviousSteps( previousStepMeta );
      prevCount++;
      previousCache.put( previousStepMeta, prevSteps );
    }

    // Now, get the previous steps for stepMeta recursively...
    // We only do this when the beforeMap is not known yet...
    //
    for ( StepMeta prev : prevSteps ) {
      Map<StepMeta, Boolean> beforePrevMap = updateFillStepMap( previousCache, beforeCache, originStepMeta, prev );

      // Keep a copy in the cache...
      //
      beforeCache.put( prev, beforePrevMap );

      // Also add it to the new map for this step...
      //
      beforeMap.putAll( beforePrevMap );
    }

    return beforeMap;
  }

  /**
   * Sorts the hops in a natural way: from beginning to end.
   */
  public void sortHopsNatural() {
    // Loop over the hops...
    for ( int j = 0; j < nrPipelineHops(); j++ ) {
      // Buble sort: we need to do this several times...
      for ( int i = 0; i < nrPipelineHops() - 1; i++ ) {
        PipelineHopMeta one = getPipelineHop( i );
        PipelineHopMeta two = getPipelineHop( i + 1 );

        StepMeta a = two.getFromStep();
        StepMeta b = one.getToStep();

        if ( !findPrevious( a, b ) && !a.equals( b ) ) {
          setPipelineHop( i + 1, one );
          setPipelineHop( i, two );
        }
      }
    }
  }

  /**
   * Determines the impact of the different steps in a pipeline on databases, tables and field.
   *
   * @param impact  An ArrayList of DatabaseImpact objects.
   * @param monitor a progress monitor listener to be updated as the pipeline is analyzed
   * @throws HopStepException if any errors occur during analysis
   */
  public void analyseImpact( List<DatabaseImpact> impact, ProgressMonitorListener monitor ) throws HopStepException {
    if ( monitor != null ) {
      monitor
        .beginTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.DeterminingImpactTask.Title" ), nrSteps() );
    }
    boolean stop = false;
    for ( int i = 0; i < nrSteps() && !stop; i++ ) {
      if ( monitor != null ) {
        monitor.subTask(
          BaseMessages.getString( PKG, "PipelineMeta.Monitor.LookingAtStepTask.Title" ) + ( i + 1 ) + "/" + nrSteps() );
      }
      StepMeta stepMeta = getStep( i );

      RowMetaInterface prev = getPrevStepFields( stepMeta );
      StepMetaInterface stepint = stepMeta.getStepMetaInterface();
      RowMetaInterface inform = null;
      StepMeta[] lu = getInfoStep( stepMeta );
      if ( lu != null ) {
        inform = getStepFields( lu );
      } else {
        inform = stepint.getTableFields();
      }

      stepint.analyseImpact( impact, this, stepMeta, prev, null, null, inform, metaStore );

      if ( monitor != null ) {
        monitor.worked( 1 );
        stop = monitor.isCanceled();
      }
    }

    if ( monitor != null ) {
      monitor.done();
    }
  }

  /**
   * Proposes an alternative stepname when the original already exists.
   *
   * @param stepname The stepname to find an alternative for
   * @return The suggested alternative stepname.
   */
  public String getAlternativeStepname( String stepname ) {
    String newname = stepname;
    StepMeta stepMeta = findStep( newname );
    int nr = 1;
    while ( stepMeta != null ) {
      nr++;
      newname = stepname + " " + nr;
      stepMeta = findStep( newname );
    }

    return newname;
  }

  /**
   * Builds a list of all the SQL statements that this pipeline needs in order to work properly.
   *
   * @return An ArrayList of SQLStatement objects.
   * @throws HopStepException if any errors occur during SQL statement generation
   */
  public List<SQLStatement> getSQLStatements() throws HopStepException {
    return getSQLStatements( null );
  }

  /**
   * Builds a list of all the SQL statements that this pipeline needs in order to work properly.
   *
   * @param monitor a progress monitor listener to be updated as the SQL statements are generated
   * @return An ArrayList of SQLStatement objects.
   * @throws HopStepException if any errors occur during SQL statement generation
   */
  public List<SQLStatement> getSQLStatements( ProgressMonitorListener monitor ) throws HopStepException {
    if ( monitor != null ) {
      monitor.beginTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.GettingTheSQLForPipelineTask.Title" ), nrSteps() + 1 );
    }
    List<SQLStatement> stats = new ArrayList<>();

    for ( int i = 0; i < nrSteps(); i++ ) {
      StepMeta stepMeta = getStep( i );
      if ( monitor != null ) {
        monitor.subTask(
          BaseMessages.getString( PKG, "PipelineMeta.Monitor.GettingTheSQLForStepTask.Title", "" + stepMeta ) );
      }
      RowMetaInterface prev = getPrevStepFields( stepMeta );
      SQLStatement sql = stepMeta.getStepMetaInterface().getSQLStatements( this, stepMeta, prev, metaStore );
      if ( sql.getSQL() != null || sql.hasError() ) {
        stats.add( sql );
      }
      if ( monitor != null ) {
        monitor.worked( 1 );
      }
    }

    // Also check the sql for the logtable...
    //
    if ( monitor != null ) {
      monitor.subTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.GettingTheSQLForPipelineTask.Title2" ) );
    }
    if ( pipelineLogTable.getDatabaseMeta() != null && ( !Utils.isEmpty( pipelineLogTable.getTableName() ) || !Utils
      .isEmpty( performanceLogTable.getTableName() ) ) ) {
      try {
        for ( LogTableInterface logTable : new LogTableInterface[] { pipelineLogTable, performanceLogTable,
          channelLogTable, stepLogTable, } ) {
          if ( logTable.getDatabaseMeta() != null && !Utils.isEmpty( logTable.getTableName() ) ) {

            Database db = null;
            try {
              db = new Database( this, pipelineLogTable.getDatabaseMeta() );
              db.shareVariablesWith( this );
              db.connect();

              RowMetaInterface fields = logTable.getLogRecord( LogStatus.START, null, null ).getRowMeta();
              String
                schemaTable =
                logTable.getDatabaseMeta()
                  .getQuotedSchemaTableCombination( logTable.getSchemaName(), logTable.getTableName() );
              String sql = db.getDDL( schemaTable, fields );
              if ( !Utils.isEmpty( sql ) ) {
                SQLStatement stat = new SQLStatement( "<this pipeline>", pipelineLogTable.getDatabaseMeta(), sql );
                stats.add( stat );
              }
            } catch ( Exception e ) {
              throw new HopDatabaseException(
                "Unable to connect to logging database [" + logTable.getDatabaseMeta() + "]", e );
            } finally {
              if ( db != null ) {
                db.disconnect();
              }
            }
          }
        }
      } catch ( HopDatabaseException dbe ) {
        SQLStatement stat = new SQLStatement( "<this pipeline>", pipelineLogTable.getDatabaseMeta(), null );
        stat.setError(
          BaseMessages.getString( PKG, "PipelineMeta.SQLStatement.ErrorDesc.ErrorObtainingPipelineLogTableInfo" )
            + dbe.getMessage() );
        stats.add( stat );
      }
    }
    if ( monitor != null ) {
      monitor.worked( 1 );
    }
    if ( monitor != null ) {
      monitor.done();
    }

    return stats;
  }

  /**
   * Get the SQL statements (needed to run this pipeline) as a single String.
   *
   * @return the SQL statements needed to run this pipeline
   * @throws HopStepException if any errors occur during SQL statement generation
   */
  public String getSQLStatementsString() throws HopStepException {
    String sql = "";
    List<SQLStatement> stats = getSQLStatements();
    for ( int i = 0; i < stats.size(); i++ ) {
      SQLStatement stat = stats.get( i );
      if ( !stat.hasError() && stat.hasSQL() ) {
        sql += stat.getSQL();
      }
    }

    return sql;
  }

  /**
   * Checks all the steps and fills a List of (CheckResult) remarks.
   *
   * @param remarks       The remarks list to add to.
   * @param only_selected true to check only the selected steps, false for all steps
   * @param monitor       a progress monitor listener to be updated as the SQL statements are generated
   */
  public void checkSteps( List<CheckResultInterface> remarks, boolean only_selected, ProgressMonitorListener monitor,
                          VariableSpace space, IMetaStore metaStore ) {
    try {
      remarks.clear(); // Start with a clean slate...

      Map<ValueMetaInterface, String> values = new Hashtable<>();
      String[] stepnames;
      StepMeta[] steps;
      List<StepMeta> selectedSteps = getSelectedSteps();
      if ( !only_selected || selectedSteps.isEmpty() ) {
        stepnames = getStepNames();
        steps = getStepsArray();
      } else {
        stepnames = getSelectedStepNames();
        steps = selectedSteps.toArray( new StepMeta[ selectedSteps.size() ] );
      }

      ExtensionPointHandler.callExtensionPoint( getLogChannel(), HopExtensionPoint.BeforeCheckSteps.id,
        new CheckStepsExtension( remarks, space, this, steps, metaStore ) );

      boolean stop_checking = false;

      if ( monitor != null ) {
        monitor.beginTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.VerifyingThisPipelineTask.Title" ),
          steps.length + 2 );
      }

      for ( int i = 0; i < steps.length && !stop_checking; i++ ) {
        if ( monitor != null ) {
          monitor.subTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.VerifyingStepTask.Title", stepnames[ i ] ) );
        }

        StepMeta stepMeta = steps[ i ];

        int nrinfo = findNrInfoSteps( stepMeta );
        StepMeta[] infostep = null;
        if ( nrinfo > 0 ) {
          infostep = getInfoStep( stepMeta );
        }

        RowMetaInterface info = null;
        if ( infostep != null ) {
          try {
            info = getStepFields( infostep );
          } catch ( HopStepException kse ) {
            info = null;
            CheckResult
              cr =
              new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString( PKG,
                "PipelineMeta.CheckResult.TypeResultError.ErrorOccurredGettingStepInfoFields.Description",
                "" + stepMeta, Const.CR + kse.getMessage() ), stepMeta );
            remarks.add( cr );
          }
        }

        // The previous fields from non-informative steps:
        RowMetaInterface prev = null;
        try {
          prev = getPrevStepFields( stepMeta );
        } catch ( HopStepException kse ) {
          CheckResult
            cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages
              .getString( PKG, "PipelineMeta.CheckResult.TypeResultError.ErrorOccurredGettingInputFields.Description",
                "" + stepMeta, Const.CR + kse.getMessage() ), stepMeta );
          remarks.add( cr );
          // This is a severe error: stop checking...
          // Otherwise we wind up checking time & time again because nothing gets put in the database
          // cache, the timeout of certain databases is very long... (Oracle)
          stop_checking = true;
        }

        if ( isStepUsedInPipelineHops( stepMeta ) || getSteps().size() == 1 ) {
          // Get the input & output steps!
          // Copy to arrays:
          String[] input = getPrevStepNames( stepMeta );
          String[] output = getNextStepNames( stepMeta );

          // Check step specific info...
          ExtensionPointHandler.callExtensionPoint( getLogChannel(), HopExtensionPoint.BeforeCheckStep.id,
            new CheckStepsExtension( remarks, space, this, new StepMeta[] { stepMeta }, metaStore ) );
          stepMeta.check( remarks, this, prev, input, output, info, space, metaStore );
          ExtensionPointHandler.callExtensionPoint( getLogChannel(), HopExtensionPoint.AfterCheckStep.id,
            new CheckStepsExtension( remarks, space, this, new StepMeta[] { stepMeta }, metaStore ) );

          // See if illegal characters etc. were used in field-names...
          if ( prev != null ) {
            for ( int x = 0; x < prev.size(); x++ ) {
              ValueMetaInterface v = prev.getValueMeta( x );
              String name = v.getName();
              if ( name == null ) {
                values.put( v,
                  BaseMessages.getString( PKG, "PipelineMeta.Value.CheckingFieldName.FieldNameIsEmpty.Description" ) );
              } else if ( name.indexOf( ' ' ) >= 0 ) {
                values.put( v, BaseMessages
                  .getString( PKG, "PipelineMeta.Value.CheckingFieldName.FieldNameContainsSpaces.Description" ) );
              } else {
                char[] list =
                  new char[] { '.', ',', '-', '/', '+', '*', '\'', '\t', '"', '|', '@', '(', ')', '{', '}', '!',
                    '^' };
                for ( int c = 0; c < list.length; c++ ) {
                  if ( name.indexOf( list[ c ] ) >= 0 ) {
                    values.put( v, BaseMessages.getString( PKG,
                      "PipelineMeta.Value.CheckingFieldName.FieldNameContainsUnfriendlyCodes.Description",
                      String.valueOf( list[ c ] ) ) );
                  }
                }
              }
            }

            // Check if 2 steps with the same name are entering the step...
            if ( prev.size() > 1 ) {
              String[] fieldNames = prev.getFieldNames();
              String[] sortedNames = Const.sortStrings( fieldNames );

              String prevName = sortedNames[ 0 ];
              for ( int x = 1; x < sortedNames.length; x++ ) {
                // Checking for doubles
                if ( prevName.equalsIgnoreCase( sortedNames[ x ] ) ) {
                  // Give a warning!!
                  CheckResult
                    cr =
                    new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages
                      .getString( PKG, "PipelineMeta.CheckResult.TypeResultWarning.HaveTheSameNameField.Description",
                        prevName ), stepMeta );
                  remarks.add( cr );
                } else {
                  prevName = sortedNames[ x ];
                }
              }
            }
          } else {
            CheckResult
              cr =
              new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages
                .getString( PKG, "PipelineMeta.CheckResult.TypeResultError.CannotFindPreviousFields.Description" )
                + stepMeta.getName(), stepMeta );
            remarks.add( cr );
          }
        } else {
          CheckResult
            cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_WARNING,
              BaseMessages.getString( PKG, "PipelineMeta.CheckResult.TypeResultWarning.StepIsNotUsed.Description" ),
              stepMeta );
          remarks.add( cr );
        }

        // Also check for mixing rows...
        try {
          checkRowMixingStatically( stepMeta, null );
        } catch ( HopRowException e ) {
          CheckResult cr = new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, e.getMessage(), stepMeta );
          remarks.add( cr );
        }

        if ( monitor != null ) {
          monitor.worked( 1 ); // progress bar...
          if ( monitor.isCanceled() ) {
            stop_checking = true;
          }
        }
      }

      // Also, check the logging table of the pipeline...
      if ( monitor == null || !monitor.isCanceled() ) {
        if ( monitor != null ) {
          monitor.subTask( BaseMessages.getString( PKG, "PipelineMeta.Monitor.CheckingTheLoggingTableTask.Title" ) );
        }
        if ( pipelineLogTable.getDatabaseMeta() != null ) {
          Database logdb = new Database( this, pipelineLogTable.getDatabaseMeta() );
          logdb.shareVariablesWith( this );
          try {
            logdb.connect();
            CheckResult
              cr =
              new CheckResult( CheckResultInterface.TYPE_RESULT_OK,
                BaseMessages.getString( PKG, "PipelineMeta.CheckResult.TypeResultOK.ConnectingWorks.Description" ),
                null );
            remarks.add( cr );

            if ( pipelineLogTable.getTableName() != null ) {
              if ( logdb.checkTableExists( pipelineLogTable.getSchemaName(), pipelineLogTable.getTableName() ) ) {
                cr =
                  new CheckResult( CheckResultInterface.TYPE_RESULT_OK, BaseMessages
                    .getString( PKG, "PipelineMeta.CheckResult.TypeResultOK.LoggingTableExists.Description",
                      pipelineLogTable.getTableName() ), null );
                remarks.add( cr );

                RowMetaInterface fields = pipelineLogTable.getLogRecord( LogStatus.START, null, null ).getRowMeta();
                String sql = logdb.getDDL( pipelineLogTable.getTableName(), fields );
                if ( sql == null || sql.length() == 0 ) {
                  cr =
                    new CheckResult( CheckResultInterface.TYPE_RESULT_OK,
                      BaseMessages.getString( PKG, "PipelineMeta.CheckResult.TypeResultOK.CorrectLayout.Description" ),
                      null );
                  remarks.add( cr );
                } else {
                  cr =
                    new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages.getString( PKG,
                      "PipelineMeta.CheckResult.TypeResultError.LoggingTableNeedsAdjustments.Description" ) + Const.CR
                      + sql, null );
                  remarks.add( cr );
                }

              } else {
                cr =
                  new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages
                    .getString( PKG, "PipelineMeta.CheckResult.TypeResultError.LoggingTableDoesNotExist.Description" ),
                    null );
                remarks.add( cr );
              }
            } else {
              cr =
                new CheckResult( CheckResultInterface.TYPE_RESULT_ERROR, BaseMessages
                  .getString( PKG, "PipelineMeta.CheckResult.TypeResultError.LogTableNotSpecified.Description" ),
                  null );
              remarks.add( cr );
            }
          } catch ( HopDatabaseException dbe ) {
            // Ignore errors
          } finally {
            logdb.disconnect();
          }
        }
        if ( monitor != null ) {
          monitor.worked( 1 );
        }

      }

      if ( monitor != null ) {
        monitor.subTask( BaseMessages
          .getString( PKG, "PipelineMeta.Monitor.CheckingForDatabaseUnfriendlyCharactersInFieldNamesTask.Title" ) );
      }
      if ( values.size() > 0 ) {
        for ( ValueMetaInterface v : values.keySet() ) {
          String message = values.get( v );
          CheckResult
            cr =
            new CheckResult( CheckResultInterface.TYPE_RESULT_WARNING, BaseMessages
              .getString( PKG, "PipelineMeta.CheckResult.TypeResultWarning.Description", v.getName(), message,
                v.getOrigin() ), findStep( v.getOrigin() ) );
          remarks.add( cr );
        }
      } else {
        CheckResult
          cr =
          new CheckResult( CheckResultInterface.TYPE_RESULT_OK,
            BaseMessages.getString( PKG, "PipelineMeta.CheckResult.TypeResultOK.Description" ), null );
        remarks.add( cr );
      }
      if ( monitor != null ) {
        monitor.worked( 1 );
      }
      ExtensionPointHandler.callExtensionPoint( getLogChannel(), HopExtensionPoint.AfterCheckSteps.id,
        new CheckStepsExtension( remarks, space, this, steps, metaStore ) );
    } catch ( Exception e ) {
      log.logError( Const.getStackTracker( e ) );
      throw new RuntimeException( e );
    }

  }

  /**
   * Gets a list of dependencies for the pipeline
   *
   * @return a list of the dependencies for the pipeline
   */
  public List<PipelineDependency> getDependencies() {
    return dependencies;
  }

  /**
   * Sets the dependencies for the pipeline.
   *
   * @param dependencies The dependency list to set.
   */
  public void setDependencies( List<PipelineDependency> dependencies ) {
    this.dependencies = dependencies;
  }

  /**
   * Gets the database connection associated with "max date" processing. The connection, along with a specified table
   * and field, allows for the filtering of the number of rows to process in a pipeline by time, such as only
   * processing the rows/records since the last time the pipeline ran correctly. This can be used for auditing and
   * throttling data during warehousing operations.
   *
   * @return Returns the meta-data associated with the most recent database connection.
   */
  public DatabaseMeta getMaxDateConnection() {
    return maxDateConnection;
  }

  /**
   * Sets the database connection associated with "max date" processing.
   *
   * @param maxDateConnection the database meta-data to set
   * @see #getMaxDateConnection()
   */
  public void setMaxDateConnection( DatabaseMeta maxDateConnection ) {
    this.maxDateConnection = maxDateConnection;
  }

  /**
   * Gets the maximum date difference between start and end dates for row/record processing. This can be used for
   * auditing and throttling data during warehousing operations.
   *
   * @return the maximum date difference
   */
  public double getMaxDateDifference() {
    return maxDateDifference;
  }

  /**
   * Sets the maximum date difference between start and end dates for row/record processing.
   *
   * @param maxDateDifference The date difference to set.
   * @see #getMaxDateDifference()
   */
  public void setMaxDateDifference( double maxDateDifference ) {
    this.maxDateDifference = maxDateDifference;
  }

  /**
   * Gets the date field associated with "max date" processing. This allows for the filtering of the number of rows to
   * process in a pipeline by time, such as only processing the rows/records since the last time the
   * pipeline ran correctly. This can be used for auditing and throttling data during warehousing operations.
   *
   * @return a string representing the date for the most recent database connection.
   * @see #getMaxDateConnection()
   */
  public String getMaxDateField() {
    return maxDateField;
  }

  /**
   * Sets the date field associated with "max date" processing.
   *
   * @param maxDateField The date field to set.
   * @see #getMaxDateField()
   */
  public void setMaxDateField( String maxDateField ) {
    this.maxDateField = maxDateField;
  }

  /**
   * Gets the amount by which to increase the "max date" difference. This is used in "max date" processing, and can be
   * used to provide more fine-grained control of the date range. For example, if the end date specifies a minute for
   * which the data is not complete, you can "roll-back" the end date by one minute by
   *
   * @return Returns the maxDateOffset.
   * @see #setMaxDateOffset(double)
   */
  public double getMaxDateOffset() {
    return maxDateOffset;
  }

  /**
   * Sets the amount by which to increase the end date in "max date" processing. This can be used to provide more
   * fine-grained control of the date range. For example, if the end date specifies a minute for which the data is not
   * complete, you can "roll-back" the end date by one minute by setting the offset to -60.
   *
   * @param maxDateOffset The maxDateOffset to set.
   */
  public void setMaxDateOffset( double maxDateOffset ) {
    this.maxDateOffset = maxDateOffset;
  }

  /**
   * Gets the database table providing a date to be used in "max date" processing. This allows for the filtering of the
   * number of rows to process in a pipeline by time, such as only processing the rows/records since the last time
   * the pipeline ran correctly.
   *
   * @return Returns the maxDateTable.
   * @see #getMaxDateConnection()
   */
  public String getMaxDateTable() {
    return maxDateTable;
  }

  /**
   * Sets the table name associated with "max date" processing.
   *
   * @param maxDateTable The maxDateTable to set.
   * @see #getMaxDateTable()
   */
  public void setMaxDateTable( String maxDateTable ) {
    this.maxDateTable = maxDateTable;
  }

  /**
   * Gets the database cache object.
   *
   * @return the database cache object.
   */
  public DBCache getDbCache() {
    return dbCache;
  }

  /**
   * Sets the database cache object.
   *
   * @param dbCache the database cache object to set
   */
  public void setDbCache( DBCache dbCache ) {
    this.dbCache = dbCache;
  }

  /**
   * Gets the version of the pipeline.
   *
   * @return The version of the pipeline
   */
  public String getPipelineVersion() {
    return pipelineVersion;
  }

  /**
   * Sets the version of the pipeline.
   *
   * @param n The new version description of the pipeline
   */
  public void setPipelineVersion( String n ) {
    pipelineVersion = n;
  }

  /**
   * Sets the status of the pipeline.
   *
   * @param n The new status description of the pipeline
   */
  public void setPipelineStatus( int n ) {
    pipelineStatus = n;
  }

  /**
   * Gets the status of the pipeline.
   *
   * @return The status of the pipeline
   */
  public int getPipelineStatus() {
    return pipelineStatus;
  }

  /**
   * Gets a textual representation of the pipeline. If its name has been set, it will be returned, otherwise the
   * classname is returned.
   *
   * @return the textual representation of the pipeline.
   */
  @Override
  public String toString() {
    if ( !Utils.isEmpty( filename ) ) {
      if ( Utils.isEmpty( name ) ) {
        return filename;
      } else {
        return filename + " : " + name;
      }
    }

    if ( name != null ) {
      return name;
    } else {
      return PipelineMeta.class.getName();
    }
  }

  /**
   * Cancels queries opened for checking & fieldprediction.
   *
   * @throws HopDatabaseException if any errors occur during query cancellation
   */
  public void cancelQueries() throws HopDatabaseException {
    for ( int i = 0; i < nrSteps(); i++ ) {
      getStep( i ).getStepMetaInterface().cancelQueries();
    }
  }

  /**
   * Gets the amount of time (in nano-seconds) to wait while the input buffer is empty.
   *
   * @return the number of nano-seconds to wait while the input buffer is empty.
   */
  public int getSleepTimeEmpty() {
    return sleepTimeEmpty;
  }

  /**
   * Gets the amount of time (in nano-seconds) to wait while the input buffer is full.
   *
   * @return the number of nano-seconds to wait while the input buffer is full.
   */
  public int getSleepTimeFull() {
    return sleepTimeFull;
  }

  /**
   * Sets the amount of time (in nano-seconds) to wait while the input buffer is empty.
   *
   * @param sleepTimeEmpty the number of nano-seconds to wait while the input buffer is empty.
   */
  public void setSleepTimeEmpty( int sleepTimeEmpty ) {
    this.sleepTimeEmpty = sleepTimeEmpty;
  }

  /**
   * Sets the amount of time (in nano-seconds) to wait while the input buffer is full.
   *
   * @param sleepTimeFull the number of nano-seconds to wait while the input buffer is full.
   */
  public void setSleepTimeFull( int sleepTimeFull ) {
    this.sleepTimeFull = sleepTimeFull;
  }


  /**
   * Gets a list of all the strings used in this pipeline. The parameters indicate which collections to search and
   * which to exclude.
   *
   * @param searchSteps      true if steps should be searched, false otherwise
   * @param searchDatabases  true if databases should be searched, false otherwise
   * @param searchNotes      true if notes should be searched, false otherwise
   * @param includePasswords true if passwords should be searched, false otherwise
   * @return a list of search results for strings used in the pipeline.
   */
  public List<StringSearchResult> getStringList( boolean searchSteps, boolean searchDatabases, boolean searchNotes,
                                                 boolean includePasswords ) {
    List<StringSearchResult> stringList = new ArrayList<>();

    if ( searchSteps ) {
      // Loop over all steps in the pipeline and see what the used vars are...
      for ( int i = 0; i < nrSteps(); i++ ) {
        StepMeta stepMeta = getStep( i );
        stringList.add( new StringSearchResult( stepMeta.getName(), stepMeta, this,
          BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.StepName" ) ) );
        if ( stepMeta.getDescription() != null ) {
          stringList.add( new StringSearchResult( stepMeta.getDescription(), stepMeta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.StepDescription" ) ) );
        }
        StepMetaInterface metaInterface = stepMeta.getStepMetaInterface();
        StringSearcher.findMetaData( metaInterface, 1, stringList, stepMeta, this );
      }
    }

    // Loop over all steps in the pipeline and see what the used vars are...
    if ( searchDatabases ) {
      for ( DatabaseMeta meta : getDatabases() ) {
        stringList.add( new StringSearchResult( meta.getName(), meta, this,
          BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseConnectionName" ) ) );
        if ( meta.getHostname() != null ) {
          stringList.add( new StringSearchResult( meta.getHostname(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseHostName" ) ) );
        }
        if ( meta.getDatabaseName() != null ) {
          stringList.add( new StringSearchResult( meta.getDatabaseName(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseName" ) ) );
        }
        if ( meta.getUsername() != null ) {
          stringList.add( new StringSearchResult( meta.getUsername(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseUsername" ) ) );
        }
        if ( meta.getPluginId() != null ) {
          stringList.add( new StringSearchResult( meta.getPluginId(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseTypeDescription" ) ) );
        }
        if ( meta.getPort() != null ) {
          stringList.add( new StringSearchResult( meta.getPort(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabasePort" ) ) );
        }
        if ( meta.getServername() != null ) {
          stringList.add( new StringSearchResult( meta.getServername(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabaseServer" ) ) );
        }
        if ( includePasswords ) {
          if ( meta.getPassword() != null ) {
            stringList.add( new StringSearchResult( meta.getPassword(), meta, this,
              BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.DatabasePassword" ) ) );
          }
        }
      }
    }

    // Loop over all steps in the pipeline and see what the used vars are...
    if ( searchNotes ) {
      for ( int i = 0; i < nrNotes(); i++ ) {
        NotePadMeta meta = getNote( i );
        if ( meta.getNote() != null ) {
          stringList.add( new StringSearchResult( meta.getNote(), meta, this,
            BaseMessages.getString( PKG, "PipelineMeta.SearchMetadata.NotepadText" ) ) );
        }
      }
    }

    return stringList;
  }

  /**
   * Get a list of all the strings used in this pipeline. The parameters indicate which collections to search and
   * which to exclude.
   *
   * @param searchSteps     true if steps should be searched, false otherwise
   * @param searchDatabases true if databases should be searched, false otherwise
   * @param searchNotes     true if notes should be searched, false otherwise
   * @return a list of search results for strings used in the pipeline.
   */
  public List<StringSearchResult> getStringList( boolean searchSteps, boolean searchDatabases, boolean searchNotes ) {
    return getStringList( searchSteps, searchDatabases, searchNotes, false );
  }

  /**
   * Gets a list of the used variables in this pipeline.
   *
   * @return a list of the used variables in this pipeline.
   */
  public List<String> getUsedVariables() {
    // Get the list of Strings.
    List<StringSearchResult> stringList = getStringList( true, true, false, true );

    List<String> varList = new ArrayList<>();

    // Look around in the strings, see what we find...
    for ( int i = 0; i < stringList.size(); i++ ) {
      StringSearchResult result = stringList.get( i );
      StringUtil.getUsedVariables( result.getString(), varList, false );
    }

    return varList;
  }

  /**
   * Gets a list of partition schemas for this pipeline.
   *
   * @return a list of PartitionSchemas
   */
  public List<PartitionSchema> getPartitionSchemas() {
    return partitionSchemas;
  }


  /**
   * Checks if the pipeline is using unique database connections.
   *
   * @return true if the pipeline is using unique database connections, false otherwise
   */
  public boolean isUsingUniqueConnections() {
    return usingUniqueConnections;
  }

  /**
   * Sets whether the pipeline is using unique database connections.
   *
   * @param usingUniqueConnections true if the pipeline is using unique database connections, false otherwise
   */
  public void setUsingUniqueConnections( boolean usingUniqueConnections ) {
    this.usingUniqueConnections = usingUniqueConnections;
  }

  /**
   * Find a partition schema using its name.
   *
   * @param name The name of the partition schema to look for.
   * @return the partition with the specified name of null if nothing was found
   */
  public PartitionSchema findPartitionSchema( String name ) {
    for ( int i = 0; i < partitionSchemas.size(); i++ ) {
      PartitionSchema schema = partitionSchemas.get( i );
      if ( schema.getName().equalsIgnoreCase( name ) ) {
        return schema;
      }
    }
    return null;
  }

  /**
   * Add a new partition schema to the pipeline if that didn't exist yet. Otherwise, replace it.
   *
   * @param partitionSchema The partition schema to be added.
   */
  public void addOrReplacePartitionSchema( PartitionSchema partitionSchema ) {
    int index = partitionSchemas.indexOf( partitionSchema );
    if ( index < 0 ) {
      partitionSchemas.add( partitionSchema );
    } else {
      PartitionSchema previous = partitionSchemas.get( index );
      previous.replaceMeta( partitionSchema );
    }
    setChanged();
  }

  /**
   * Checks whether the pipeline is using thread priority management.
   *
   * @return true if the pipeline is using thread priority management, false otherwise
   */
  public boolean isUsingThreadPriorityManagment() {
    return usingThreadPriorityManagment;
  }

  /**
   * Sets whether the pipeline is using thread priority management.
   *
   * @param usingThreadPriorityManagment true if the pipeline is using thread priority management, false otherwise
   */
  public void setUsingThreadPriorityManagment( boolean usingThreadPriorityManagment ) {
    this.usingThreadPriorityManagment = usingThreadPriorityManagment;
  }

  /**
   * Check a step to see if there are no multiple steps to read from. If so, check to see if the receiving rows are all
   * the same in layout. We only want to ONLY use the DBCache for this to prevent GUI stalls.
   *
   * @param stepMeta the step to check
   * @param monitor  the monitor
   * @throws HopRowException in case we detect a row mixing violation
   */
  public void checkRowMixingStatically( StepMeta stepMeta, ProgressMonitorListener monitor ) throws HopRowException {
    List<StepMeta> prevSteps = findPreviousSteps( stepMeta );
    int nrPrevious = prevSteps.size();
    if ( nrPrevious > 1 ) {
      RowMetaInterface referenceRow = null;
      // See if all previous steps send out the same rows...
      for ( int i = 0; i < nrPrevious; i++ ) {
        StepMeta previousStep = prevSteps.get( i );
        try {
          RowMetaInterface row = getStepFields( previousStep, monitor ); // Throws HopStepException
          if ( referenceRow == null ) {
            referenceRow = row;
          } else if ( !stepMeta.getStepMetaInterface().excludeFromRowLayoutVerification() ) {
            BaseStep.safeModeChecking( referenceRow, row );
          }
        } catch ( HopStepException e ) {
          // We ignore this one because we are in the process of designing the pipeline, anything intermediate can
          // go wrong.
        }
      }
    }
  }

  /**
   * Sets the internal kettle variables.
   *
   * @param var the new internal kettle variables
   */
  @Override
  public void setInternalHopVariables( VariableSpace var ) {
    setInternalFilenameHopVariables( var );
    setInternalNameHopVariable( var );

    // Here we don't remove the job specific parameters, as they may come in handy.
    //
    if ( variables.getVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY ) == null ) {
      variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_DIRECTORY, "Parent Job File Directory" );
    }
    if ( variables.getVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_NAME ) == null ) {
      variables.setVariable( Const.INTERNAL_VARIABLE_JOB_FILENAME_NAME, "Parent Job Filename" );
    }
    if ( variables.getVariable( Const.INTERNAL_VARIABLE_JOB_NAME ) == null ) {
      variables.setVariable( Const.INTERNAL_VARIABLE_JOB_NAME, "Parent Job Name" );
    }

    setInternalEntryCurrentDirectory();

  }

  /**
   * Sets the internal name kettle variable.
   *
   * @param var the new internal name kettle variable
   */
  @Override
  protected void setInternalNameHopVariable( VariableSpace var ) {
    // The name of the pipeline
    //
    variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_NAME, Const.NVL( name, "" ) );
  }

  /**
   * Sets the internal filename kettle variables.
   *
   * @param var the new internal filename kettle variables
   */
  @Override
  protected void setInternalFilenameHopVariables( VariableSpace var ) {
    // If we have a filename that's defined, set variables. If not, clear them.
    //
    if ( !Utils.isEmpty( filename ) ) {
      try {
        FileObject fileObject = HopVFS.getFileObject( filename, var );
        FileName fileName = fileObject.getName();

        // The filename of the pipeline
        variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, fileName.getBaseName() );

        // The directory of the pipeline
        FileName fileDir = fileName.getParent();
        variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, fileDir.getURI() );
      } catch ( HopFileException e ) {
        log.logError( "Unexpected error setting internal filename variables!", e );

        variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, "" );
        variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, "" );
      }
    } else {
      variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY, "" );
      variables.setVariable( Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_NAME, "" );
    }

    setInternalEntryCurrentDirectory();

  }

  protected void setInternalEntryCurrentDirectory() {
    variables.setVariable( Const.INTERNAL_VARIABLE_ENTRY_CURRENT_DIRECTORY, variables.getVariable(
      StringUtils.isNotEmpty( filename )
        ? Const.INTERNAL_VARIABLE_PIPELINE_FILENAME_DIRECTORY
        : Const.INTERNAL_VARIABLE_ENTRY_CURRENT_DIRECTORY ) );
  }


  /**
   * Finds the mapping input step with the specified name. If no mapping input step is found, null is returned
   *
   * @param stepname the name to search for
   * @return the step meta-data corresponding to the desired mapping input step, or null if no step was found
   * @throws HopStepException if any errors occur during the search
   */
  public StepMeta findMappingInputStep( String stepname ) throws HopStepException {
    if ( !Utils.isEmpty( stepname ) ) {
      StepMeta stepMeta = findStep( stepname ); // TODO verify that it's a mapping input!!
      if ( stepMeta == null ) {
        throw new HopStepException( BaseMessages.getString(
          PKG, "PipelineMeta.Exception.StepNameNotFound", stepname ) );
      }
      return stepMeta;
    } else {
      // Find the first mapping input step that fits the bill.
      StepMeta stepMeta = null;
      for ( StepMeta mappingStep : steps ) {
        if ( mappingStep.getStepID().equals( "MappingInput" ) ) {
          if ( stepMeta == null ) {
            stepMeta = mappingStep;
          } else if ( stepMeta != null ) {
            throw new HopStepException( BaseMessages.getString(
              PKG, "PipelineMeta.Exception.OnlyOneMappingInputStepAllowed", "2" ) );
          }
        }
      }
      if ( stepMeta == null ) {
        throw new HopStepException( BaseMessages.getString(
          PKG, "PipelineMeta.Exception.OneMappingInputStepRequired" ) );
      }
      return stepMeta;
    }
  }

  /**
   * Finds the mapping output step with the specified name. If no mapping output step is found, null is returned.
   *
   * @param stepname the name to search for
   * @return the step meta-data corresponding to the desired mapping input step, or null if no step was found
   * @throws HopStepException if any errors occur during the search
   */
  public StepMeta findMappingOutputStep( String stepname ) throws HopStepException {
    if ( !Utils.isEmpty( stepname ) ) {
      StepMeta stepMeta = findStep( stepname ); // TODO verify that it's a mapping output step.
      if ( stepMeta == null ) {
        throw new HopStepException( BaseMessages.getString(
          PKG, "PipelineMeta.Exception.StepNameNotFound", stepname ) );
      }
      return stepMeta;
    } else {
      // Find the first mapping output step that fits the bill.
      StepMeta stepMeta = null;
      for ( StepMeta mappingStep : steps ) {
        if ( mappingStep.getStepID().equals( "MappingOutput" ) ) {
          if ( stepMeta == null ) {
            stepMeta = mappingStep;
          } else if ( stepMeta != null ) {
            throw new HopStepException( BaseMessages.getString(
              PKG, "PipelineMeta.Exception.OnlyOneMappingOutputStepAllowed", "2" ) );
          }
        }
      }
      if ( stepMeta == null ) {
        throw new HopStepException( BaseMessages.getString(
          PKG, "PipelineMeta.Exception.OneMappingOutputStepRequired" ) );
      }
      return stepMeta;
    }
  }

  /**
   * Gets a list of the resource dependencies.
   *
   * @return a list of ResourceReferences
   */
  public List<ResourceReference> getResourceDependencies() {
    return steps.stream()
      .flatMap( ( StepMeta stepMeta ) -> stepMeta.getResourceDependencies( this ).stream() )
      .collect( Collectors.toList() );
  }

  /**
   * Exports the specified objects to a flat-file system, adding content with filename keys to a set of definitions. The
   * supplied resource naming interface allows the object to name appropriately without worrying about those parts of
   * the implementation specific details.
   *
   * @param space                   the variable space to use
   * @param definitions
   * @param resourceNamingInterface
   * @param metaStore               the metaStore in which non-kettle metadata could reside.
   * @return the filename of the exported resource
   */
  @Override
  public String exportResources( VariableSpace space, Map<String, ResourceDefinition> definitions,
                                 ResourceNamingInterface resourceNamingInterface, IMetaStore metaStore ) throws HopException {

    String exportFileName = null;
    try {
      // Handle naming for XML bases resources...
      //
      String baseName;
      String originalPath;
      String fullname;
      String extension = "ktr";
      if ( StringUtils.isNotEmpty( getFilename() ) ) {
        FileObject fileObject = HopVFS.getFileObject( space.environmentSubstitute( getFilename() ), space );
        originalPath = fileObject.getParent().getURL().toString();
        baseName = fileObject.getName().getBaseName();
        fullname = fileObject.getURL().toString();

        exportFileName = resourceNamingInterface.nameResource( baseName, originalPath, extension, ResourceNamingInterface.FileNamingType.PIPELINE );
        ResourceDefinition definition = definitions.get( exportFileName );
        if ( definition == null ) {
          // If we do this once, it will be plenty :-)
          //
          PipelineMeta pipelineMeta = (PipelineMeta) this.realClone( false );
          // pipelineMeta.copyVariablesFrom(space);

          // Add used resources, modify pipelineMeta accordingly
          // Go through the list of steps, etc.
          // These critters change the steps in the cloned PipelineMeta
          // At the end we make a new XML version of it in "exported"
          // format...

          // loop over steps, databases will be exported to XML anyway.
          //
          for ( StepMeta stepMeta : pipelineMeta.getSteps() ) {
            stepMeta.exportResources( space, definitions, resourceNamingInterface, metaStore );
          }

          // Change the filename, calling this sets internal variables
          // inside of the pipeline.
          //
          pipelineMeta.setFilename( exportFileName );

          // Set a number of parameters for all the data files referenced so far...
          //
          Map<String, String> directoryMap = resourceNamingInterface.getDirectoryMap();
          if ( directoryMap != null ) {
            for ( String directory : directoryMap.keySet() ) {
              String parameterName = directoryMap.get( directory );
              pipelineMeta.addParameterDefinition( parameterName, directory, "Data file path discovered during export" );
            }
          }

          // At the end, add ourselves to the map...
          //
          String pipelineMetaContent = pipelineMeta.getXML();

          definition = new ResourceDefinition( exportFileName, pipelineMetaContent );

          // Also remember the original filename (if any), including variables etc.
          //
          if ( Utils.isEmpty( this.getFilename() ) ) { // Generated
            definition.setOrigin( fullname );
          } else {
            definition.setOrigin( this.getFilename() );
          }

          definitions.put( fullname, definition );
        }
      }

      return exportFileName;
    } catch ( FileSystemException e ) {
      throw new HopException( BaseMessages.getString(
        PKG, "PipelineMeta.Exception.ErrorOpeningOrValidatingTheXMLFile", getFilename() ), e );
    } catch ( HopFileException e ) {
      throw new HopException( BaseMessages.getString(
        PKG, "PipelineMeta.Exception.ErrorOpeningOrValidatingTheXMLFile", getFilename() ), e );
    }
  }

  /**
   * Checks whether the pipeline is capturing step performance snapshots.
   *
   * @return true if the pipeline is capturing step performance snapshots, false otherwise
   */
  public boolean isCapturingStepPerformanceSnapShots() {
    return capturingStepPerformanceSnapShots;
  }

  /**
   * Sets whether the pipeline is capturing step performance snapshots.
   *
   * @param capturingStepPerformanceSnapShots true if the pipeline is capturing step performance snapshots, false otherwise
   */
  public void setCapturingStepPerformanceSnapShots( boolean capturingStepPerformanceSnapShots ) {
    this.capturingStepPerformanceSnapShots = capturingStepPerformanceSnapShots;
  }

  /**
   * Gets the step performance capturing delay.
   *
   * @return the step performance capturing delay
   */
  public long getStepPerformanceCapturingDelay() {
    return stepPerformanceCapturingDelay;
  }

  /**
   * Sets the step performance capturing delay.
   *
   * @param stepPerformanceCapturingDelay the stepPerformanceCapturingDelay to set
   */
  public void setStepPerformanceCapturingDelay( long stepPerformanceCapturingDelay ) {
    this.stepPerformanceCapturingDelay = stepPerformanceCapturingDelay;
  }

  /**
   * Gets the step performance capturing size limit.
   *
   * @return the step performance capturing size limit
   */
  public String getStepPerformanceCapturingSizeLimit() {
    return stepPerformanceCapturingSizeLimit;
  }

  /**
   * Sets the step performance capturing size limit.
   *
   * @param stepPerformanceCapturingSizeLimit the step performance capturing size limit to set
   */
  public void setStepPerformanceCapturingSizeLimit( String stepPerformanceCapturingSizeLimit ) {
    this.stepPerformanceCapturingSizeLimit = stepPerformanceCapturingSizeLimit;
  }

  /**
   * Clears the step fields and loop caches.
   */
  public void clearCaches() {
    clearStepFieldsCachce();
    clearLoopCache();
    clearPreviousStepCache();
  }

  /**
   * Clears the step fields cachce.
   */
  private void clearStepFieldsCachce() {
    stepsFieldsCache.clear();
  }

  /**
   * Clears the loop cache.
   */
  private void clearLoopCache() {
    loopCache.clear();
  }

  @VisibleForTesting
  void clearPreviousStepCache() {
    previousStepCache.clear();
  }

  /**
   * Gets the log channel.
   *
   * @return the log channel
   */
  public LogChannelInterface getLogChannel() {
    return log;
  }

  /**
   * Gets the log channel ID.
   *
   * @return the log channel ID
   * @see org.apache.hop.core.logging.LoggingObjectInterface#getLogChannelId()
   */
  @Override
  public String getLogChannelId() {
    return log.getLogChannelId();
  }

  /**
   * Gets the object type.
   *
   * @return the object type
   * @see org.apache.hop.core.logging.LoggingObjectInterface#getObjectType()
   */
  @Override
  public LoggingObjectType getObjectType() {
    return LoggingObjectType.PIPELINE_META;
  }

  /**
   * Gets the log table for the pipeline.
   *
   * @return the log table for the pipeline
   */
  public PipelineLogTable getPipelineLogTable() {
    return pipelineLogTable;
  }

  /**
   * Sets the log table for the pipeline.
   *
   * @param pipelineLogTable the log table to set
   */
  public void setPipelineLogTable( PipelineLogTable pipelineLogTable ) {
    this.pipelineLogTable = pipelineLogTable;
  }

  /**
   * Gets the performance log table for the pipeline.
   *
   * @return the performance log table for the pipeline
   */
  public PerformanceLogTable getPerformanceLogTable() {
    return performanceLogTable;
  }

  /**
   * Sets the performance log table for the pipeline.
   *
   * @param performanceLogTable the performance log table to set
   */
  public void setPerformanceLogTable( PerformanceLogTable performanceLogTable ) {
    this.performanceLogTable = performanceLogTable;
  }

  /**
   * Gets the step log table for the pipeline.
   *
   * @return the step log table for the pipeline
   */
  public StepLogTable getStepLogTable() {
    return stepLogTable;
  }

  /**
   * Sets the step log table for the pipeline.
   *
   * @param stepLogTable the step log table to set
   */
  public void setStepLogTable( StepLogTable stepLogTable ) {
    this.stepLogTable = stepLogTable;
  }

  /**
   * Gets a list of the log tables (pipeline, step, performance, channel) for the pipeline.
   *
   * @return a list of LogTableInterfaces for the pipeline
   */
  public List<LogTableInterface> getLogTables() {
    List<LogTableInterface> logTables = new ArrayList<>();
    logTables.add( pipelineLogTable );
    logTables.add( stepLogTable );
    logTables.add( performanceLogTable );
    logTables.add( channelLogTable );
    logTables.add( metricsLogTable );
    return logTables;
  }

  /**
   * Gets the pipeline type.
   *
   * @return the pipelineType
   */
  public PipelineType getPipelineType() {
    return pipelineType;
  }

  /**
   * Sets the pipeline type.
   *
   * @param pipelineType the pipelineType to set
   */
  public void setPipelineType( PipelineType pipelineType ) {
    this.pipelineType = pipelineType;
  }

  /**
   * Utility method to write the XML of this pipeline to a file, mostly for testing purposes.
   *
   * @param filename The filename to save to
   * @throws HopXMLException in case something goes wrong.
   */
  public void writeXML( String filename ) throws HopXMLException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream( filename );
      fos.write( XMLHandler.getXMLHeader().getBytes( Const.XML_ENCODING ) );
      fos.write( getXML().getBytes( Const.XML_ENCODING ) );
    } catch ( Exception e ) {
      throw new HopXMLException( "Unable to save to XML file '" + filename + "'", e );
    } finally {
      if ( fos != null ) {
        try {
          fos.close();
        } catch ( IOException e ) {
          throw new HopXMLException( "Unable to close file '" + filename + "'", e );
        }
      }
    }
  }

  /**
   * @return the metricsLogTable
   */
  public MetricsLogTable getMetricsLogTable() {
    return metricsLogTable;
  }

  /**
   * @param metricsLogTable the metricsLogTable to set
   */
  public void setMetricsLogTable( MetricsLogTable metricsLogTable ) {
    this.metricsLogTable = metricsLogTable;
  }

  @Override
  public boolean isGatheringMetrics() {
    return log.isGatheringMetrics();
  }

  @Override
  public void setGatheringMetrics( boolean gatheringMetrics ) {
    log.setGatheringMetrics( gatheringMetrics );
  }

  @Override
  public boolean isForcingSeparateLogging() {
    return log.isForcingSeparateLogging();
  }

  @Override
  public void setForcingSeparateLogging( boolean forcingSeparateLogging ) {
    log.setForcingSeparateLogging( forcingSeparateLogging );
  }

  public void addStepChangeListener( StepMetaChangeListenerInterface listener ) {
    stepChangeListeners.add( listener );
  }

  public void addStepChangeListener( int p, StepMetaChangeListenerInterface list ) {
    int indexListener = -1;
    int indexListenerRemove = -1;
    StepMeta rewriteStep = steps.get( p );
    StepMetaInterface iface = rewriteStep.getStepMetaInterface();
    if ( iface instanceof StepMetaChangeListenerInterface ) {
      for ( StepMetaChangeListenerInterface listener : stepChangeListeners ) {
        indexListener++;
        if ( listener.equals( iface ) ) {
          indexListenerRemove = indexListener;
        }
      }
      if ( indexListenerRemove >= 0 ) {
        stepChangeListeners.add( indexListenerRemove, list );
      } else if ( stepChangeListeners.size() == 0 && p == 0 ) {
        stepChangeListeners.add( list );
      }
    }
  }

  public void removeStepChangeListener( StepMetaChangeListenerInterface list ) {
    int indexListener = -1;
    int indexListenerRemove = -1;
    for ( StepMetaChangeListenerInterface listener : stepChangeListeners ) {
      indexListener++;
      if ( listener.equals( list ) ) {
        indexListenerRemove = indexListener;
      }
    }
    if ( indexListenerRemove >= 0 ) {
      stepChangeListeners.remove( indexListenerRemove );
    }
  }

  public void notifyAllListeners( StepMeta oldMeta, StepMeta newMeta ) {
    for ( StepMetaChangeListenerInterface listener : stepChangeListeners ) {
      listener.onStepChange( this, oldMeta, newMeta );
    }
  }

  public boolean containsStepMeta( StepMeta stepMeta ) {
    return steps.contains( stepMeta );
  }

  public List<Missing> getMissingPipeline() {
    return missingPipeline;
  }

  public void addMissingPipeline( Missing pipeline ) {
    if ( missingPipeline == null ) {
      missingPipeline = new ArrayList<>();
    }
    missingPipeline.add( pipeline );
  }

  public void removeMissingPipeline( Missing pipeline ) {
    if ( missingPipeline != null && pipeline != null && missingPipeline.contains( pipeline ) ) {
      missingPipeline.remove( pipeline );
    }
  }

  @Override
  public boolean hasMissingPlugins() {
    return missingPipeline != null && !missingPipeline.isEmpty();
  }

  /**
   * @return
   */
  public int getCacheVersion() throws HopException {
    HashCodeBuilder hashCodeBuilder = new HashCodeBuilder( 17, 31 )
      // info
      .append( this.getName() )
      .append( this.getPipelineType() )
      .append( this.getSleepTimeEmpty() )
      .append( this.getSleepTimeFull() )
      .append( this.isUsingUniqueConnections() )
      .append( this.isUsingThreadPriorityManagment() )
      .append( this.isCapturingStepPerformanceSnapShots() )
      .append( this.getStepPerformanceCapturingDelay() )
      .append( this.getStepPerformanceCapturingSizeLimit() )

      .append( this.getMaxDateConnection() )
      .append( this.getMaxDateTable() )
      .append( this.getMaxDateField() )
      .append( this.getMaxDateOffset() )
      .append( this.getMaxDateDifference() )

      .append( this.getDependencies() )
      .append( this.getPartitionSchemas() )

      .append( this.nrPipelineHops() )

      // steps
      .append( this.getSteps().size() )
      .append( this.getStepNames() )

      // hops
      .append( this.hops );

    List<StepMeta> steps = this.getSteps();

    for ( StepMeta step : steps ) {
      hashCodeBuilder
        .append( step.getName() )
        .append( step.getStepMetaInterface().getXML() )
        .append( step.isDoingErrorHandling() );
    }
    return hashCodeBuilder.toHashCode();
  }

  private static String getStepMetaCacheKey( StepMeta stepMeta, boolean info ) {
    return String.format( "%1$b-%2$s-%3$s", info, stepMeta.getStepID(), stepMeta.toString() );
  }

  private static RowMetaInterface[] cloneRowMetaInterfaces( RowMetaInterface[] inform ) {
    RowMetaInterface[] cloned = inform.clone();
    for ( int i = 0; i < cloned.length; i++ ) {
      if ( cloned[ i ] != null ) {
        cloned[ i ] = cloned[ i ].clone();
      }
    }
    return cloned;
  }
}
