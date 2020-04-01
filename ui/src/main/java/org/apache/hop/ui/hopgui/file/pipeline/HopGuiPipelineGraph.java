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

package org.apache.hop.ui.hopgui.file.pipeline;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.CheckResultInterface;
import org.apache.hop.core.Const;
import org.apache.hop.core.EngineMetaInterface;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.Props;
import org.apache.hop.core.SwtUniversalImage;
import org.apache.hop.core.action.GuiContextAction;
import org.apache.hop.core.dnd.DragAndDropContainer;
import org.apache.hop.core.dnd.XMLTransfer;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointHandler;
import org.apache.hop.core.extension.HopExtensionPoint;
import org.apache.hop.core.gui.AreaOwner;
import org.apache.hop.core.gui.AreaOwner.AreaType;
import org.apache.hop.core.gui.BasePainter;
import org.apache.hop.core.gui.GCInterface;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.Redrawable;
import org.apache.hop.core.gui.SnapAllignDistribute;
import org.apache.hop.core.gui.plugin.GuiActionType;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiKeyboardShortcut;
import org.apache.hop.core.gui.plugin.GuiOSXKeyboardShortcut;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiToolbarElement;
import org.apache.hop.core.gui.plugin.IGuiRefresher;
import org.apache.hop.core.logging.DefaultLogLevel;
import org.apache.hop.core.logging.HasLogChannelInterface;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogChannelInterface;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.logging.LogParentProvidedInterface;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.LoggingRegistry;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.plugins.PluginInterface;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.StepPluginType;
import org.apache.hop.core.row.RowMetaInterface;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVFS;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.lineage.PipelineDataLineage;
import org.apache.hop.metastore.api.exceptions.MetaStoreException;
import org.apache.hop.metastore.persist.MetaStoreFactory;
import org.apache.hop.pipeline.DatabaseImpact;
import org.apache.hop.pipeline.ExecutionAdapter;
import org.apache.hop.pipeline.PipelineExecutionConfiguration;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePainter;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.config.PipelineRunConfiguration;
import org.apache.hop.pipeline.debug.PipelineDebugMeta;
import org.apache.hop.pipeline.debug.StepDebugMeta;
import org.apache.hop.pipeline.engine.IEngineComponent;
import org.apache.hop.pipeline.engine.IPipelineEngine;
import org.apache.hop.pipeline.engine.PipelineEngineFactory;
import org.apache.hop.pipeline.step.RowDistributionInterface;
import org.apache.hop.pipeline.step.RowDistributionPluginType;
import org.apache.hop.pipeline.step.StepErrorMeta;
import org.apache.hop.pipeline.step.StepIOMetaInterface;
import org.apache.hop.pipeline.step.StepMeta;
import org.apache.hop.pipeline.step.errorhandling.Stream;
import org.apache.hop.pipeline.step.errorhandling.StreamIcon;
import org.apache.hop.pipeline.step.errorhandling.StreamInterface;
import org.apache.hop.pipeline.step.errorhandling.StreamInterface.StreamType;
import org.apache.hop.pipeline.steps.tableinput.TableInputMeta;
import org.apache.hop.ui.core.ConstUI;
import org.apache.hop.ui.core.PrintSpool;
import org.apache.hop.ui.core.PropsUI;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.EnterStringDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.PreviewRowsDialog;
import org.apache.hop.ui.core.dialog.StepFieldsDialog;
import org.apache.hop.ui.core.gui.GUIResource;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.core.widget.CheckBoxToolTip;
import org.apache.hop.ui.core.widget.CheckBoxToolTipListener;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.context.GuiContextUtil;
import org.apache.hop.ui.hopgui.context.IGuiContextHandler;
import org.apache.hop.ui.hopgui.delegates.HopGuiSlaveDelegate;
import org.apache.hop.ui.hopgui.dialog.EnterPreviewRowsDialog;
import org.apache.hop.ui.hopgui.dialog.NotePadDialog;
import org.apache.hop.ui.hopgui.dialog.SearchFieldsProgressDialog;
import org.apache.hop.ui.hopgui.file.HopFileTypeHandlerInterface;
import org.apache.hop.ui.hopgui.file.delegates.HopGuiNotePadDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.extension.HopGuiPipelineGraphExtension;
import org.apache.hop.ui.hopgui.file.shared.DelayTimer;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineContext;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineNoteContext;
import org.apache.hop.ui.hopgui.file.pipeline.context.HopGuiPipelineStepContext;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineClipboardDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineGridDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineHopDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineLogDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineMetricsDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelinePerfDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelinePreviewDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineRunDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineStepDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.delegates.HopGuiPipelineUndoDelegate;
import org.apache.hop.ui.hopgui.file.pipeline.extension.HopGuiPipelinePainterFlyoutTooltipExtension;
import org.apache.hop.ui.hopgui.perspective.dataorch.HopDataOrchestrationPerspective;
import org.apache.hop.ui.hopgui.perspective.dataorch.HopGuiAbstractGraph;
import org.apache.hop.ui.hopgui.shared.SWTGC;
import org.apache.hop.ui.hopgui.shared.SwtScrollBar;
import org.apache.hop.ui.pipeline.dialog.PipelineDialog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.printing.Printer;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * This class handles the display of the pipelines in a graphical way using icons, arrows, etc. One pipeline
 * is handled per HopGuiPipelineGraph
 *
 * @author Matt
 * @since 17-mei-2003
 */
@GuiPlugin(
  description = "The pipeline graph GUI plugin"
)
public class HopGuiPipelineGraph extends HopGuiAbstractGraph
  implements Redrawable, MouseListener, MouseMoveListener, MouseTrackListener, MouseWheelListener, KeyListener,
  HasLogChannelInterface, LogParentProvidedInterface,  // TODO: Aren't these the same?
  HopFileTypeHandlerInterface,
  IGuiRefresher {

  private static Class<?> PKG = HopGui.class; // for i18n purposes, needed by Translator!!

  public static final String GUI_PLUGIN_TOOLBAR_PARENT_ID = "HopGuiPipelineGraph-Toolbar";
  public static final String TOOLBAR_ITEM_START = "HopGuiPipelineGraph-ToolBar-10010-Run";
  public static final String TOOLBAR_ITEM_STOP = "HopGuiPipelineGraph-ToolBar-10030-Stop";
  public static final String TOOLBAR_ITEM_PAUSE = "HopGuiPipelineGraph-ToolBar-10020-Pause";
  public static final String TOOLBAR_ITEM_PREVIEW = "HopGuiPipelineGraph-ToolBar-10040-Preview";
  public static final String TOOLBAR_ITEM_DEBUG = "HopGuiPipelineGraph-ToolBar-10045-Debug";

  public static final String TOOLBAR_ITEM_UNDO_ID = "HopGuiPipelineGraph-ToolBar-10100-Undo";
  public static final String TOOLBAR_ITEM_REDO_ID = "HopGuiPipelineGraph-ToolBar-10110-Redo";

  public static final String TOOLBAR_ITEM_SNAP_TO_GRID = "HopGuiPipelineGraph-ToolBar-10190-Snap-To-Grid";
  public static final String TOOLBAR_ITEM_ALIGN_LEFT = "HopGuiPipelineGraph-ToolBar-10200-Align-Left";
  public static final String TOOLBAR_ITEM_ALIGN_RIGHT = "HopGuiPipelineGraph-ToolBar-10210-Align-Right";
  public static final String TOOLBAR_ITEM_ALIGN_TOP = "HopGuiPipelineGraph-ToolBar-10250-Align-Ttop";
  public static final String TOOLBAR_ITEM_ALIGN_BOTTOM = "HopGuiPipelineGraph-ToolBar-10260-Align-Bottom";
  public static final String TOOLBAR_ITEM_DISTRIBUTE_HORIZONTALLY = "HopGuiPipelineGraph-ToolBar-10300-Distribute-Horizontally";
  public static final String TOOLBAR_ITEM_DISTRIBUTE_VERTICALLY = "HopGuiPipelineGraph-ToolBar-10310-Distribute-Vertically";

  public static final String TOOLBAR_ITEM_SHOW_EXECUTION_RESULTS = "HopGuiPipelineGraph-ToolBar-10400-Execution-Results";

  public static final String LOAD_TAB = "loadTab";
  public static final String PREVIEW_PIPELINE = "previewPipeline";

  private LogChannelInterface log;

  private static final int HOP_SEL_MARGIN = 9;

  public static final String START_TEXT = BaseMessages.getString( PKG, "PipelineLog.Button.StartPipeline" );

  public static final String PAUSE_TEXT = BaseMessages.getString( PKG, "PipelineLog.Button.PausePipeline" );

  public static final String RESUME_TEXT = BaseMessages.getString( PKG, "PipelineLog.Button.ResumePipeline" );

  public static final String STOP_TEXT = BaseMessages.getString( PKG, "PipelineLog.Button.StopPipeline" );

  public static final String PIPELINE_GRAPH_ENTRY_SNIFF = "pipeline-graph-entry-sniff";

  public static final String PIPELINE_GRAPH_ENTRY_AGAIN = "pipeline-graph-entry-align";

  private static final int TOOLTIP_HIDE_DELAY_SHORT = 5000;

  private static final int TOOLTIP_HIDE_DELAY_LONG = 10000;

  private PipelineMeta pipelineMeta;
  public IPipelineEngine<PipelineMeta> pipeline;

  private final HopDataOrchestrationPerspective perspective;

  private Composite mainComposite;

  private DefaultToolTip toolTip;

  private CheckBoxToolTip helpTip;

  private ToolBar toolBar;
  private GuiCompositeWidgets toolBarWidgets;

  private int iconsize;

  private Point lastclick;

  private Point lastMove;

  private Point[] previous_step_locations;

  private Point[] previous_note_locations;

  private List<StepMeta> selectedSteps;

  private StepMeta selectedStep;

  private List<NotePadMeta> selectedNotes;

  private NotePadMeta selectedNote;

  private PipelineHopMeta candidate;

  private Point drop_candidate;

  private boolean split_hop;

  private int lastButton;

  private PipelineHopMeta last_hop_split;

  private org.apache.hop.core.gui.Rectangle selectionRegion;

  /**
   * A list of remarks on the current Pipeline...
   */
  private List<CheckResultInterface> remarks;

  /**
   * A list of impacts of the current pipeline on the used databases.
   */
  private List<DatabaseImpact> impact;

  /**
   * Indicates whether or not an impact analysis has already run.
   */
  private boolean impactFinished;

  private PipelineDebugMeta lastPipelineDebugMeta;

  protected int currentMouseX = 0;

  protected int currentMouseY = 0;

  protected NotePadMeta ni = null;

  protected PipelineHopMeta currentHop;

  protected StepMeta currentStep;

  private List<AreaOwner> areaOwners;

  // private Text filenameLabel;
  private SashForm sashForm;

  public Composite extraViewComposite;

  public CTabFolder extraViewTabFolder;

  private boolean initialized;

  private boolean running;

  private boolean halted;

  private boolean halting;

  private boolean safeStopping;

  private boolean debug;

  private boolean pausing;

  public HopGuiPipelineLogDelegate pipelineLogDelegate;
  public HopGuiPipelineGridDelegate pipelineGridDelegate;
  public HopGuiPipelineMetricsDelegate pipelineMetricsDelegate;
  public HopGuiPipelinePreviewDelegate pipelinePreviewDelegate;
  public HopGuiPipelineRunDelegate pipelineRunDelegate;
  public HopGuiPipelineStepDelegate pipelineStepDelegate;
  public HopGuiPipelineClipboardDelegate pipelineClipboardDelegate;
  public HopGuiPipelineHopDelegate pipelineHopDelegate;
  public HopGuiPipelineUndoDelegate pipelineUndoDelegate;
  public HopGuiPipelinePerfDelegate pipelinePerfDelegate;

  public HopGuiSlaveDelegate slaveDelegate;
  public HopGuiNotePadDelegate notePadDelegate;

  public List<SelectedStepListener> stepListeners;
  public List<StepSelectionListener> currentStepListeners = new ArrayList<>();

  /**
   * A map that keeps track of which log line was written by which step
   */
  private Map<String, String> stepLogMap;

  private StepMeta startHopStep;
  private Point endHopLocation;
  private boolean startErrorHopStep;

  private StepMeta noInputStep;

  private StepMeta endHopStep;

  private StreamType candidateHopType;

  private Map<StepMeta, DelayTimer> delayTimers;

  private StepMeta showTargetStreamsStep;

  Timer redrawTimer;
  private ToolItem stopItem;
  private Combo zoomLabel;

  private HopPipelineFileType fileType;

  public void setCurrentNote( NotePadMeta ni ) {
    this.ni = ni;
  }

  public NotePadMeta getCurrentNote() {
    return ni;
  }

  public PipelineHopMeta getCurrentHop() {
    return currentHop;
  }

  public void setCurrentHop( PipelineHopMeta currentHop ) {
    this.currentHop = currentHop;
  }

  public StepMeta getCurrentStep() {
    return currentStep;
  }

  public void setCurrentStep( StepMeta currentStep ) {
    this.currentStep = currentStep;
  }

  public void addSelectedStepListener( SelectedStepListener selectedStepListener ) {
    stepListeners.add( selectedStepListener );
  }

  public void addCurrentStepListener( StepSelectionListener stepSelectionListener ) {
    currentStepListeners.add( stepSelectionListener );
  }

  public HopGuiPipelineGraph( Composite parent, final HopGui hopUi, final CTabItem parentTabItem,
                              final HopDataOrchestrationPerspective perspective, final PipelineMeta pipelineMeta, final HopPipelineFileType fileType ) {
    super( hopUi, parent, SWT.NONE, parentTabItem );
    this.hopUi = hopUi;
    this.parentTabItem = parentTabItem;
    this.perspective = perspective;
    this.pipelineMeta = pipelineMeta;
    this.fileType = fileType;
    this.areaOwners = new ArrayList<>();

    this.log = hopUi.getLog();

    this.delayTimers = new HashMap<>();

    pipelineLogDelegate = new HopGuiPipelineLogDelegate( hopUi, this );
    pipelineGridDelegate = new HopGuiPipelineGridDelegate( hopUi, this );
    pipelinePerfDelegate = new HopGuiPipelinePerfDelegate( hopUi, this );
    pipelineMetricsDelegate = new HopGuiPipelineMetricsDelegate( hopUi, this );
    pipelinePreviewDelegate = new HopGuiPipelinePreviewDelegate( hopUi, this );
    pipelineClipboardDelegate = new HopGuiPipelineClipboardDelegate( hopUi, this );
    pipelineStepDelegate = new HopGuiPipelineStepDelegate( hopUi, this );
    pipelineHopDelegate = new HopGuiPipelineHopDelegate( hopUi, this );
    pipelineUndoDelegate = new HopGuiPipelineUndoDelegate( hopUi, this );
    pipelineRunDelegate = new HopGuiPipelineRunDelegate( hopUi, this );
    pipelinePerfDelegate = new HopGuiPipelinePerfDelegate( hopUi, this );

    slaveDelegate = new HopGuiSlaveDelegate( hopUi, this );
    notePadDelegate = new HopGuiNotePadDelegate( hopUi, this );

    stepListeners = new ArrayList<>();

    // This composite takes up all the space in the parent
    //
    FormData formData = new FormData();
    formData.left = new FormAttachment( 0, 0 );
    formData.top = new FormAttachment( 0, 0 );
    formData.right = new FormAttachment( 100, 0 );
    formData.bottom = new FormAttachment( 100, 0 );
    setLayoutData( formData );

    // The layout in the widget is done using a FormLayout
    //
    setLayout( new FormLayout() );

    // Add a tool-bar at the top of the tab
    // The form-data is set on the native widget automatically
    //
    addToolBar();

    // The main composite contains the graph view, but if needed also
    // a view with an extra tab containing log, etc.
    //
    mainComposite = new Composite( this, SWT.NONE );
    mainComposite.setBackground( GUIResource.getInstance().getColorOrange() );
    mainComposite.setLayout( new FormLayout() );
    FormData fdMainComposite = new FormData();
    fdMainComposite.left = new FormAttachment( 0, 0 );
    fdMainComposite.top = new FormAttachment( 0, toolBar.getBounds().height ); // Position below toolbar
    fdMainComposite.right = new FormAttachment( 100, 0 );
    fdMainComposite.bottom = new FormAttachment( 100, 0 );
    mainComposite.setLayoutData( fdMainComposite );


    // To allow for a splitter later on, we will add the splitter here...
    //
    sashForm = new SashForm( mainComposite, SWT.VERTICAL );
    FormData fdSashForm = new FormData();
    fdSashForm.left = new FormAttachment( 0, 0 );
    fdSashForm.top = new FormAttachment( 0, 0 );
    fdSashForm.right = new FormAttachment( 100, 0 );
    fdSashForm.bottom = new FormAttachment( 100, 0 );
    sashForm.setLayoutData( fdSashForm );

    // Add a canvas below it, use up all space initially
    //
    canvas = new Canvas( sashForm, SWT.V_SCROLL | SWT.H_SCROLL | SWT.NO_BACKGROUND | SWT.BORDER );
    FormData fdCanvas = new FormData();
    fdCanvas.left = new FormAttachment( 0, 0 );
    fdCanvas.top = new FormAttachment( 0, 0 );
    fdCanvas.right = new FormAttachment( 100, 0 );
    fdCanvas.bottom = new FormAttachment( 100, 0 );
    canvas.setLayoutData( fdCanvas );

    sashForm.setWeights( new int[] { 100, } );


    toolTip = new DefaultToolTip( canvas, ToolTip.NO_RECREATE, true );
    toolTip.setRespectMonitorBounds( true );
    toolTip.setRespectDisplayBounds( true );
    toolTip.setPopupDelay( 350 );
    toolTip.setHideDelay( TOOLTIP_HIDE_DELAY_SHORT );
    toolTip.setShift( new org.eclipse.swt.graphics.Point( ConstUI.TOOLTIP_OFFSET, ConstUI.TOOLTIP_OFFSET ) );

    helpTip = new CheckBoxToolTip( canvas );
    helpTip.addCheckBoxToolTipListener( new CheckBoxToolTipListener() {

      @Override
      public void checkBoxSelected( boolean enabled ) {
        hopUi.getProps().setShowingHelpToolTips( enabled );
      }
    } );

    iconsize = hopUi.getProps().getIconSize();

    clearSettings();

    remarks = new ArrayList<>();
    impact = new ArrayList<>();
    impactFinished = false;

    hori = canvas.getHorizontalBar();
    vert = canvas.getVerticalBar();

    hori.addSelectionListener( new SelectionAdapter() {
      @Override
      public void widgetSelected( SelectionEvent e ) {
        redraw();
      }
    } );
    vert.addSelectionListener( new SelectionAdapter() {
      @Override
      public void widgetSelected( SelectionEvent e ) {
        redraw();
      }
    } );
    hori.setThumb( 100 );
    vert.setThumb( 100 );

    hori.setVisible( true );
    vert.setVisible( true );

    setVisible( true );
    newProps();

    canvas.setBackground( GUIResource.getInstance().getColorBlueCustomGrid() );

    canvas.addPaintListener( new PaintListener() {
      @Override
      public void paintControl( PaintEvent e ) {
        // if ( !hopUi.isStopped() ) {
        HopGuiPipelineGraph.this.paintControl( e );
        // }
      }
    } );

    selectedSteps = null;
    lastclick = null;

    /*
     * Handle the mouse...
     */

    canvas.addMouseListener( this );
    canvas.addMouseMoveListener( this );
    canvas.addMouseTrackListener( this );
    canvas.addMouseWheelListener( this );
    canvas.addKeyListener( this );

    // Drag & Drop for steps
    Transfer[] ttypes = new Transfer[] { XMLTransfer.getInstance() };
    DropTarget ddTarget = new DropTarget( canvas, DND.DROP_MOVE );
    ddTarget.setTransfer( ttypes );
    ddTarget.addDropListener( new DropTargetListener() {
      @Override
      public void dragEnter( DropTargetEvent event ) {
        clearSettings();

        drop_candidate = PropsUI.calculateGridPosition( getRealPosition( canvas, event.x, event.y ) );

        redraw();
      }

      @Override
      public void dragLeave( DropTargetEvent event ) {
        drop_candidate = null;
        redraw();
      }

      @Override
      public void dragOperationChanged( DropTargetEvent event ) {
      }

      @Override
      public void dragOver( DropTargetEvent event ) {
        drop_candidate = PropsUI.calculateGridPosition( getRealPosition( canvas, event.x, event.y ) );

        redraw();
      }

      @Override
      public void drop( DropTargetEvent event ) {
        // no data to copy, indicate failure in event.detail
        if ( event.data == null ) {
          event.detail = DND.DROP_NONE;
          return;
        }

        // What's the real drop position?
        Point p = getRealPosition( canvas, event.x, event.y );

        //
        // We expect a Drag and Drop container... (encased in XML)
        try {
          DragAndDropContainer container = (DragAndDropContainer) event.data;

          StepMeta stepMeta = null;
          boolean newstep = false;

          switch ( container.getType() ) {
            // Put an existing one on the canvas.
            case DragAndDropContainer.TYPE_STEP:
              // Drop hidden step onto canvas....
              stepMeta = pipelineMeta.findStep( container.getData() );
              if ( stepMeta != null ) {
                if ( pipelineMeta.isStepUsedInPipelineHops( stepMeta ) ) {
                  modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.StepIsAlreadyOnCanvas.Title" ),
                    BaseMessages.getString( PKG, "PipelineGraph.Dialog.StepIsAlreadyOnCanvas.Message" ), SWT.OK );
                  return;
                }
                // This step gets the drawn attribute and position set below.
              } else {
                // Unknown step dropped: ignore this to be safe!
                return;
              }
              break;

            // Create a new step
            case DragAndDropContainer.TYPE_BASE_STEP_TYPE:
              // Not an existing step: data refers to the type of step to create
              String id = container.getId();
              String name = container.getData();
              stepMeta = pipelineStepDelegate.newStep( pipelineMeta, id, name, name, false, true, p );
              if ( stepMeta != null ) {
                newstep = true;
              } else {
                return; // Cancelled pressed in dialog or unable to create step.
              }
              break;

            // Create a new TableInput step using the selected connection...
            case DragAndDropContainer.TYPE_DATABASE_CONNECTION:
              newstep = true;
              String connectionName = container.getData();
              TableInputMeta tii = new TableInputMeta();
              tii.setDatabaseMeta( pipelineMeta.findDatabase( connectionName ) );
              PluginRegistry registry = PluginRegistry.getInstance();
              String stepID = registry.getPluginId( StepPluginType.class, tii );
              PluginInterface stepPlugin = registry.findPluginWithId( StepPluginType.class, stepID );
              String stepName = pipelineMeta.getAlternativeStepname( stepPlugin.getName() );
              stepMeta = new StepMeta( stepID, stepName, tii );
              if ( pipelineStepDelegate.editStep( pipelineMeta, stepMeta ) != null ) {
                pipelineMeta.addStep( stepMeta );
                redraw();
              } else {
                return;
              }
              break;

            // Drag hop on the canvas: create a new Hop...
            case DragAndDropContainer.TYPE_PIPELINE_HOP:
              newHop();
              return;

            default:
              // Nothing we can use: give an error!
              modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.ItemCanNotBePlacedOnCanvas.Title" ),
                BaseMessages.getString( PKG, "PipelineGraph.Dialog.ItemCanNotBePlacedOnCanvas.Message" ), SWT.OK );
              return;
          }

          pipelineMeta.unselectAll();

          StepMeta before = null;
          if ( !newstep ) {
            before = (StepMeta) stepMeta.clone();
          }


          stepMeta.setSelected( true );
          PropsUI.setLocation( stepMeta, p.x, p.y );

          if ( newstep ) {
            hopUi.undoDelegate.addUndoNew( pipelineMeta, new StepMeta[] { stepMeta }, new int[] { pipelineMeta.indexOfStep( stepMeta ) } );
          } else {
            hopUi.undoDelegate.addUndoChange( pipelineMeta, new StepMeta[] { before }, new StepMeta[] { (StepMeta) stepMeta.clone() },
              new int[] { pipelineMeta.indexOfStep( stepMeta ) } );
          }

          forceFocus();
          redraw();

          // See if we want to draw a tool tip explaining how to create new hops...
          //
          if ( newstep && pipelineMeta.nrSteps() > 1 && pipelineMeta.nrSteps() < 5 && hopUi.getProps().isShowingHelpToolTips() ) {
            showHelpTip( p.x, p.y, BaseMessages.getString( PKG, "PipelineGraph.HelpToolTip.CreatingHops.Title" ),
              BaseMessages.getString( PKG, "PipelineGraph.HelpToolTip.CreatingHops.Message" ) );
          }
        } catch ( Exception e ) {
          new ErrorDialog( hopShell(), BaseMessages.getString( PKG, "PipelineGraph.Dialog.ErrorDroppingObject.Message" ),
            BaseMessages.getString( PKG, "PipelineGraph.Dialog.ErrorDroppingObject.Title" ), e );
        }
      }

      @Override
      public void dropAccept( DropTargetEvent event ) {
      }
    } );

    setBackground( GUIResource.getInstance().getColorBackground() );

    // Add keyboard listeners from the main GUI and this class (toolbar etc) to the canvas. That's where the focus should be
    //
    hopUi.replaceKeyboardShortcutListeners( this );

    // Update menu, toolbar, force redraw canvas
    //
    updateGui();
  }


  @Override
  public void mouseDoubleClick( MouseEvent e ) {
    clearSettings();

    Point real = screen2real( e.x, e.y );

    // Hide the tooltip!
    hideToolTips();

    /** TODO: Add back in
     try {
     ExtensionPointHandler.callExtensionPoint( LogChannel.GENERAL, HopExtensionPoint.PipelineGraphMouseDoubleClick.id,
     new HopGuiPipelineGraphExtension( this, e, real ) );
     } catch ( Exception ex ) {
     LogChannel.GENERAL.logError( "Error calling PipelineGraphMouseDoubleClick extension point", ex );
     }
     **/

    StepMeta stepMeta = pipelineMeta.getStep( real.x, real.y, iconsize );
    if ( stepMeta != null ) {
      if ( e.button == 1 ) {
        editStep( stepMeta );
      } else {
        editDescription( stepMeta );
      }
    } else {
      // Check if point lies on one of the many hop-lines...
      PipelineHopMeta online = findHop( real.x, real.y );
      if ( online != null ) {
        editHop( online );
      } else {
        NotePadMeta ni = pipelineMeta.getNote( real.x, real.y );
        if ( ni != null ) {
          selectedNote = null;
          editNote( ni );
        } else {
          // See if the double click was in one of the area's...
          //
          boolean hit = false;
          for ( AreaOwner areaOwner : areaOwners ) {
            if ( areaOwner.contains( real.x, real.y ) ) {
              if ( areaOwner.getParent() instanceof StepMeta
                && areaOwner.getOwner().equals( PipelinePainter.STRING_PARTITIONING_CURRENT_STEP ) ) {
                StepMeta step = (StepMeta) areaOwner.getParent();
                pipelineStepDelegate.editStepPartitioning( pipelineMeta, step );
                hit = true;
                break;
              }
            }
          }

          if ( !hit ) {
            editPipelineProperties( new HopGuiPipelineContext( pipelineMeta, this, real ) );
          }

        }
      }
    }
  }

  @Override
  public void mouseDown( MouseEvent e ) {

    boolean alt = ( e.stateMask & SWT.ALT ) != 0;
    boolean control = ( e.stateMask & SWT.MOD1 ) != 0;
    boolean shift = ( e.stateMask & SWT.SHIFT ) != 0;

    lastButton = e.button;
    Point real = screen2real( e.x, e.y );
    lastclick = new Point( real.x, real.y );

    // Hide the tooltip!
    hideToolTips();

    // Set the pop-up menu
    if ( e.button == 3 ) {
      setMenu( real.x, real.y );
      return;
    }

    try {
      ExtensionPointHandler.callExtensionPoint( LogChannel.GENERAL, HopExtensionPoint.PipelineGraphMouseDown.id, new HopGuiPipelineGraphExtension( this, e, real ) );
    } catch ( Exception ex ) {
      LogChannel.GENERAL.logError( "Error calling PipelineGraphMouseDown extension point", ex );
    }

    // A single left or middle click on one of the area owners...
    //
    if ( e.button == 1 || e.button == 2 ) {
      AreaOwner areaOwner = getVisibleAreaOwner( real.x, real.y );
      if ( areaOwner != null && areaOwner.getAreaType() != null ) {
        switch ( areaOwner.getAreaType() ) {
          case STEP_OUTPUT_HOP_ICON:
            // Click on the output icon means: start of drag
            // Action: We show the input icons on the other steps...
            //
            selectedStep = null;
            startHopStep = (StepMeta) areaOwner.getParent();
            candidateHopType = null;
            startErrorHopStep = false;
            break;

          case STEP_INPUT_HOP_ICON:
            // Click on the input icon means: start to a new hop
            // In this case, we set the end hop step...
            //
            selectedStep = null;
            startHopStep = null;
            endHopStep = (StepMeta) areaOwner.getParent();
            candidateHopType = null;
            startErrorHopStep = false;
            break;

          case HOP_ERROR_ICON:
            // Click on the error icon means: Edit error handling
            //
            StepMeta stepMeta = (StepMeta) areaOwner.getParent();
            pipelineStepDelegate.editStepErrorHandling( pipelineMeta, stepMeta );
            break;

          case STEP_TARGET_HOP_ICON_OPTION:
            // Below, see showStepTargetOptions()
            break;

          case STEP_EDIT_ICON:
            clearSettings();
            currentStep = (StepMeta) areaOwner.getParent();
            editStep();
            break;

          case STEP_INJECT_ICON:
            modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.StepInjectionSupported.Title" ),
              BaseMessages.getString( PKG, "PipelineGraph.StepInjectionSupported.Tooltip" ), SWT.OK | SWT.ICON_INFORMATION );
            break;

          case STEP_MENU_ICON:
            clearSettings();
            stepMeta = (StepMeta) areaOwner.getParent();
            setMenu( stepMeta.getLocation().x, stepMeta.getLocation().y );
            break;

          case STEP_ICON:
            stepMeta = (StepMeta) areaOwner.getOwner();
            currentStep = stepMeta;

            for ( StepSelectionListener listener : currentStepListeners ) {
              listener.onUpdateSelection( currentStep );
            }

            if ( candidate != null ) {
              addCandidateAsHop( e.x, e.y );
            }
            // ALT-Click: edit error handling
            //
            if ( e.button == 1 && alt && stepMeta.supportsErrorHandling() ) {
              pipelineStepDelegate.editStepErrorHandling( pipelineMeta, stepMeta );
              return;
            } else if ( e.button == 1 && startHopStep != null && endHopStep == null ) {
              candidate = new PipelineHopMeta( startHopStep, currentStep );
              addCandidateAsHop( e.x, e.y );
            } else if ( e.button == 2 || ( e.button == 1 && shift ) ) {
              // SHIFT CLICK is start of drag to create a new hop
              //
              startHopStep = stepMeta;
            } else {
              selectedSteps = pipelineMeta.getSelectedSteps();
              selectedStep = stepMeta;
              //
              // When an icon is moved that is not selected, it gets
              // selected too late.
              // It is not captured here, but in the mouseMoveListener...
              //
              previous_step_locations = pipelineMeta.getSelectedStepLocations();

              Point p = stepMeta.getLocation();
              iconoffset = new Point( real.x - p.x, real.y - p.y );
            }
            redraw();
            break;

          case NOTE:
            ni = (NotePadMeta) areaOwner.getOwner();
            selectedNotes = pipelineMeta.getSelectedNotes();
            selectedNote = ni;
            Point loc = ni.getLocation();

            previous_note_locations = pipelineMeta.getSelectedNoteLocations();

            noteoffset = new Point( real.x - loc.x, real.y - loc.y );

            redraw();
            break;

          case STEP_COPIES_TEXT:
            copies( (StepMeta) areaOwner.getOwner() );
            break;

          case STEP_DATA_SERVICE:
            editProperties( pipelineMeta, hopUi, true, PipelineDialog.Tabs.EXTRA_TAB );
            break;
          default:
            break;
        }
      } else {
        // A hop? --> enable/disable
        //
        PipelineHopMeta hop = findHop( real.x, real.y );
        if ( hop != null ) {
          PipelineHopMeta before = (PipelineHopMeta) hop.clone();
          setHopEnabled( hop, !hop.isEnabled() );
          if ( hop.isEnabled() && pipelineMeta.hasLoop( hop.getToStep() ) ) {
            setHopEnabled( hop, false );
            modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Title" ),
              BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Message" ), SWT.OK | SWT.ICON_ERROR );
          }
          PipelineHopMeta after = (PipelineHopMeta) hop.clone();
          hopUi.undoDelegate.addUndoChange( pipelineMeta, new PipelineHopMeta[] { before }, new PipelineHopMeta[] { after }, new int[] { pipelineMeta.indexOfPipelineHop( hop ) } );

          updateGui();
        } else {
          // No area-owner & no hop means : background click:
          //
          startHopStep = null;
          if ( !control ) {
            selectionRegion = new org.apache.hop.core.gui.Rectangle( real.x, real.y, 0, 0 );
          }
          updateGui();
        }
      }
    }
  }

  private enum SingleClickType {
    Pipeline,
    Step,
    Note
  }

  @Override
  public void mouseUp( MouseEvent e ) {
    try {
      HopGuiPipelineGraphExtension ext = new HopGuiPipelineGraphExtension( null, e, getArea() );
      ExtensionPointHandler.callExtensionPoint( LogChannel.GENERAL, HopExtensionPoint.PipelineGraphMouseUp.id, ext );
      if ( ext.isPreventDefault() ) {
        redraw();
        clearSettings();
        return;
      }
    } catch ( Exception ex ) {
      LogChannel.GENERAL.logError( "Error calling PipelineGraphMouseUp extension point", ex );
    }

    boolean control = ( e.stateMask & SWT.MOD1 ) != 0;
    PipelineHopMeta selectedHop = findHop( e.x, e.y );
    updateErrorMetaForHop( selectedHop );
    boolean singleClick = false;
    SingleClickType singleClickType = null;
    StepMeta singleClickStep = null;
    NotePadMeta singleClickNote = null;

    if ( iconoffset == null ) {
      iconoffset = new Point( 0, 0 );
    }
    Point real = screen2real( e.x, e.y );
    Point icon = new Point( real.x - iconoffset.x, real.y - iconoffset.y );
    AreaOwner areaOwner = getVisibleAreaOwner( real.x, real.y );

    try {
      HopGuiPipelineGraphExtension ext = new HopGuiPipelineGraphExtension( this, e, real );
      ExtensionPointHandler.callExtensionPoint( LogChannel.GENERAL, HopExtensionPoint.PipelineGraphMouseUp.id, ext );
      if ( ext.isPreventDefault() ) {
        redraw();
        clearSettings();
        return;
      }
    } catch ( Exception ex ) {
      LogChannel.GENERAL.logError( "Error calling PipelineGraphMouseUp extension point", ex );
    }

    // Quick new hop option? (drag from one step to another)
    //
    if ( candidate != null && areaOwner != null && areaOwner.getAreaType() != null ) {
      switch ( areaOwner.getAreaType() ) {
        case STEP_ICON:
          currentStep = (StepMeta) areaOwner.getOwner();
          break;
        case STEP_INPUT_HOP_ICON:
          currentStep = (StepMeta) areaOwner.getParent();
          break;
        default:
          break;
      }
      addCandidateAsHop( e.x, e.y );
      redraw();
    } else {
      // Did we select a region on the screen? Mark steps in region as
      // selected
      //
      if ( selectionRegion != null ) {
        selectionRegion.width = real.x - selectionRegion.x;
        selectionRegion.height = real.y - selectionRegion.y;
        if ( selectionRegion.width == 0 && selectionRegion.height == 0 ) {
          singleClick = true;
          singleClickType = SingleClickType.Pipeline;
        }
        pipelineMeta.unselectAll();
        selectInRect( pipelineMeta, selectionRegion );
        selectionRegion = null;
        updateGui();
      } else {
        // Clicked on an icon?
        //
        if ( selectedStep != null && startHopStep == null ) {
          if ( e.button == 1 ) {
            Point realclick = screen2real( e.x, e.y );
            if ( lastclick.x == realclick.x && lastclick.y == realclick.y ) {
              // Flip selection when control is pressed!
              if ( control ) {
                selectedStep.flipSelected();
              } else {
                singleClick = true;
                singleClickType = SingleClickType.Step;
                singleClickStep = selectedStep;
              }
            } else {
              // Find out which Steps & Notes are selected
              selectedSteps = pipelineMeta.getSelectedSteps();
              selectedNotes = pipelineMeta.getSelectedNotes();

              // We moved around some items: store undo info...
              //
              boolean also = false;
              if ( selectedNotes != null && selectedNotes.size() > 0 && previous_note_locations != null ) {
                int[] indexes = pipelineMeta.getNoteIndexes( selectedNotes );

                also = selectedSteps != null && selectedSteps.size() > 0;
                hopUi.undoDelegate.addUndoPosition( pipelineMeta, selectedNotes.toArray( new NotePadMeta[ selectedNotes.size() ] ),
                  indexes, previous_note_locations, pipelineMeta.getSelectedNoteLocations(), also );
              }
              if ( selectedSteps != null && previous_step_locations != null ) {
                int[] indexes = pipelineMeta.getStepIndexes( selectedSteps );
                hopUi.undoDelegate.addUndoPosition( pipelineMeta, selectedSteps.toArray( new StepMeta[ selectedSteps.size() ] ), indexes,
                  previous_step_locations, pipelineMeta.getSelectedStepLocations(), also );
              }
            }
          }

          // OK, we moved the step, did we move it across a hop?
          // If so, ask to split the hop!
          if ( split_hop ) {
            PipelineHopMeta hi = findHop( icon.x + iconsize / 2, icon.y + iconsize / 2, selectedStep );
            if ( hi != null ) {
              splitHop( hi );
            }
            split_hop = false;
          }

          selectedSteps = null;
          selectedNotes = null;
          selectedStep = null;
          selectedNote = null;
          startHopStep = null;
          endHopLocation = null;

          updateGui();
        } else {
          // Notes?
          //

          if ( selectedNote != null ) {
            if ( e.button == 1 ) {
              if ( lastclick.x == real.x && lastclick.y == real.y ) {
                // Flip selection when control is pressed!
                if ( control ) {
                  selectedNote.flipSelected();
                } else {
                  // single click on a note: ask what needs to happen...
                  //
                  singleClick = true;
                  singleClickType = SingleClickType.Note;
                  singleClickNote = selectedNote;
                }
              } else {
                // Find out which Steps & Notes are selected
                selectedSteps = pipelineMeta.getSelectedSteps();
                selectedNotes = pipelineMeta.getSelectedNotes();

                // We moved around some items: store undo info...

                boolean also = false;
                if ( selectedNotes != null && selectedNotes.size() > 0 && previous_note_locations != null ) {
                  int[] indexes = pipelineMeta.getNoteIndexes( selectedNotes );
                  hopUi.undoDelegate.addUndoPosition( pipelineMeta, selectedNotes.toArray( new NotePadMeta[ selectedNotes.size() ] ), indexes,
                    previous_note_locations, pipelineMeta.getSelectedNoteLocations(), also );
                  also = selectedSteps != null && selectedSteps.size() > 0;
                }
                if ( selectedSteps != null && selectedSteps.size() > 0 && previous_step_locations != null ) {
                  int[] indexes = pipelineMeta.getStepIndexes( selectedSteps );
                  hopUi.undoDelegate.addUndoPosition( pipelineMeta, selectedSteps.toArray( new StepMeta[ selectedSteps.size() ] ), indexes,
                    previous_step_locations, pipelineMeta.getSelectedStepLocations(), also );
                }
              }
            }

            selectedNotes = null;
            selectedSteps = null;
            selectedStep = null;
            selectedNote = null;
            startHopStep = null;
            endHopLocation = null;
          }
        }
      }
    }

    // Just a single click on the background:
    // We have a bunch of possible actions for you...
    //
    if ( singleClick && singleClickType != null ) {
      IGuiContextHandler contextHandler = null;
      String message = null;
      switch ( singleClickType ) {
        case Pipeline:
          message = "Select the action to execute or the step to create:";
          contextHandler = new HopGuiPipelineContext( pipelineMeta, this, real );
          break;
        case Step:
          message = "Select the action to take on step '" + singleClickStep.getName() + "':";
          contextHandler = new HopGuiPipelineStepContext( pipelineMeta, singleClickStep, this, real );
          break;
        case Note:
          message = "Select the note action to take:";
          contextHandler = new HopGuiPipelineNoteContext( pipelineMeta, singleClickNote, this, real );
          break;
        default:
          break;
      }
      if ( contextHandler != null ) {
        Shell parent = hopShell();
        org.eclipse.swt.graphics.Point p = parent.getDisplay().map( canvas, null, e.x, e.y );
        GuiContextUtil.handleActionSelection( parent, message, new Point( p.x, p.y ), contextHandler.getSupportedActions() );
      }
    }

    lastButton = 0;
  }

  private void splitHop( PipelineHopMeta hi ) {
    int id = 0;
    if ( !hopUi.getProps().getAutoSplit() ) {
      MessageDialogWithToggle md =
        new MessageDialogWithToggle( hopShell(), BaseMessages.getString( PKG, "PipelineGraph.Dialog.SplitHop.Title" ), null,
          BaseMessages.getString( PKG, "PipelineGraph.Dialog.SplitHop.Message" ) + Const.CR + hi.toString(),
          MessageDialog.QUESTION, new String[] { BaseMessages.getString( PKG, "System.Button.Yes" ),
          BaseMessages.getString( PKG, "System.Button.No" ) }, 0, BaseMessages.getString( PKG,
          "PipelineGraph.Dialog.Option.SplitHop.DoNotAskAgain" ), hopUi.getProps().getAutoSplit() );
      MessageDialogWithToggle.setDefaultImage( GUIResource.getInstance().getImageHopUi() );
      id = md.open();
      hopUi.getProps().setAutoSplit( md.getToggleState() );
    }

    if ( ( id & 0xFF ) == 0 ) { // Means: "Yes" button clicked!

      // Only split A-->--B by putting C in between IF...
      // C-->--A or B-->--C don't exists...
      // A ==> hi.getFromStep()
      // B ==> hi.getToStep();
      // C ==> selected_step
      //
      boolean caExists = pipelineMeta.findPipelineHop( selectedStep, hi.getFromStep() ) != null;
      boolean bcExists = pipelineMeta.findPipelineHop( hi.getToStep(), selectedStep ) != null;
      if ( !caExists && !bcExists ) {

        StepMeta fromStep = hi.getFromStep();
        StepMeta toStep = hi.getToStep();

        // In case step A targets B then we now need to target C
        //
        StepIOMetaInterface fromIo = fromStep.getStepMetaInterface().getStepIOMeta();
        for ( StreamInterface stream : fromIo.getTargetStreams() ) {
          if ( stream.getStepMeta() != null && stream.getStepMeta().equals( toStep ) ) {
            // This target stream was directed to B, now we need to direct it to C
            stream.setStepMeta( selectedStep );
            fromStep.getStepMetaInterface().handleStreamSelection( stream );
          }
        }

        // In case step B sources from A then we now need to source from C
        //
        StepIOMetaInterface toIo = toStep.getStepMetaInterface().getStepIOMeta();
        for ( StreamInterface stream : toIo.getInfoStreams() ) {
          if ( stream.getStepMeta() != null && stream.getStepMeta().equals( fromStep ) ) {
            // This info stream was reading from B, now we need to direct it to C
            stream.setStepMeta( selectedStep );
            toStep.getStepMetaInterface().handleStreamSelection( stream );
          }
        }

        // In case there is error handling on A, we want to make it point to C now
        //
        StepErrorMeta errorMeta = fromStep.getStepErrorMeta();
        if ( fromStep.isDoingErrorHandling() && toStep.equals( errorMeta.getTargetStep() ) ) {
          errorMeta.setTargetStep( selectedStep );
        }

        PipelineHopMeta newhop1 = new PipelineHopMeta( hi.getFromStep(), selectedStep );
        if ( pipelineMeta.findPipelineHop( newhop1 ) == null ) {
          pipelineMeta.addPipelineHop( newhop1 );
          hopUi.undoDelegate.addUndoNew( pipelineMeta, new PipelineHopMeta[] { newhop1, }, new int[] { pipelineMeta.indexOfPipelineHop( newhop1 ), }, true );
        }
        PipelineHopMeta newhop2 = new PipelineHopMeta( selectedStep, hi.getToStep() );
        if ( pipelineMeta.findPipelineHop( newhop2 ) == null ) {
          pipelineMeta.addPipelineHop( newhop2 );
          hopUi.undoDelegate.addUndoNew( pipelineMeta, new PipelineHopMeta[] { newhop2 }, new int[] { pipelineMeta.indexOfPipelineHop( newhop2 ) }, true );
        }
        int idx = pipelineMeta.indexOfPipelineHop( hi );

        hopUi.undoDelegate.addUndoDelete( pipelineMeta, new PipelineHopMeta[] { hi }, new int[] { idx }, true );
        pipelineMeta.removePipelineHop( idx );

        redraw();
      }

      // else: Silently discard this hop-split attempt.
    }
  }

  @Override
  public void mouseMove( MouseEvent e ) {
    boolean shift = ( e.stateMask & SWT.SHIFT ) != 0;
    noInputStep = null;

    // disable the tooltip
    //
    toolTip.hide();
    toolTip.setHideDelay( TOOLTIP_HIDE_DELAY_SHORT );

    Point real = screen2real( e.x, e.y );

    currentMouseX = real.x;
    currentMouseY = real.y;

    // Remember the last position of the mouse for paste with keyboard
    //
    lastMove = real;

    if ( iconoffset == null ) {
      iconoffset = new Point( 0, 0 );
    }
    Point icon = new Point( real.x - iconoffset.x, real.y - iconoffset.y );

    if ( noteoffset == null ) {
      noteoffset = new Point( 0, 0 );
    }
    Point note = new Point( real.x - noteoffset.x, real.y - noteoffset.y );

    // Moved over an area?
    //
    AreaOwner areaOwner = getVisibleAreaOwner( real.x, real.y );
    if ( areaOwner != null && areaOwner.getAreaType() != null ) {
      switch ( areaOwner.getAreaType() ) {
        case STEP_ICON:
          StepMeta stepMeta = (StepMeta) areaOwner.getOwner();
          resetDelayTimer( stepMeta );
          break;

        case MINI_ICONS_BALLOON: // Give the timer a bit more time
          stepMeta = (StepMeta) areaOwner.getParent();
          resetDelayTimer( stepMeta );
          break;

        default:
          break;
      }
    }

    try {
      HopGuiPipelineGraphExtension ext = new HopGuiPipelineGraphExtension( this, e, real );
      ExtensionPointHandler.callExtensionPoint( LogChannel.GENERAL, HopExtensionPoint.PipelineGraphMouseMoved.id, ext );
    } catch ( Exception ex ) {
      LogChannel.GENERAL.logError( "Error calling PipelineGraphMouseMoved extension point", ex );
    }

    //
    // First see if the icon we clicked on was selected.
    // If the icon was not selected, we should un-select all other
    // icons, selected and move only the one icon
    //
    if ( selectedStep != null && !selectedStep.isSelected() ) {
      pipelineMeta.unselectAll();
      selectedStep.setSelected( true );
      selectedSteps = new ArrayList<>();
      selectedSteps.add( selectedStep );
      previous_step_locations = new Point[] { selectedStep.getLocation() };
      redraw();
    } else if ( selectedNote != null && !selectedNote.isSelected() ) {
      pipelineMeta.unselectAll();
      selectedNote.setSelected( true );
      selectedNotes = new ArrayList<>();
      selectedNotes.add( selectedNote );
      previous_note_locations = new Point[] { selectedNote.getLocation() };
      redraw();
    } else if ( selectionRegion != null && startHopStep == null ) {
      // Did we select a region...?
      //

      selectionRegion.width = real.x - selectionRegion.x;
      selectionRegion.height = real.y - selectionRegion.y;
      redraw();
    } else if ( selectedStep != null && lastButton == 1 && !shift && startHopStep == null ) {
      //
      // One or more icons are selected and moved around...
      //
      // new : new position of the ICON (not the mouse pointer) dx : difference with previous position
      //
      int dx = icon.x - selectedStep.getLocation().x;
      int dy = icon.y - selectedStep.getLocation().y;

      // See if we have a hop-split candidate
      //
      PipelineHopMeta hi = findHop( icon.x + iconsize / 2, icon.y + iconsize / 2, selectedStep );
      if ( hi != null ) {
        // OK, we want to split the hop in 2
        //
        if ( !hi.getFromStep().equals( selectedStep ) && !hi.getToStep().equals( selectedStep ) ) {
          split_hop = true;
          last_hop_split = hi;
          hi.split = true;
        }
      } else {
        if ( last_hop_split != null ) {
          last_hop_split.split = false;
          last_hop_split = null;
          split_hop = false;
        }
      }

      selectedNotes = pipelineMeta.getSelectedNotes();
      selectedSteps = pipelineMeta.getSelectedSteps();

      // Adjust location of selected steps...
      if ( selectedSteps != null ) {
        for ( int i = 0; i < selectedSteps.size(); i++ ) {
          StepMeta stepMeta = selectedSteps.get( i );
          PropsUI.setLocation( stepMeta, stepMeta.getLocation().x + dx, stepMeta.getLocation().y + dy );
        }
      }
      // Adjust location of selected hops...
      if ( selectedNotes != null ) {
        for ( int i = 0; i < selectedNotes.size(); i++ ) {
          NotePadMeta ni = selectedNotes.get( i );
          PropsUI.setLocation( ni, ni.getLocation().x + dx, ni.getLocation().y + dy );
        }
      }

      redraw();
    } else if ( ( startHopStep != null && endHopStep == null ) || ( endHopStep != null && startHopStep == null ) ) {
      // Are we creating a new hop with the middle button or pressing SHIFT?
      //

      StepMeta stepMeta = pipelineMeta.getStep( real.x, real.y, iconsize );
      endHopLocation = new Point( real.x, real.y );
      if ( stepMeta != null
        && ( ( startHopStep != null && !startHopStep.equals( stepMeta ) ) || ( endHopStep != null && !endHopStep
        .equals( stepMeta ) ) ) ) {
        StepIOMetaInterface ioMeta = stepMeta.getStepMetaInterface().getStepIOMeta();
        if ( candidate == null ) {
          // See if the step accepts input. If not, we can't create a new hop...
          //
          if ( startHopStep != null ) {
            if ( ioMeta.isInputAcceptor() ) {
              candidate = new PipelineHopMeta( startHopStep, stepMeta );
              endHopLocation = null;
            } else {
              noInputStep = stepMeta;
              toolTip.setImage( null );
              toolTip.setText( "This step does not accept any input from other steps" );
              toolTip.show( new org.eclipse.swt.graphics.Point( real.x, real.y ) );
            }
          } else if ( endHopStep != null ) {
            if ( ioMeta.isOutputProducer() ) {
              candidate = new PipelineHopMeta( stepMeta, endHopStep );
              endHopLocation = null;
            } else {
              noInputStep = stepMeta;
              toolTip.setImage( null );
              toolTip
                .setText( "This step doesn't pass any output to other steps. (except perhaps for targetted output)" );
              toolTip.show( new org.eclipse.swt.graphics.Point( real.x, real.y ) );
            }
          }
        }
      } else {
        if ( candidate != null ) {
          candidate = null;
          redraw();
        }
      }

      redraw();
    }

    // Move around notes & steps
    //
    if ( selectedNote != null ) {
      if ( lastButton == 1 && !shift ) {
        /*
         * One or more notes are selected and moved around...
         *
         * new : new position of the note (not the mouse pointer) dx : difference with previous position
         */
        int dx = note.x - selectedNote.getLocation().x;
        int dy = note.y - selectedNote.getLocation().y;

        selectedNotes = pipelineMeta.getSelectedNotes();
        selectedSteps = pipelineMeta.getSelectedSteps();

        // Adjust location of selected steps...
        if ( selectedSteps != null ) {
          for ( int i = 0; i < selectedSteps.size(); i++ ) {
            StepMeta stepMeta = selectedSteps.get( i );
            PropsUI.setLocation( stepMeta, stepMeta.getLocation().x + dx, stepMeta.getLocation().y + dy );
          }
        }
        // Adjust location of selected hops...
        if ( selectedNotes != null ) {
          for ( int i = 0; i < selectedNotes.size(); i++ ) {
            NotePadMeta ni = selectedNotes.get( i );
            PropsUI.setLocation( ni, ni.getLocation().x + dx, ni.getLocation().y + dy );
          }
        }

        redraw();
      }
    }
  }

  @Override
  public void mouseHover( MouseEvent e ) {

    boolean tip = true;
    boolean isDeprecated = false;

    toolTip.hide();
    toolTip.setHideDelay( TOOLTIP_HIDE_DELAY_SHORT );
    Point real = screen2real( e.x, e.y );

    AreaOwner areaOwner = getVisibleAreaOwner( real.x, real.y );
    if ( areaOwner != null && areaOwner.getAreaType() != null ) {
      switch ( areaOwner.getAreaType() ) {
        default:
          break;
      }
    }

    // Show a tool tip upon mouse-over of an object on the canvas
    if ( ( tip && !helpTip.isVisible() ) || isDeprecated ) {
      setToolTip( real.x, real.y, e.x, e.y );
    }
  }

  @Override
  public void mouseScrolled( MouseEvent e ) {
    /*
     * if (e.count == 3) { // scroll up zoomIn(); } else if (e.count == -3) { // scroll down zoomOut(); } }
     */
  }

  private void addCandidateAsHop( int mouseX, int mouseY ) {

    boolean forward = startHopStep != null;

    StepMeta fromStep = candidate.getFromStep();
    StepMeta toStep = candidate.getToStep();

    // See what the options are.
    // - Does the source step has multiple stream options?
    // - Does the target step have multiple input stream options?
    //
    List<StreamInterface> streams = new ArrayList<>();

    StepIOMetaInterface fromIoMeta = fromStep.getStepMetaInterface().getStepIOMeta();
    List<StreamInterface> targetStreams = fromIoMeta.getTargetStreams();
    if ( forward ) {
      streams.addAll( targetStreams );
    }

    StepIOMetaInterface toIoMeta = toStep.getStepMetaInterface().getStepIOMeta();
    List<StreamInterface> infoStreams = toIoMeta.getInfoStreams();
    if ( !forward ) {
      streams.addAll( infoStreams );
    }

    if ( forward ) {
      if ( fromIoMeta.isOutputProducer() && toStep.equals( currentStep ) ) {
        streams.add( new Stream( StreamType.OUTPUT, fromStep, BaseMessages
          .getString( PKG, "HopGui.Hop.MainOutputOfStep" ), StreamIcon.OUTPUT, null ) );
      }

      if ( fromStep.supportsErrorHandling() && toStep.equals( currentStep ) ) {
        streams.add( new Stream( StreamType.ERROR, fromStep, BaseMessages.getString( PKG,
          "HopGui.Hop.ErrorHandlingOfStep" ), StreamIcon.ERROR, null ) );
      }
    } else {
      if ( toIoMeta.isInputAcceptor() && fromStep.equals( currentStep ) ) {
        streams.add( new Stream( StreamType.INPUT, toStep, BaseMessages.getString( PKG, "HopGui.Hop.MainInputOfStep" ),
          StreamIcon.INPUT, null ) );
      }

      if ( fromStep.supportsErrorHandling() && fromStep.equals( currentStep ) ) {
        streams.add( new Stream( StreamType.ERROR, fromStep, BaseMessages.getString( PKG,
          "HopGui.Hop.ErrorHandlingOfStep" ), StreamIcon.ERROR, null ) );
      }
    }

    // Targets can be dynamically added to this step...
    //
    if ( forward ) {
      streams.addAll( fromStep.getStepMetaInterface().getOptionalStreams() );
    } else {
      streams.addAll( toStep.getStepMetaInterface().getOptionalStreams() );
    }

    // Show a list of options on the canvas...
    //
    if ( streams.size() > 1 ) {
      // Show a pop-up menu with all the possible options...
      //
      Menu menu = new Menu( canvas );
      for ( final StreamInterface stream : streams ) {
        MenuItem item = new MenuItem( menu, SWT.NONE );
        item.setText( Const.NVL( stream.getDescription(), "" ) );
        item.setImage( getImageFor( stream ) );
        item.addSelectionListener( new SelectionAdapter() {
          @Override
          public void widgetSelected( SelectionEvent e ) {
            addHop( stream );
          }
        } );
      }
      menu.setLocation( canvas.toDisplay( mouseX, mouseY ) );
      menu.setVisible( true );

      return;
    }
    if ( streams.size() == 1 ) {
      addHop( streams.get( 0 ) );
    } else {
      return;
    }

    /*
     *
     * if (pipelineMeta.findPipelineHop(candidate) == null) { spoon.newHop(pipelineMeta, candidate); } if (startErrorHopStep) {
     * addErrorHop(); } if (startTargetHopStream != null) { // Auto-configure the target in the source step... //
     * startTargetHopStream.setStepMeta(candidate.getToStep());
     * startTargetHopStream.setStepname(candidate.getToStep().getName()); startTargetHopStream = null; }
     */
    candidate = null;
    selectedSteps = null;
    startHopStep = null;
    endHopLocation = null;
    startErrorHopStep = false;

    // redraw();
  }

  private Image getImageFor( StreamInterface stream ) {
    Display disp = hopDisplay();
    SwtUniversalImage swtImage = SWTGC.getNativeImage( BasePainter.getStreamIconImage( stream.getStreamIcon() ) );
    return swtImage.getAsBitmapForSize( disp, ConstUI.SMALL_ICON_SIZE, ConstUI.SMALL_ICON_SIZE );
  }

  protected void addHop( StreamInterface stream ) {
    switch ( stream.getStreamType() ) {
      case ERROR:
        addErrorHop();
        candidate.setErrorHop( true );
        pipelineHopDelegate.newHop( pipelineMeta, candidate );
        break;
      case INPUT:
        pipelineHopDelegate.newHop( pipelineMeta, candidate );
        break;
      case OUTPUT:
        StepErrorMeta stepErrorMeta = candidate.getFromStep().getStepErrorMeta();
        if ( stepErrorMeta != null && stepErrorMeta.getTargetStep() != null ) {
          if ( stepErrorMeta.getTargetStep().equals( candidate.getToStep() ) ) {
            candidate.getFromStep().setStepErrorMeta( null );
          }
        }
        pipelineHopDelegate.newHop( pipelineMeta, candidate );
        break;
      case INFO:
        stream.setStepMeta( candidate.getFromStep() );
        candidate.getToStep().getStepMetaInterface().handleStreamSelection( stream );
        pipelineHopDelegate.newHop( pipelineMeta, candidate );
        break;
      case TARGET:
        // We connect a target of the source step to an output step...
        //
        stream.setStepMeta( candidate.getToStep() );
        candidate.getFromStep().getStepMetaInterface().handleStreamSelection( stream );
        pipelineHopDelegate.newHop( pipelineMeta, candidate );
        break;
      default:
        break;

    }
    clearSettings();
  }

  private void addErrorHop() {
    // Automatically configure the step error handling too!
    //
    if ( candidate == null || candidate.getFromStep() == null ) {
      return;
    }
    StepErrorMeta errorMeta = candidate.getFromStep().getStepErrorMeta();
    if ( errorMeta == null ) {
      errorMeta = new StepErrorMeta( pipelineMeta, candidate.getFromStep() );
    }
    errorMeta.setEnabled( true );
    errorMeta.setTargetStep( candidate.getToStep() );
    candidate.getFromStep().setStepErrorMeta( errorMeta );
  }

  private void resetDelayTimer( StepMeta stepMeta ) {
    DelayTimer delayTimer = delayTimers.get( stepMeta );
    if ( delayTimer != null ) {
      delayTimer.reset();
    }
  }

  @Override
  public void mouseEnter( MouseEvent arg0 ) {
  }

  @Override
  public void mouseExit( MouseEvent arg0 ) {
  }


  protected void asyncRedraw() {
    hopDisplay().asyncExec( new Runnable() {
      @Override
      public void run() {
        if ( !HopGuiPipelineGraph.this.isDisposed() ) {
          HopGuiPipelineGraph.this.redraw();
        }
      }
    } );
  }

  private void addToolBar() {

    try {
      // Create a new toolbar at the top of the main composite...
      //
      toolBar = new ToolBar( this, SWT.WRAP | SWT.LEFT | SWT.HORIZONTAL );
      toolBarWidgets = new GuiCompositeWidgets( HopGui.getInstance().getVariableSpace() );
      toolBarWidgets.createCompositeWidgets( this, null, toolBar, GUI_PLUGIN_TOOLBAR_PARENT_ID, null );
      FormData layoutData = new FormData();
      layoutData.left = new FormAttachment( 0, 0 );
      layoutData.top = new FormAttachment( 0, 0 );
      layoutData.right = new FormAttachment( 100, 0 );
      toolBar.setLayoutData( layoutData );
      toolBar.pack();

      // Add a zoom label widget: TODO: move to GuiElement
      //
      new ToolItem( toolBar, SWT.SEPARATOR );
      ToolItem sep = new ToolItem( toolBar, SWT.SEPARATOR );

      zoomLabel = new Combo( toolBar, SWT.DROP_DOWN );
      zoomLabel.setItems( PipelinePainter.magnificationDescriptions );
      zoomLabel.addSelectionListener( new SelectionAdapter() {
        @Override
        public void widgetSelected( SelectionEvent arg0 ) {
          readMagnification();
        }
      } );

      zoomLabel.addKeyListener( new KeyAdapter() {
        @Override
        public void keyPressed( KeyEvent event ) {
          if ( event.character == SWT.CR ) {
            readMagnification();
          }
        }
      } );

      setZoomLabel();
      zoomLabel.pack();
      zoomLabel.layout( true, true );
      sep.setWidth( zoomLabel.getBounds().width );
      sep.setControl( zoomLabel );
      toolBar.pack();

      // enable / disable the icons in the toolbar too.
      //
      updateGui();

    } catch ( Throwable t ) {
      log.logError( "Error setting up the navigation toolbar for HopUI", t );
      new ErrorDialog( hopShell(), "Error", "Error setting up the navigation toolbar for HopGUI", new Exception( t ) );
    }
  }

  public void setZoomLabel() {
    String newString = Integer.toString( Math.round( magnification * 100 ) ) + "%";
    String oldString = zoomLabel.getText();
    if ( !newString.equals( oldString ) ) {
      zoomLabel.setText( Integer.toString( Math.round( magnification * 100 ) ) + "%" );
    }
  }

  /**
   * Allows for magnifying to any percentage entered by the user...
   */
  private void readMagnification() {
    String possibleText = zoomLabel.getText();
    possibleText = possibleText.replace( "%", "" );

    float possibleFloatMagnification;
    try {
      possibleFloatMagnification = Float.parseFloat( possibleText ) / 100;
      magnification = possibleFloatMagnification;
      if ( zoomLabel.getText().indexOf( '%' ) < 0 ) {
        zoomLabel.setText( zoomLabel.getText().concat( "%" ) );
      }
    } catch ( Exception e ) {
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.InvalidZoomMeasurement.Title" ),
        BaseMessages.getString( PKG, "PipelineGraph.Dialog.InvalidZoomMeasurement.Message", zoomLabel.getText() ),
        SWT.YES | SWT.ICON_ERROR );
    }
    redraw();
  }

  protected void hideToolTips() {
    toolTip.hide();
    helpTip.hide();
    toolTip.setHideDelay( TOOLTIP_HIDE_DELAY_SHORT );
  }

  private void showHelpTip( int x, int y, String tipTitle, String tipMessage ) {

    helpTip.setTitle( tipTitle );
    helpTip.setMessage( tipMessage.replaceAll( "\n", Const.CR ) );
    helpTip
      .setCheckBoxMessage( BaseMessages.getString( PKG, "PipelineGraph.HelpToolTip.DoNotShowAnyMoreCheckBox.Message" ) );

    // helpTip.hide();
    // int iconSize = spoon.props.getIconSize();
    org.eclipse.swt.graphics.Point location = new org.eclipse.swt.graphics.Point( x - 5, y - 5 );

    helpTip.show( location );
  }

  /**
   * Select all the steps in a certain (screen) rectangle
   *
   * @param rect The selection area as a rectangle
   */
  public void selectInRect( PipelineMeta pipelineMeta, org.apache.hop.core.gui.Rectangle rect ) {
    if ( rect.height < 0 || rect.width < 0 ) {
      org.apache.hop.core.gui.Rectangle rectified =
        new org.apache.hop.core.gui.Rectangle( rect.x, rect.y, rect.width, rect.height );

      // Only for people not dragging from left top to right bottom
      if ( rectified.height < 0 ) {
        rectified.y = rectified.y + rectified.height;
        rectified.height = -rectified.height;
      }
      if ( rectified.width < 0 ) {
        rectified.x = rectified.x + rectified.width;
        rectified.width = -rectified.width;
      }
      rect = rectified;
    }

    for ( int i = 0; i < pipelineMeta.nrSteps(); i++ ) {
      StepMeta stepMeta = pipelineMeta.getStep( i );
      Point a = stepMeta.getLocation();
      if ( rect.contains( a.x, a.y ) ) {
        stepMeta.setSelected( true );
      }
    }

    for ( int i = 0; i < pipelineMeta.nrNotes(); i++ ) {
      NotePadMeta ni = pipelineMeta.getNote( i );
      Point a = ni.getLocation();
      Point b = new Point( a.x + ni.width, a.y + ni.height );
      if ( rect.contains( a.x, a.y ) && rect.contains( b.x, b.y ) ) {
        ni.setSelected( true );
      }
    }
  }

  @Override
  public void keyPressed( KeyEvent e ) {

    // CTRL-UP : allignTop();
    if ( e.keyCode == SWT.ARROW_UP && ( e.stateMask & SWT.MOD1 ) != 0 ) {
      alignTop();
    }
    // CTRL-DOWN : allignBottom();
    if ( e.keyCode == SWT.ARROW_DOWN && ( e.stateMask & SWT.MOD1 ) != 0 ) {
      alignBottom();
    }
    // CTRL-LEFT : allignleft();
    if ( e.keyCode == SWT.ARROW_LEFT && ( e.stateMask & SWT.MOD1 ) != 0 ) {
      alignLeft();
    }
    // CTRL-RIGHT : allignRight();
    if ( e.keyCode == SWT.ARROW_RIGHT && ( e.stateMask & SWT.MOD1 ) != 0 ) {
      alignRight();
    }
    // ALT-RIGHT : distributeHorizontal();
    if ( e.keyCode == SWT.ARROW_RIGHT && ( e.stateMask & SWT.ALT ) != 0 ) {
      distributeHorizontal();
    }
    // ALT-UP : distributeVertical();
    if ( e.keyCode == SWT.ARROW_UP && ( e.stateMask & SWT.ALT ) != 0 ) {
      distributeVertical();
    }
    // ALT-HOME : snap to grid
    if ( e.keyCode == SWT.HOME && ( e.stateMask & SWT.ALT ) != 0 ) {
      snapToGrid( ConstUI.GRID_SIZE );
    }

    if ( e.character == 'E' && ( e.stateMask & SWT.CTRL ) != 0 ) {
      checkErrorVisuals();
    }

    // SPACE : over a step: show output fields...
    if ( e.character == ' ' && lastMove != null ) {

      Point real = lastMove;

      // Hide the tooltip!
      hideToolTips();

      // Set the pop-up menu
      StepMeta stepMeta = pipelineMeta.getStep( real.x, real.y, iconsize );
      if ( stepMeta != null ) {
        // OK, we found a step, show the output fields...
        inputOutputFields( stepMeta, false );
      }
    }
  }

  @Override
  public void keyReleased( KeyEvent e ) {
  }

  @Override
  public boolean setFocus() {
    return ( canvas != null && !canvas.isDisposed() ) ? canvas.setFocus() : false;
  }

  public void renameStep( StepMeta stepMeta, String stepname ) {
    String newname = stepname;

    StepMeta smeta = pipelineMeta.findStep( newname, stepMeta );
    int nr = 2;
    while ( smeta != null ) {
      newname = stepname + " " + nr;
      smeta = pipelineMeta.findStep( newname );
      nr++;
    }
    if ( nr > 2 ) {
      stepname = newname;
      modalMessageDialog( BaseMessages.getString( PKG, "HopGui.Dialog.StepnameExists.Title" ),
        BaseMessages.getString( PKG, "HopGui.Dialog.StepnameExists.Message", stepname ), SWT.OK | SWT.ICON_INFORMATION );
    }
    stepMeta.setName( stepname );
    stepMeta.setChanged();
    redraw();
  }

  public void clearSettings() {
    selectedStep = null;
    noInputStep = null;
    selectedNote = null;
    selectedSteps = null;
    selectionRegion = null;
    candidate = null;
    last_hop_split = null;
    lastButton = 0;
    iconoffset = null;
    startHopStep = null;
    endHopStep = null;
    endHopLocation = null;
    pipelineMeta.unselectAll();
    for ( int i = 0; i < pipelineMeta.nrPipelineHops(); i++ ) {
      pipelineMeta.getPipelineHop( i ).split = false;
    }
  }

  public String[] getDropStrings( String str, String sep ) {
    StringTokenizer strtok = new StringTokenizer( str, sep );
    String[] retval = new String[ strtok.countTokens() ];
    int i = 0;
    while ( strtok.hasMoreElements() ) {
      retval[ i ] = strtok.nextToken();
      i++;
    }
    return retval;
  }

  public Point getRealPosition( Composite canvas, int x, int y ) {
    Point p = new Point( 0, 0 );
    Composite follow = canvas;
    while ( follow != null ) {
      org.eclipse.swt.graphics.Point loc = follow.getLocation();
      Point xy = new Point( loc.x, loc.y );
      p.x += xy.x;
      p.y += xy.y;
      follow = follow.getParent();
    }

    int offsetX = -16;
    int offsetY = -64;
    if ( Const.isOSX() ) {
      offsetX = -2;
      offsetY = -24;
    }
    p.x = x - p.x + offsetX;
    p.y = y - p.y + offsetY;

    return screen2real( p.x, p.y );
  }

  /**
   * See if location (x,y) is on a line between two steps: the hop!
   *
   * @param x
   * @param y
   * @return the pipeline hop on the specified location, otherwise: null
   */
  protected PipelineHopMeta findHop( int x, int y ) {
    return findHop( x, y, null );
  }

  /**
   * See if location (x,y) is on a line between two steps: the hop!
   *
   * @param x
   * @param y
   * @param exclude the step to exclude from the hops (from or to location). Specify null if no step is to be excluded.
   * @return the pipeline hop on the specified location, otherwise: null
   */
  private PipelineHopMeta findHop( int x, int y, StepMeta exclude ) {
    int i;
    PipelineHopMeta online = null;
    for ( i = 0; i < pipelineMeta.nrPipelineHops(); i++ ) {
      PipelineHopMeta hi = pipelineMeta.getPipelineHop( i );
      StepMeta fs = hi.getFromStep();
      StepMeta ts = hi.getToStep();

      if ( fs == null || ts == null ) {
        return null;
      }

      // If either the "from" or "to" step is excluded, skip this hop.
      //
      if ( exclude != null && ( exclude.equals( fs ) || exclude.equals( ts ) ) ) {
        continue;
      }

      int[] line = getLine( fs, ts );

      if ( pointOnLine( x, y, line ) ) {
        online = hi;
      }
    }
    return online;
  }

  private int[] getLine( StepMeta fs, StepMeta ts ) {
    Point from = fs.getLocation();
    Point to = ts.getLocation();
    offset = getOffset();

    int x1 = from.x + iconsize / 2;
    int y1 = from.y + iconsize / 2;

    int x2 = to.x + iconsize / 2;
    int y2 = to.y + iconsize / 2;

    return new int[] { x1, y1, x2, y2 };
  }

  public void detachStep() {
    detach( getCurrentStep() );
    selectedSteps = null;
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10700-partitioning",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Specify step partitioning",
    tooltip = "Specify how rows of data need to be grouped into partitions allowing parallel execution where similar rows need to end up on the same step copy",
    image = "ui/images/partition_schema.svg"
  )
  public void partitioning( HopGuiPipelineStepContext context ) {
    pipelineStepDelegate.editStepPartitioning( pipelineMeta, context.getStepMeta() );
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10800-error-handling",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Step error handling",
    tooltip = "Specify how error handling is behaving for this step",
    image = "ui/images/step_error.svg"
  )
  public void errorHandling( HopGuiPipelineStepContext context ) {
    pipelineStepDelegate.editStepErrorHandling( pipelineMeta, context.getStepMeta() );
  }

  public void newHopChoice() {
    selectedSteps = null;
    newHop();
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10000-edit",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Edit the step",
    tooltip = "Edit the step properties",
    image = "ui/images/Edit.svg"
  )
  public void editStep( HopGuiPipelineStepContext context ) {
    editStep( context.getStepMeta() );
  }

  public void editStep() {
    selectedSteps = null;
    editStep( getCurrentStep() );
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10800-edit-description",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Edit step description",
    tooltip = "Modify the step description",
    image = "ui/images/Edit.svg"
  )
  public void editDescription( HopGuiPipelineStepContext context ) {
    editDescription( context.getStepMeta() );
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10600-rows-distrubute",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Distribute rows",
    tooltip = "Make the step distribute rows to next steps",
    image = "ui/images/Edit.svg"
  )
  public void setDistributes( HopGuiPipelineStepContext context ) {
    context.getStepMeta().setDistributes( true );
    context.getStepMeta().setRowDistribution( null );
    redraw();
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10500-custom-row-distribution",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Specify row distribution",
    tooltip = "Specify how the step should distribute rows to next steps",
    image = "ui/images/Edit.svg"
  )
  public void setCustomRowDistribution( HopGuiPipelineStepContext context ) {
    // ask user which row distribution is needed...
    //
    RowDistributionInterface rowDistribution = askUserForCustomDistributionMethod();
    context.getStepMeta().setDistributes( true );
    context.getStepMeta().setRowDistribution( rowDistribution );
    redraw();
  }

  public RowDistributionInterface askUserForCustomDistributionMethod() {
    List<PluginInterface> plugins = PluginRegistry.getInstance().getPlugins( RowDistributionPluginType.class );
    if ( Utils.isEmpty( plugins ) ) {
      return null;
    }
    List<String> choices = new ArrayList<>();
    for ( PluginInterface plugin : plugins ) {
      choices.add( plugin.getName() + " : " + plugin.getDescription() );
    }
    EnterSelectionDialog dialog =
      new EnterSelectionDialog( hopShell(), choices.toArray( new String[ choices.size() ] ), "Select distribution method",
        "Please select the row distribution method:" );
    if ( dialog.open() != null ) {
      PluginInterface plugin = plugins.get( dialog.getSelectionNr() );
      try {
        return (RowDistributionInterface) PluginRegistry.getInstance().loadClass( plugin );
      } catch ( Exception e ) {
        new ErrorDialog( hopShell(), "Error", "Error loading row distribution plugin class", e );
        return null;
      }
    } else {
      return null;
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10100-copies",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Set the number of step copies",
    tooltip = "Set the number of step copies to use during execution",
    image = "ui/images/parallel-hop.svg"
  )
  public void copies( HopGuiPipelineStepContext context ) {
    StepMeta stepMeta = context.getStepMeta();
    copies( stepMeta );
  }

  public void copies( StepMeta stepMeta ) {
    final boolean multipleOK = checkNumberOfCopies( pipelineMeta, stepMeta );
    selectedSteps = null;
    String tt = BaseMessages.getString( PKG, "PipelineGraph.Dialog.NrOfCopiesOfStep.Title" );
    String mt = BaseMessages.getString( PKG, "PipelineGraph.Dialog.NrOfCopiesOfStep.Message" );
    EnterStringDialog nd = new EnterStringDialog( hopShell(), stepMeta.getCopiesString(), tt, mt, true, pipelineMeta );
    String cop = nd.open();
    if ( !Utils.isEmpty( cop ) ) {

      int copies = Const.toInt( pipelineMeta.environmentSubstitute( cop ), -1 );
      if ( copies > 1 && !multipleOK ) {
        cop = "1";

        modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.MultipleCopiesAreNotAllowedHere.Title" ),
          BaseMessages.getString( PKG, "PipelineGraph.Dialog.MultipleCopiesAreNotAllowedHere.Message" ), SWT.YES | SWT.ICON_WARNING );
      }
      String cps = stepMeta.getCopiesString();
      if ( ( cps != null && !cps.equals( cop ) ) || ( cps == null && cop != null ) ) {
        stepMeta.setChanged();
      }
      stepMeta.setCopiesString( cop );
      redraw();
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10900-delete",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Delete,
    name = "Delete this step",
    tooltip = "Delete the selected step from the pipeline",
    image = "ui/images/generic-delete.svg"
  )
  public void delStep( HopGuiPipelineStepContext context ) {
    delSelected( context.getStepMeta() );
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10200-fields-before",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Info,
    name = "Show the fields entering this step",
    tooltip = "Show all the fields entering this step",
    image = "ui/images/info-hop.svg"
  )
  public void fieldsBefore( HopGuiPipelineStepContext context ) {
    selectedSteps = null;
    inputOutputFields( context.getStepMeta(), true );
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10300-fields-after",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Info,
    name = "Show the fields exiting this step",
    tooltip = "Show all the fields resulting from this step",
    image = "ui/images/info-hop.svg"
  )
  public void fieldsAfter( HopGuiPipelineStepContext context ) {
    selectedSteps = null;
    inputOutputFields( context.getStepMeta(), false );
  }

  public void fieldsLineage() {
    PipelineDataLineage tdl = new PipelineDataLineage( pipelineMeta );
    try {
      tdl.calculateLineage();
    } catch ( Exception e ) {
      new ErrorDialog( hopShell(), "Lineage error", "Unexpected lineage calculation error", e );
    }
  }

  public void editHop() {
    selectionRegion = null;
    editHop( getCurrentHop() );
  }

  public void flipHopDirection() {
    selectionRegion = null;
    PipelineHopMeta hi = getCurrentHop();

    hi.flip();
    if ( pipelineMeta.hasLoop( hi.getToStep() ) ) {
      redraw();
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.LoopsAreNotAllowed.Title" ),
        BaseMessages.getString( PKG, "PipelineGraph.Dialog.LoopsAreNotAllowed.Message" ), SWT.OK | SWT.ICON_ERROR );
      hi.flip();
      redraw();
    } else {
      hi.setChanged();

      updateGui();
    }
  }

  public void enableHop() {
    selectionRegion = null;
    PipelineHopMeta hi = getCurrentHop();
    PipelineHopMeta before = (PipelineHopMeta) hi.clone();
    setHopEnabled( hi, !hi.isEnabled() );
    if ( hi.isEnabled() && pipelineMeta.hasLoop( hi.getToStep() ) ) {
      setHopEnabled( hi, false );
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.LoopAfterHopEnabled.Title" ),
        BaseMessages.getString( PKG, "PipelineGraph.Dialog.LoopAfterHopEnabled.Message" ), SWT.OK | SWT.ICON_ERROR );
    } else {
      PipelineHopMeta after = (PipelineHopMeta) hi.clone();
      hopUi.undoDelegate.addUndoChange( pipelineMeta, new PipelineHopMeta[] { before }, new PipelineHopMeta[] { after }, new int[] { pipelineMeta.indexOfPipelineHop( hi ) } );
      redraw();
    }
    updateErrorMetaForHop( hi );
  }

  public void deleteHop() {
    selectionRegion = null;
    PipelineHopMeta hi = getCurrentHop();
    pipelineHopDelegate.delHop( pipelineMeta, hi );
  }

  private void updateErrorMetaForHop( PipelineHopMeta hop ) {
    if ( hop != null && hop.isErrorHop() ) {
      StepErrorMeta errorMeta = hop.getFromStep().getStepErrorMeta();
      if ( errorMeta != null ) {
        errorMeta.setEnabled( hop.isEnabled() );
      }
    }
  }

  public void enableHopsBetweenSelectedSteps() {
    enableHopsBetweenSelectedSteps( true );
  }

  public void disableHopsBetweenSelectedSteps() {
    enableHopsBetweenSelectedSteps( false );
  }

  /**
   * This method enables or disables all the hops between the selected steps.
   **/
  public void enableHopsBetweenSelectedSteps( boolean enabled ) {
    List<StepMeta> list = pipelineMeta.getSelectedSteps();

    boolean hasLoop = false;

    for ( int i = 0; i < pipelineMeta.nrPipelineHops(); i++ ) {
      PipelineHopMeta hop = pipelineMeta.getPipelineHop( i );
      if ( list.contains( hop.getFromStep() ) && list.contains( hop.getToStep() ) ) {

        PipelineHopMeta before = (PipelineHopMeta) hop.clone();
        setHopEnabled( hop, enabled );
        PipelineHopMeta after = (PipelineHopMeta) hop.clone();
        hopUi.undoDelegate.addUndoChange( pipelineMeta, new PipelineHopMeta[] { before }, new PipelineHopMeta[] { after }, new int[] { pipelineMeta.indexOfPipelineHop( hop ) } );

        if ( pipelineMeta.hasLoop( hop.getToStep() ) ) {
          hasLoop = true;
          setHopEnabled( hop, false );
        }
      }
    }

    if ( enabled && hasLoop ) {
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Title" ),
        BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Message" ), SWT.OK | SWT.ICON_ERROR );
    }

    updateGui();
  }

  public void enableHopsDownstream() {
    enableDisableHopsDownstream( true );
  }

  public void disableHopsDownstream() {
    enableDisableHopsDownstream( false );
  }

  public void enableDisableHopsDownstream( boolean enabled ) {
    if ( currentHop == null ) {
      return;
    }

    PipelineHopMeta before = (PipelineHopMeta) currentHop.clone();
    setHopEnabled( currentHop, enabled );
    PipelineHopMeta after = (PipelineHopMeta) currentHop.clone();
    hopUi.undoDelegate.addUndoChange( pipelineMeta, new PipelineHopMeta[] { before }, new PipelineHopMeta[] { after }, new int[] { pipelineMeta.indexOfPipelineHop( currentHop ) } );

    Set<StepMeta> checkedEntries = enableDisableNextHops( currentHop.getToStep(), enabled, new HashSet<>() );

    if ( checkedEntries.stream().anyMatch( entry -> pipelineMeta.hasLoop( entry ) ) ) {
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Title" ),
        BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopCausesLoop.Message" ), SWT.OK | SWT.ICON_ERROR );
    }

    updateGui();
  }

  private Set<StepMeta> enableDisableNextHops( StepMeta from, boolean enabled, Set<StepMeta> checkedEntries ) {
    checkedEntries.add( from );
    pipelineMeta.getPipelineHops().stream()
      .filter( hop -> from.equals( hop.getFromStep() ) )
      .forEach( hop -> {
        if ( hop.isEnabled() != enabled ) {
          PipelineHopMeta before = (PipelineHopMeta) hop.clone();
          setHopEnabled( hop, enabled );
          PipelineHopMeta after = (PipelineHopMeta) hop.clone();
          hopUi.undoDelegate.addUndoChange( pipelineMeta, new PipelineHopMeta[] { before }, new PipelineHopMeta[] { after }, new int[] { pipelineMeta.indexOfPipelineHop( hop ) } );
        }
        if ( !checkedEntries.contains( hop.getToStep() ) ) {
          enableDisableNextHops( hop.getToStep(), enabled, checkedEntries );
        }
      } );
    return checkedEntries;
  }

  @GuiContextAction(
    id = "pipeline-graph-edit-note",
    parentId = HopGuiPipelineNoteContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Edit the note",
    tooltip = "Edit the note",
    image = "ui/images/Edit.svg"
  )
  public void editNote( HopGuiPipelineNoteContext context ) {
    selectionRegion = null;
    editNote( context.getNotePadMeta() );
  }

  @GuiContextAction(
    id = "pipeline-graph-delete-note",
    parentId = HopGuiPipelineNoteContext.CONTEXT_ID,
    type = GuiActionType.Delete,
    name = "Delete the note",
    tooltip = "Delete the note",
    image = "ui/images/generic-delete.svg"
  )
  public void deleteNote( HopGuiPipelineNoteContext context ) {
    selectionRegion = null;
    int idx = pipelineMeta.indexOfNote( context.getNotePadMeta() );
    if ( idx >= 0 ) {
      pipelineMeta.removeNote( idx );
      hopUi.undoDelegate.addUndoDelete( pipelineMeta, new NotePadMeta[] { context.getNotePadMeta().clone() }, new int[] { idx } );
      updateGui();
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-new-note",
    parentId = HopGuiPipelineContext.CONTEXT_ID,
    type = GuiActionType.Create,
    name = "Create a note",
    tooltip = "Create a new note",
    image = "ui/images/new.svg"
  )
  public void newNote( HopGuiPipelineContext context ) {
    selectionRegion = null;
    String title = BaseMessages.getString( PKG, "PipelineGraph.Dialog.NoteEditor.Title" );
    NotePadDialog dd = new NotePadDialog( pipelineMeta, hopShell(), title );
    NotePadMeta n = dd.open();
    if ( n != null ) {
      NotePadMeta npi =
        new NotePadMeta( n.getNote(), context.getClick().x, context.getClick().y, ConstUI.NOTE_MIN_SIZE, ConstUI.NOTE_MIN_SIZE, n
          .getFontName(), n.getFontSize(), n.isFontBold(), n.isFontItalic(), n.getFontColorRed(), n
          .getFontColorGreen(), n.getFontColorBlue(), n.getBackGroundColorRed(), n.getBackGroundColorGreen(), n
          .getBackGroundColorBlue(), n.getBorderColorRed(), n.getBorderColorGreen(), n.getBorderColorBlue(), n
          .isDrawShadow() );
      pipelineMeta.addNote( npi );
      hopUi.undoDelegate.addUndoNew( pipelineMeta, new NotePadMeta[] { npi }, new int[] { pipelineMeta.indexOfNote( npi ) } );
      updateGui();
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-edit-pipeline",
    parentId = HopGuiPipelineContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Edit pipeline",
    tooltip = "Edit pipeline properties",
    image = "ui/images/TRN.svg"
  )
  public void editPipelineProperties( HopGuiPipelineContext context ) {
    editProperties( pipelineMeta, hopUi, true );
  }

  public void newStep( String description ) {
    StepMeta stepMeta = pipelineStepDelegate.newStep( pipelineMeta, null, description, description, false, true, new Point( currentMouseX, currentMouseY ) );
    PropsUI.setLocation( stepMeta, currentMouseX, currentMouseY );
    updateGui();
  }

  /**
   * This sets the popup-menu on the background of the canvas based on the xy coordinate of the mouse. This method is
   * called after a mouse-click.
   *
   * @param x X-coordinate on screen
   * @param y Y-coordinate on screen
   */
  private synchronized void setMenu( int x, int y ) {
    try {
      currentMouseX = x;
      currentMouseY = y;


    } catch ( Throwable t ) {
      new ErrorDialog( hopShell(), "Error", "Error showing context menu", t );
    }
  }


  private boolean checkNumberOfCopies( PipelineMeta pipelineMeta, StepMeta stepMeta ) {
    boolean enabled = true;
    List<StepMeta> prevSteps = pipelineMeta.findPreviousSteps( stepMeta );
    for ( StepMeta prevStep : prevSteps ) {
      // See what the target steps are.
      // If one of the target steps is our original step, we can't start multiple copies
      //
      String[] targetSteps = prevStep.getStepMetaInterface().getStepIOMeta().getTargetStepnames();
      if ( targetSteps != null ) {
        for ( int t = 0; t < targetSteps.length && enabled; t++ ) {
          if ( !Utils.isEmpty( targetSteps[ t ] ) && targetSteps[ t ].equalsIgnoreCase( stepMeta.getName() ) ) {
            enabled = false;
          }
        }
      }
    }
    return enabled;
  }

  private AreaOwner setToolTip( int x, int y, int screenX, int screenY ) {
    AreaOwner subject = null;

    if ( !hopUi.getProps().showToolTips() ) {
      return subject;
    }

    canvas.setToolTipText( null );

    String newTip = null;
    Image tipImage = null;

    final PipelineHopMeta hi = findHop( x, y );
    // check the area owner list...
    //
    StringBuilder tip = new StringBuilder();
    AreaOwner areaOwner = getVisibleAreaOwner( x, y );
    AreaType areaType = null;
    if ( areaOwner != null && areaOwner.getAreaType() != null ) {
      areaType = areaOwner.getAreaType();
      switch ( areaType ) {
        case STEP_PARTITIONING:
          StepMeta step = (StepMeta) areaOwner.getParent();
          tip.append( "Step partitioning:" ).append( Const.CR ).append( "-----------------------" ).append( Const.CR );
          tip.append( step.getStepPartitioningMeta().toString() ).append( Const.CR );
          if ( step.getTargetStepPartitioningMeta() != null ) {
            tip.append( Const.CR ).append( Const.CR ).append(
              "TARGET: " + step.getTargetStepPartitioningMeta().toString() ).append( Const.CR );
          }
          break;
        case STEP_ERROR_ICON:
          String log = (String) areaOwner.getParent();
          tip.append( log );
          tipImage = GUIResource.getInstance().getImageStepError();
          break;
        case STEP_ERROR_RED_ICON:
          String redLog = (String) areaOwner.getParent();
          tip.append( redLog );
          tipImage = GUIResource.getInstance().getImageRedStepError();
          break;
        case HOP_COPY_ICON:
          step = (StepMeta) areaOwner.getParent();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.Hop.Tooltip.HopTypeCopy", step.getName(), Const.CR ) );
          tipImage = GUIResource.getInstance().getImageCopyHop();
          break;
        case ROW_DISTRIBUTION_ICON:
          step = (StepMeta) areaOwner.getParent();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.Hop.Tooltip.RowDistribution", step.getName(), step
            .getRowDistribution() == null ? "" : step.getRowDistribution().getDescription() ) );
          tip.append( Const.CR );
          tipImage = GUIResource.getInstance().getImageBalance();
          break;
        case HOP_INFO_ICON:
          StepMeta from = (StepMeta) areaOwner.getParent();
          StepMeta to = (StepMeta) areaOwner.getOwner();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.Hop.Tooltip.HopTypeInfo", to.getName(), from.getName(),
            Const.CR ) );
          tipImage = GUIResource.getInstance().getImageInfoHop();
          break;
        case HOP_ERROR_ICON:
          from = (StepMeta) areaOwner.getParent();
          to = (StepMeta) areaOwner.getOwner();
          areaOwner.getOwner();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.Hop.Tooltip.HopTypeError", from.getName(), to.getName(),
            Const.CR ) );
          tipImage = GUIResource.getInstance().getImageErrorHop();
          break;
        case HOP_INFO_STEP_COPIES_ERROR:
          from = (StepMeta) areaOwner.getParent();
          to = (StepMeta) areaOwner.getOwner();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.Hop.Tooltip.InfoStepCopies", from.getName(), to
            .getName(), Const.CR ) );
          tipImage = GUIResource.getInstance().getImageStepError();
          break;
        case STEP_INPUT_HOP_ICON:
          // StepMeta subjectStep = (StepMeta) (areaOwner.getParent());
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepInputConnector.Tooltip" ) );
          tipImage = GUIResource.getInstance().getImageHopInput();
          break;
        case STEP_OUTPUT_HOP_ICON:
          // subjectStep = (StepMeta) (areaOwner.getParent());
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepOutputConnector.Tooltip" ) );
          tipImage = GUIResource.getInstance().getImageHopOutput();
          break;
        case STEP_INFO_HOP_ICON:
          // subjectStep = (StepMeta) (areaOwner.getParent());
          // StreamInterface stream = (StreamInterface) areaOwner.getOwner();
          StepIOMetaInterface ioMeta = (StepIOMetaInterface) areaOwner.getOwner();
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepInfoConnector.Tooltip" ) + Const.CR
            + ioMeta.toString() );
          tipImage = GUIResource.getInstance().getImageHopOutput();
          break;
        case STEP_TARGET_HOP_ICON:
          StreamInterface stream = (StreamInterface) areaOwner.getOwner();
          tip.append( stream.getDescription() );
          tipImage = GUIResource.getInstance().getImageHopOutput();
          break;
        case STEP_ERROR_HOP_ICON:
          StepMeta stepMeta = (StepMeta) areaOwner.getParent();
          if ( stepMeta.supportsErrorHandling() ) {
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepSupportsErrorHandling.Tooltip" ) );
          } else {
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepDoesNotSupportsErrorHandling.Tooltip" ) );
          }
          tipImage = GUIResource.getInstance().getImageHopOutput();
          break;
        case STEP_EDIT_ICON:
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.EditStep.Tooltip" ) );
          tipImage = GUIResource.getInstance().getImageEdit();
          break;
        case STEP_INJECT_ICON:
          Object injection = areaOwner.getOwner();
          if ( injection != null ) {
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepInjectionSupported.Tooltip" ) );
          } else {
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.StepInjectionNotSupported.Tooltip" ) );
          }
          tipImage = GUIResource.getInstance().getImageInject();
          break;
        case STEP_MENU_ICON:
          tip.append( BaseMessages.getString( PKG, "PipelineGraph.ShowMenu.Tooltip" ) );
          tipImage = GUIResource.getInstance().getImageContextMenu();
          break;
        case STEP_ICON:
          StepMeta iconStepMeta = (StepMeta) areaOwner.getOwner();
          if ( iconStepMeta.isDeprecated() ) { // only need tooltip if step is deprecated
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.DeprecatedStep.Tooltip.Title" ) ).append( Const.CR );
            String tipNext = BaseMessages.getString( PKG, "PipelineGraph.DeprecatedStep.Tooltip.Message1",
              iconStepMeta.getName() );
            int length = tipNext.length() + 5;
            for ( int i = 0; i < length; i++ ) {
              tip.append( "-" );
            }
            tip.append( Const.CR ).append( tipNext ).append( Const.CR );
            tip.append( BaseMessages.getString( PKG, "PipelineGraph.DeprecatedStep.Tooltip.Message2" ) );
            if ( !Utils.isEmpty( iconStepMeta.getSuggestion() )
              && !( iconStepMeta.getSuggestion().startsWith( "!" ) && iconStepMeta.getSuggestion().endsWith( "!" ) ) ) {
              tip.append( " " );
              tip.append( BaseMessages.getString( PKG, "PipelineGraph.DeprecatedStep.Tooltip.Message3",
                iconStepMeta.getSuggestion() ) );
            }
            tipImage = GUIResource.getInstance().getImageDeprecated();
            toolTip.setHideDelay( TOOLTIP_HIDE_DELAY_LONG );
          }
          break;
        default:
          break;
      }
    }

    if ( hi != null && tip.length() == 0 ) { // We clicked on a HOP!
      // Set the tooltip for the hop:
      tip.append( Const.CR ).append( BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo" ) ).append(
        newTip = hi.toString() ).append( Const.CR );
    }

    if ( tip.length() == 0 ) {
      newTip = null;
    } else {
      newTip = tip.toString();
    }

    if ( newTip == null ) {
      toolTip.hide();
      if ( hi != null ) { // We clicked on a HOP!

        // Set the tooltip for the hop:
        newTip =
          BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo" )
            + Const.CR
            + BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo.SourceStep" )
            + " "
            + hi.getFromStep().getName()
            + Const.CR
            + BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo.TargetStep" )
            + " "
            + hi.getToStep().getName()
            + Const.CR
            + BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo.Status" )
            + " "
            + ( hi.isEnabled() ? BaseMessages.getString( PKG, "PipelineGraph.Dialog.HopInfo.Enable" ) : BaseMessages
            .getString( PKG, "PipelineGraph.Dialog.HopInfo.Disable" ) );
        toolTip.setText( newTip );
        if ( hi.isEnabled() ) {
          toolTip.setImage( GUIResource.getInstance().getImageHop() );
        } else {
          toolTip.setImage( GUIResource.getInstance().getImageDisabledHop() );
        }
        toolTip.show( new org.eclipse.swt.graphics.Point( screenX, screenY ) );
      } else {
        newTip = null;
      }

    } else if ( !newTip.equalsIgnoreCase( getToolTipText() ) ) {
      Image tooltipImage = null;
      if ( tipImage != null ) {
        tooltipImage = tipImage;
      } else {
        tooltipImage = GUIResource.getInstance().getImageHopUi();
      }
      showTooltip( newTip, tooltipImage, screenX, screenY );
    }

    if ( areaOwner != null && areaOwner.getExtensionAreaType() != null ) {
      try {
        HopGuiPipelinePainterFlyoutTooltipExtension extension =
          new HopGuiPipelinePainterFlyoutTooltipExtension( areaOwner, this, new Point( screenX, screenY ) );

        ExtensionPointHandler.callExtensionPoint(
          LogChannel.GENERAL, HopExtensionPoint.PipelinePainterFlyoutTooltip.id, extension );

      } catch ( Exception e ) {
        LogChannel.GENERAL.logError( "Error calling extension point(s) for the pipeline painter step", e );
      }
    }

    return subject;
  }

  public void showTooltip( String label, Image image, int screenX, int screenY ) {
    toolTip.setImage( image );
    toolTip.setText( label );
    toolTip.hide();
    toolTip.show( new org.eclipse.swt.graphics.Point( screenX, screenY ) );
  }

  public synchronized AreaOwner getVisibleAreaOwner( int x, int y ) {
    for ( int i = areaOwners.size() - 1; i >= 0; i-- ) {
      AreaOwner areaOwner = areaOwners.get( i );
      if ( areaOwner.contains( x, y ) ) {
        return areaOwner;
      }
    }
    return null;
  }

  public void delSelected( StepMeta stepMeta ) {
    List<StepMeta> selection = pipelineMeta.getSelectedSteps();
    if ( stepMeta != null && selection.size() == 0 ) {
      pipelineStepDelegate.delStep( pipelineMeta, stepMeta );
      return;
    }

    if ( currentStep != null && selection.contains( currentStep ) ) {
      currentStep = null;
      for ( StepSelectionListener listener : currentStepListeners ) {
        listener.onUpdateSelection( currentStep );
      }
    }

    pipelineStepDelegate.delSteps( pipelineMeta, selection );
    notePadDelegate.deleteNotes( pipelineMeta, pipelineMeta.getSelectedNotes() );
  }

  public void editDescription( StepMeta stepMeta ) {
    String title = BaseMessages.getString( PKG, "PipelineGraph.Dialog.StepDescription.Title" );
    String message = BaseMessages.getString( PKG, "PipelineGraph.Dialog.StepDescription.Message" );
    EnterTextDialog dd = new EnterTextDialog( hopShell(), title, message, stepMeta.getDescription() );
    String d = dd.open();
    if ( d != null ) {
      stepMeta.setDescription( d );
      stepMeta.setChanged();
      updateGui();
    }
  }

  /**
   * Display the input- or outputfields for a step.
   *
   * @param stepMeta The step (it's metadata) to query
   * @param before   set to true if you want to have the fields going INTO the step, false if you want to see all the
   *                 fields that exit the step.
   */
  private void inputOutputFields( StepMeta stepMeta, boolean before ) {
    redraw();

    SearchFieldsProgressDialog op = new SearchFieldsProgressDialog( pipelineMeta, stepMeta, before );
    boolean alreadyThrownError = false;
    try {
      final ProgressMonitorDialog pmd = new ProgressMonitorDialog( hopShell() );

      // Run something in the background to cancel active database queries, forecably if needed!
      Runnable run = new Runnable() {
        @Override
        public void run() {
          IProgressMonitor monitor = pmd.getProgressMonitor();
          while ( pmd.getShell() == null || ( !pmd.getShell().isDisposed() && !monitor.isCanceled() ) ) {
            try {
              Thread.sleep( 250 );
            } catch ( InterruptedException e ) {
              // Ignore
            }
          }

          if ( monitor.isCanceled() ) { // Disconnect and see what happens!

            try {
              pipelineMeta.cancelQueries();
            } catch ( Exception e ) {
              // Ignore
            }
          }
        }
      };
      // Dump the cancel looker in the background!
      new Thread( run ).start();

      pmd.run( true, true, op );
    } catch ( InvocationTargetException e ) {
      new ErrorDialog( hopShell(), BaseMessages.getString( PKG, "PipelineGraph.Dialog.GettingFields.Title" ), BaseMessages
        .getString( PKG, "PipelineGraph.Dialog.GettingFields.Message" ), e );
      alreadyThrownError = true;
    } catch ( InterruptedException e ) {
      new ErrorDialog( hopShell(), BaseMessages.getString( PKG, "PipelineGraph.Dialog.GettingFields.Title" ), BaseMessages
        .getString( PKG, "PipelineGraph.Dialog.GettingFields.Message" ), e );
      alreadyThrownError = true;
    }

    RowMetaInterface fields = op.getFields();

    if ( fields != null && fields.size() > 0 ) {
      StepFieldsDialog sfd = new StepFieldsDialog( hopShell(), pipelineMeta, SWT.NONE, stepMeta.getName(), fields );
      String sn = (String) sfd.open();
      if ( sn != null ) {
        StepMeta esi = pipelineMeta.findStep( sn );
        if ( esi != null ) {
          editStep( esi );
        }
      }
    } else {
      if ( !alreadyThrownError ) {
        modalMessageDialog( BaseMessages.getString( PKG, "PipelineGraph.Dialog.CouldntFindFields.Title" ),
          BaseMessages.getString( PKG, "PipelineGraph.Dialog.CouldntFindFields.Message" ), SWT.OK | SWT.ICON_INFORMATION );
      }
    }

  }

  public void paintControl( PaintEvent e ) {
    Point area = getArea();
    if ( area.x == 0 || area.y == 0 ) {
      return; // nothing to do!
    }

    Display disp = hopDisplay();

    Image img = getPipelineImage( disp, area.x, area.y, magnification );
    e.gc.drawImage( img, 0, 0 );
    if ( pipelineMeta.nrSteps() == 0 ) {
      e.gc.setForeground( GUIResource.getInstance().getColorCrystalText() );
      e.gc.setFont( GUIResource.getInstance().getFontMedium() );

      Image welcomeImage = GUIResource.getInstance().getImagePipelineCanvas();
      int leftPosition = ( area.x - welcomeImage.getBounds().width ) / 2;
      int topPosition = ( area.y - welcomeImage.getBounds().height ) / 2;
      e.gc.drawImage( welcomeImage, leftPosition, topPosition );
    }
    img.dispose();
  }

  public Image getPipelineImage( Device device, int x, int y, float magnificationFactor ) {

    GCInterface gc = new SWTGC( device, new Point( x, y ), iconsize );

    int gridSize =
      PropsUI.getInstance().isShowCanvasGridEnabled() ? PropsUI.getInstance().getCanvasGridSize() : 1;

    PipelinePainter pipelinePainter = new PipelinePainter( gc, pipelineMeta, new Point( x, y ), new SwtScrollBar( hori ), new SwtScrollBar( vert ),
      candidate, drop_candidate, selectionRegion, areaOwners,
      PropsUI.getInstance().getIconSize(), PropsUI.getInstance().getLineWidth(), gridSize,
      PropsUI.getInstance().getShadowSize(), PropsUI.getInstance()
      .isAntiAliasingEnabled(), PropsUI.getInstance().getNoteFont().getName(), PropsUI.getInstance()
      .getNoteFont().getHeight(), pipeline, PropsUI.getInstance().isIndicateSlowPipelineStepsEnabled(), PropsUI.getInstance().getZoomFactor() );

    // correct the magnifacation with the overall zoom factor
    //
    float correctedMagnification = (float) ( magnificationFactor * PropsUI.getInstance().getZoomFactor() );

    pipelinePainter.setMagnification( correctedMagnification );
    pipelinePainter.setStepLogMap( stepLogMap );
    pipelinePainter.setStartHopStep( startHopStep );
    pipelinePainter.setEndHopLocation( endHopLocation );
    pipelinePainter.setNoInputStep( noInputStep );
    pipelinePainter.setEndHopStep( endHopStep );
    pipelinePainter.setCandidateHopType( candidateHopType );
    pipelinePainter.setStartErrorHopStep( startErrorHopStep );
    pipelinePainter.setShowTargetStreamsStep( showTargetStreamsStep );

    pipelinePainter.buildPipelineImage();

    Image img = (Image) gc.getImage();

    gc.dispose();
    return img;
  }

  @Override
  protected Point getOffset() {
    Point area = getArea();
    Point max = pipelineMeta.getMaximum();
    Point thumb = getThumb( area, max );
    return getOffset( thumb, area );
  }

  private void editStep( StepMeta stepMeta ) {
    pipelineStepDelegate.editStep( pipelineMeta, stepMeta );
  }

  private void editNote( NotePadMeta ni ) {
    NotePadMeta before = (NotePadMeta) ni.clone();

    String title = BaseMessages.getString( PKG, "PipelineGraph.Dialog.EditNote.Title" );
    NotePadDialog dd = new NotePadDialog( pipelineMeta, hopShell(), title, ni );
    NotePadMeta n = dd.open();

    if ( n != null ) {
      ni.setChanged();
      ni.setNote( n.getNote() );
      ni.setFontName( n.getFontName() );
      ni.setFontSize( n.getFontSize() );
      ni.setFontBold( n.isFontBold() );
      ni.setFontItalic( n.isFontItalic() );
      // font color
      ni.setFontColorRed( n.getFontColorRed() );
      ni.setFontColorGreen( n.getFontColorGreen() );
      ni.setFontColorBlue( n.getFontColorBlue() );
      // background color
      ni.setBackGroundColorRed( n.getBackGroundColorRed() );
      ni.setBackGroundColorGreen( n.getBackGroundColorGreen() );
      ni.setBackGroundColorBlue( n.getBackGroundColorBlue() );
      // border color
      ni.setBorderColorRed( n.getBorderColorRed() );
      ni.setBorderColorGreen( n.getBorderColorGreen() );
      ni.setBorderColorBlue( n.getBorderColorBlue() );
      ni.setDrawShadow( n.isDrawShadow() );
      ni.width = ConstUI.NOTE_MIN_SIZE;
      ni.height = ConstUI.NOTE_MIN_SIZE;

      NotePadMeta after = (NotePadMeta) ni.clone();
      hopUi.undoDelegate.addUndoChange( pipelineMeta, new NotePadMeta[] { before }, new NotePadMeta[] { after }, new int[] { pipelineMeta.indexOfNote( ni ) } );
      updateGui();
    }
  }

  private void editHop( PipelineHopMeta pipelineHopMeta ) {
    String name = pipelineHopMeta.toString();
    if ( log.isDebug() ) {
      log.logDebug( BaseMessages.getString( PKG, "PipelineGraph.Logging.EditingHop" ) + name );
    }
    pipelineHopDelegate.editHop( pipelineMeta, pipelineHopMeta );
  }

  private void newHop() {
    List<StepMeta> selection = pipelineMeta.getSelectedSteps();
    if ( selection.size() == 2 ) {
      StepMeta fr = selection.get( 0 );
      StepMeta to = selection.get( 1 );
      pipelineHopDelegate.newHop( pipelineMeta, fr, to );
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-step-10050-create-hop",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Create,
    name = "Create hop",
    tooltip = "Create a new hop between 2 steps",
    image = "ui/images/HOP.svg"
  )
  public void newHopCandidate( HopGuiPipelineStepContext context ) {
    startHopStep = context.getStepMeta();
    endHopStep = null;
    redraw();
  }

  private boolean pointOnLine( int x, int y, int[] line ) {
    int dx, dy;
    int pm = HOP_SEL_MARGIN / 2;
    boolean retval = false;

    for ( dx = -pm; dx <= pm && !retval; dx++ ) {
      for ( dy = -pm; dy <= pm && !retval; dy++ ) {
        retval = pointOnThinLine( x + dx, y + dy, line );
      }
    }

    return retval;
  }

  private boolean pointOnThinLine( int x, int y, int[] line ) {
    int x1 = line[ 0 ];
    int y1 = line[ 1 ];
    int x2 = line[ 2 ];
    int y2 = line[ 3 ];

    // Not in the square formed by these 2 points: ignore!
    // CHECKSTYLE:LineLength:OFF
    if ( !( ( ( x >= x1 && x <= x2 ) || ( x >= x2 && x <= x1 ) ) && ( ( y >= y1 && y <= y2 ) || ( y >= y2
      && y <= y1 ) ) ) ) {
      return false;
    }

    double angle_line = Math.atan2( y2 - y1, x2 - x1 ) + Math.PI;
    double angle_point = Math.atan2( y - y1, x - x1 ) + Math.PI;

    // Same angle, or close enough?
    if ( angle_point >= angle_line - 0.01 && angle_point <= angle_line + 0.01 ) {
      return true;
    }

    return false;
  }

  private SnapAllignDistribute createSnapAllignDistribute() {
    List<StepMeta> selection = pipelineMeta.getSelectedSteps();
    int[] indices = pipelineMeta.getStepIndexes( selection );

    return new SnapAllignDistribute( pipelineMeta, selection, indices, hopUi.undoDelegate, this );
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_SNAP_TO_GRID,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Snap to grid",
    toolTip = "Align the selected steps to the specified grid size",
    image = "ui/images/toolbar/snap-to-grid.svg",
    disabledImage = "ui/images/toolbar/snap-to-grid-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void snapToGrid() {
    snapToGrid( ConstUI.GRID_SIZE );
  }

  private void snapToGrid( int size ) {
    createSnapAllignDistribute().snapToGrid( size );
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_ALIGN_LEFT,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Left-align selected steps",
    toolTip = "Align the steps with the left-most step in your selection",
    image = "ui/images/toolbar/align-left.svg",
    disabledImage = "ui/images/toolbar/align-left-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void alignLeft() {
    createSnapAllignDistribute().allignleft();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_ALIGN_RIGHT,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Right-align selected steps",
    toolTip = "Align the steps with the right-most step in your selection",
    image = "ui/images/toolbar/align-right.svg",
    disabledImage = "ui/images/toolbar/align-right-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void alignRight() {
    createSnapAllignDistribute().allignright();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_ALIGN_TOP,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Top-align selected steps",
    toolTip = "Align the steps with the top-most step in your selection",
    image = "ui/images/toolbar/align-top.svg",
    disabledImage = "ui/images/toolbar/align-top-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void alignTop() {
    createSnapAllignDistribute().alligntop();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_ALIGN_BOTTOM,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Bottom-align selected steps",
    toolTip = "Align the steps with the bottom-most step in your selection",
    image = "ui/images/toolbar/align-bottom.svg",
    disabledImage = "ui/images/toolbar/align-bottom-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void alignBottom() {
    createSnapAllignDistribute().allignbottom();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_DISTRIBUTE_HORIZONTALLY,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Horizontally distribute selected steps",
    toolTip = "Distribute the selected steps evenly between the left-most and right-most step in your selection",
    image = "ui/images/toolbar/distribute-horizontally.svg",
    disabledImage = "ui/images/toolbar/distribute-horizontally-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void distributeHorizontal() {
    createSnapAllignDistribute().distributehorizontal();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_DISTRIBUTE_VERTICALLY,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Vertically distribute selected steps",
    toolTip = "Distribute the selected steps evenly between the top-most and bottom-most step in your selection",
    image = "ui/images/toolbar/distribute-vertically.svg",
    disabledImage = "ui/images/toolbar/distribute-vertically-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void distributeVertical() {
    createSnapAllignDistribute().distributevertical();
  }

  private void detach( StepMeta stepMeta ) {

    for ( int i = pipelineMeta.nrPipelineHops() - 1; i >= 0; i-- ) {
      PipelineHopMeta hop = pipelineMeta.getPipelineHop( i );
      if ( stepMeta.equals( hop.getFromStep() ) || stepMeta.equals( hop.getToStep() ) ) {
        // Step is connected with a hop, remove this hop.
        //
        hopUi.undoDelegate.addUndoNew( pipelineMeta, new PipelineHopMeta[] { hop }, new int[] { i } );
        pipelineMeta.removePipelineHop( i );
      }
    }

    updateGui();
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = TOOLBAR_ITEM_PREVIEW,
    label = "Preview",
    toolTip = "Preview the pipeline",
    image = "ui/images/preview.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @Override
  public void preview() {
    try {
      pipelineRunDelegate.executePipeline( hopUi.getLog(), pipelineMeta, true, false, true, false, true, pipelineRunDelegate.getPipelinePreviewExecutionConfiguration().getLogLevel() );
    } catch ( Exception e ) {
      new ErrorDialog( hopShell(), "Error", "Error previewing pipeline", e );
    }
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = TOOLBAR_ITEM_DEBUG,
    label = "Debug",
    toolTip = "Debug the pipeline",
    image = "ui/images/debug.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @Override
  public void debug() {
    try {
      pipelineRunDelegate.executePipeline( hopUi.getLog(), pipelineMeta, true, false, false, true, true, pipelineRunDelegate.getPipelineDebugExecutionConfiguration().getLogLevel() );
    } catch ( Exception e ) {
      new ErrorDialog( hopShell(), "Error", "Error debugging pipeline", e );
    }
  }

  public void newProps() {
    iconsize = hopUi.getProps().getIconSize();
  }

  public EngineMetaInterface getMeta() {
    return pipelineMeta;
  }

  /**
   * @param pipelineMeta the pipelineMeta to set
   * @return the pipelineMeta / public PipelineMeta getPipelineMeta() { return pipelineMeta; }
   * <p/>
   * /**
   */
  public void setPipelineMeta( PipelineMeta pipelineMeta ) {
    this.pipelineMeta = pipelineMeta;
  }


  @Override public void setFilename( String filename ) {
    pipelineMeta.setFilename( filename );
  }

  @Override public String getFilename() {
    return pipelineMeta.getFilename();
  }

  public boolean canBeClosed() {
    return !pipelineMeta.hasChanged();
  }

  public PipelineMeta getManagedObject() {
    return pipelineMeta;
  }

  public boolean hasContentChanged() {
    return pipelineMeta.hasChanged();
  }

  public List<CheckResultInterface> getRemarks() {
    return remarks;
  }

  public void setRemarks( List<CheckResultInterface> remarks ) {
    this.remarks = remarks;
  }

  public List<DatabaseImpact> getImpact() {
    return impact;
  }

  public void setImpact( List<DatabaseImpact> impact ) {
    this.impact = impact;
  }

  public boolean isImpactFinished() {
    return impactFinished;
  }

  public void setImpactFinished( boolean impactHasRun ) {
    this.impactFinished = impactHasRun;
  }

  /**
   * @return the lastMove
   */
  public Point getLastMove() {
    return lastMove;
  }

  public boolean editProperties( PipelineMeta pipelineMeta, HopGui hopUi, boolean allowDirectoryChange ) {
    return editProperties( pipelineMeta, hopUi, allowDirectoryChange, null );

  }

  public boolean editProperties( PipelineMeta pipelineMeta, HopGui hopUi, boolean allowDirectoryChange,
                                 PipelineDialog.Tabs currentTab ) {
    if ( pipelineMeta == null ) {
      return false;
    }

    PipelineDialog tid = new PipelineDialog( hopUi.getShell(), SWT.NONE, pipelineMeta, currentTab );
    tid.setDirectoryChangeAllowed( allowDirectoryChange );
    PipelineMeta ti = tid.open();

    updateGui();
    return ti != null;
  }

  @Override public boolean hasChanged() {
    return pipelineMeta.hasChanged();
  }

  @Override
  public void save() throws HopException {
    String filename = pipelineMeta.getFilename();
    try {
      if ( StringUtils.isEmpty( filename ) ) {
        throw new HopException( "Please give the pipeline a filename" );
      }
      String xml = pipelineMeta.getXML();
      OutputStream out = HopVFS.getOutputStream( pipelineMeta.getFilename(), false );
      try {
        out.write( XMLHandler.getXMLHeader( Const.XML_ENCODING ).getBytes( Const.XML_ENCODING ) );
        out.write( xml.getBytes( Const.XML_ENCODING ) );
        pipelineMeta.clearChanged();
        redraw();
      } finally {
        out.flush();
        out.close();
      }
    } catch ( Exception e ) {
      throw new HopException( "Error saving pipeline to file '" + filename + "'", e );
    }
  }

  @Override
  public void saveAs( String filename ) throws HopException {
    pipelineMeta.setFilename( filename );
    save();
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = "HopGuiPipelineGraph-ToolBar-10060-Print",
    label = "Print",
    toolTip = "Print this pipeline",
    image = "ui/images/print.svg",
    separator = true,
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @Override
  public void print() {
    PrintSpool ps = new PrintSpool();
    Printer printer = ps.getPrinter( hopShell() );

    // Create an image of the screen
    Point max = pipelineMeta.getMaximum();
    Image img = getPipelineImage( printer, max.x, max.y, 1.0f );
    ps.printImage( hopShell(), img );

    img.dispose();
    ps.dispose();
  }

  public void close() {
    hopUi.menuFileClose();
  }

  @Override public boolean isCloseable() {
    try {
      // Check if the file is saved. If not, ask for it to be saved.
      //
      if ( pipelineMeta.hasChanged() ) {

        MessageBox messageDialog = new MessageBox( hopShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL );
        messageDialog.setText( "Save file?" );
        messageDialog.setMessage( "Do you want to save file '" + buildTabName() + "' before closing?" );
        int answer = messageDialog.open();
        if ( ( answer & SWT.YES ) != 0 ) {
          save();
          return true;
        }
        if ( ( answer & SWT.NO ) != 0 ) {
          // User doesn't want to save but close
          return true;
        }
        return false;
      } else {
        return true;
      }
    } catch ( Exception e ) {
      new ErrorDialog( hopShell(), "Error", "Error preparing file close", e );
    }
    return false;
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = TOOLBAR_ITEM_START,
    label = "Start",
    toolTip = "Start the execution of the pipeline",
    image = "ui/images/toolbar/run.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @Override
  public void start() {
    pipelineMeta.setShowDialog( pipelineMeta.isAlwaysShowRunOptions() );
    Thread thread = new Thread() {
      @Override
      public void run() {
        getDisplay().asyncExec( new Runnable() {
          @Override
          public void run() {
            try {
              pipelineRunDelegate.executePipeline( hopUi.getLog(), pipelineMeta, true, false, false, debug, false, LogLevel.BASIC );
            } catch ( Exception e ) {
              new ErrorDialog( getShell(), "Execute pipeline", "There was an error during pipeline execution", e );
            }
          }
        } );
      }
    };
    thread.start();
  }

  @Override
  @GuiToolbarElement(
    id = TOOLBAR_ITEM_PAUSE,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Pause",
    toolTip = "Pause the execution of the pipeline",
    image = "ui/images/toolbar/pause.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  public void pause() {
    pauseResume();
  }

  /* TODO: re-introduce
  public void checkPipeline() {
    hopUi.checkPipeline();
  }
  */

  /** TODO: re-introduce
   public void analyseImpact() {
   hopUi.analyseImpact();
   }
   */

  /**
   * TODO: re-introduce
   * public void getSQL() {
   * hopUi.getSQL();
   * }
   */

   /* TODO: re-introduce
  public void exploreDatabase() {
    hopUi.exploreDatabase();
  }
   */
  public boolean isExecutionResultsPaneVisible() {
    return extraViewComposite != null && !extraViewComposite.isDisposed();
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_SHOW_EXECUTION_RESULTS,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "HopGui.Menu.ShowExecutionResults",
    toolTip = "HopGui.Tooltip.ShowExecutionResults",
    i18nPackageClass = HopGui.class,
    image = "ui/images/show-results.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID,
    separator = true
  )
  public void showExecutionResults() {
    ToolItem item = toolBarWidgets.findToolItem( TOOLBAR_ITEM_SHOW_EXECUTION_RESULTS );
    if ( isExecutionResultsPaneVisible() ) {
      disposeExtraView();
    } else {
      addAllTabs();
    }
  }

  /**
   * If the extra tab view at the bottom is empty, we close it.
   */
  public void checkEmptyExtraView() {
    if ( extraViewTabFolder.getItemCount() == 0 ) {
      disposeExtraView();
    }
  }

  private void disposeExtraView() {

    extraViewComposite.dispose();
    sashForm.layout();
    sashForm.setWeights( new int[] { 100, } );

    ToolItem item = toolBarWidgets.findToolItem( TOOLBAR_ITEM_SHOW_EXECUTION_RESULTS );
    item.setToolTipText( BaseMessages.getString( PKG, "HopGui.Tooltip.ShowExecutionResults" ) );
    item.setImage( GUIResource.getInstance().getImageShowResults() );
  }

  private void minMaxExtraView() {
    // What is the state?
    //
    boolean maximized = sashForm.getMaximizedControl() != null;
    if ( maximized ) {
      // Minimize
      //
      sashForm.setMaximizedControl( null );
      minMaxButton.setImage( GUIResource.getInstance().getImageMaximizePanel() );
      minMaxButton.setToolTipText( BaseMessages.getString( PKG, "PipelineGraph.ExecutionResultsPanel.MaxButton.Tooltip" ) );
    } else {
      // Maximize
      //
      sashForm.setMaximizedControl( extraViewComposite );
      minMaxButton.setImage( GUIResource.getInstance().getImageMinimizePanel() );
      minMaxButton.setToolTipText( BaseMessages.getString( PKG, "PipelineGraph.ExecutionResultsPanel.MinButton.Tooltip" ) );
    }
  }

  /**
   * @return the toolbar
   */
  public ToolBar getToolBar() {
    return toolBar;
  }

  /**
   * @param toolBar the toolbar to set
   */
  public void setToolBar( ToolBar toolBar ) {
    this.toolBar = toolBar;
  }

  private Label closeButton;

  private Label minMaxButton;

  /**
   * Add an extra view to the main composite SashForm
   */
  public void addExtraView() {
    PropsUI props = PropsUI.getInstance();

    extraViewComposite = new Composite( sashForm, SWT.NONE );
    FormLayout extraCompositeFormLayout = new FormLayout();
    extraCompositeFormLayout.marginWidth = 2;
    extraCompositeFormLayout.marginHeight = 2;
    extraViewComposite.setLayout( extraCompositeFormLayout );

    // Put a close and max button to the upper right corner...
    //
    closeButton = new Label( extraViewComposite, SWT.NONE );
    closeButton.setImage( GUIResource.getInstance().getImageClosePanel() );
    closeButton.setToolTipText( BaseMessages.getString( PKG, "PipelineGraph.ExecutionResultsPanel.CloseButton.Tooltip" ) );
    FormData fdClose = new FormData();
    fdClose.right = new FormAttachment( 100, 0 );
    fdClose.top = new FormAttachment( 0, 0 );
    closeButton.setLayoutData( fdClose );
    closeButton.addMouseListener( new MouseAdapter() {
      @Override
      public void mouseDown( MouseEvent e ) {
        disposeExtraView();
      }
    } );

    minMaxButton = new Label( extraViewComposite, SWT.NONE );
    minMaxButton.setImage( GUIResource.getInstance().getImageMaximizePanel() );
    minMaxButton.setToolTipText( BaseMessages.getString( PKG, "PipelineGraph.ExecutionResultsPanel.MaxButton.Tooltip" ) );
    FormData fdMinMax = new FormData();
    fdMinMax.right = new FormAttachment( closeButton, -props.getMargin() );
    fdMinMax.top = new FormAttachment( 0, 0 );
    minMaxButton.setLayoutData( fdMinMax );
    minMaxButton.addMouseListener( new MouseAdapter() {
      @Override
      public void mouseDown( MouseEvent e ) {
        minMaxExtraView();
      }
    } );

    // Add a label at the top: Results
    //
    Label wResultsLabel = new Label( extraViewComposite, SWT.LEFT );
    wResultsLabel.setFont( GUIResource.getInstance().getFontLarge() );
    wResultsLabel.setBackground( GUIResource.getInstance().getColorWhite() );
    wResultsLabel.setText( BaseMessages.getString( PKG, "PipelineLog.ResultsPanel.NameLabel" ) );
    FormData fdResultsLabel = new FormData();
    fdResultsLabel.left = new FormAttachment( 0, 0 );
    fdResultsLabel.right = new FormAttachment( minMaxButton, -props.getMargin() );
    fdResultsLabel.top = new FormAttachment( 0, 0 );
    wResultsLabel.setLayoutData( fdResultsLabel );

    // Add a tab folder ...
    //
    extraViewTabFolder = new CTabFolder( extraViewComposite, SWT.MULTI );
    hopUi.getProps().setLook( extraViewTabFolder, Props.WIDGET_STYLE_TAB );

    extraViewTabFolder.addMouseListener( new MouseAdapter() {

      @Override
      public void mouseDoubleClick( MouseEvent arg0 ) {
        if ( sashForm.getMaximizedControl() == null ) {
          sashForm.setMaximizedControl( extraViewComposite );
        } else {
          sashForm.setMaximizedControl( null );
        }
      }

    } );

    FormData fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment( 0, 0 );
    fdTabFolder.right = new FormAttachment( 100, 0 );
    fdTabFolder.top = new FormAttachment( wResultsLabel, props.getMargin() );
    fdTabFolder.bottom = new FormAttachment( 100, 0 );
    extraViewTabFolder.setLayoutData( fdTabFolder );

    sashForm.setWeights( new int[] { 60, 40, } );
  }

  public synchronized void start( PipelineExecutionConfiguration executionConfiguration ) throws HopException {
    // Auto save feature...
    handlePipelineMetaChanges( pipelineMeta );

    // filename set & not changed?
    //
    if ( StringUtils.isNotEmpty( pipelineMeta.getFilename() ) && !pipelineMeta.hasChanged() ) {
      if ( pipeline == null || !running ) {
        try {
          // Set the requested logging level..
          //
          DefaultLogLevel.setLogLevel( executionConfiguration.getLogLevel() );

          pipelineMeta.injectVariables( executionConfiguration.getVariables() );

          // Set the named parameters
          Map<String, String> paramMap = executionConfiguration.getParams();
          Set<String> keys = paramMap.keySet();
          for ( String key : keys ) {
            pipelineMeta.setParameterValue( key, Const.NVL( paramMap.get( key ), "" ) );
          }

          pipelineMeta.activateParameters();

          // Do we need to clear the log before running?
          //
          // if ( executionConfiguration.isClearingLog() ) {
          //  pipelineLogDelegate.clearLog();
          // }

          // Also make sure to clear the log entries in the central log store & registry
          //
          if ( pipeline != null ) {
            HopLogStore.discardLines( pipeline.getLogChannelId(), true );
          }

          // Important: even though pipelineMeta is passed to the Pipeline constructor, it is not the same object as is in
          // memory
          // To be able to completely test this, we need to run it as we would normally do in pan
          //
          String pipelineRunConfigurationName = executionConfiguration.getRunConfiguration();
          if ( StringUtils.isEmpty( pipelineRunConfigurationName ) ) {
            throw new HopException( "Please specify a run configuration to use when executing a pipeline" );
          }
          MetaStoreFactory<PipelineRunConfiguration> configFactory = PipelineRunConfiguration.createFactory( hopUi.getMetaStore() );
          PipelineRunConfiguration configuration;
          try {
            configuration = configFactory.loadElement( pipelineRunConfigurationName );
          } catch ( MetaStoreException e ) {
            throw new HopException( "Unable to load pipeline run configuration named '" + pipelineRunConfigurationName + "'", e );
          }

          pipeline = PipelineEngineFactory.createPipelineEngine( configuration, pipelineMeta );
          pipeline.setMetaStore( hopUi.getMetaStore() );

          String spoonLogObjectId = UUID.randomUUID().toString();
          SimpleLoggingObject spoonLoggingObject = new SimpleLoggingObject( "HOPUI", LoggingObjectType.HOPUI, null );
          spoonLoggingObject.setContainerObjectId( spoonLogObjectId );
          spoonLoggingObject.setLogLevel( executionConfiguration.getLogLevel() );
          pipeline.setParent( spoonLoggingObject );

          pipeline.setLogLevel( executionConfiguration.getLogLevel() );
          log.logBasic( BaseMessages.getString( PKG, "PipelineLog.Log.PipelineOpened" ) );
        } catch ( HopException e ) {
          pipeline = null;
          new ErrorDialog( hopShell(), BaseMessages.getString( PKG, "PipelineLog.Dialog.ErrorOpeningPipeline.Title" ),
            BaseMessages.getString( PKG, "PipelineLog.Dialog.ErrorOpeningPipeline.Message" ), e );
        }
        if ( pipeline != null ) {
          log.logMinimal( BaseMessages.getString( PKG, "PipelineLog.Log.LaunchingPipeline" ) + pipeline.getSubject().getName() + "]..." );

          // Launch the step preparation in a different thread.
          // That way HopGui doesn't block anymore and that way we can follow the progress of the initialization
          //
          final Thread parentThread = Thread.currentThread();

          getDisplay().asyncExec( new Runnable() {
            @Override
            public void run() {
              addAllTabs();
              preparePipeline( parentThread );
            }
          } );

          log.logMinimal( BaseMessages.getString( PKG, "PipelineLog.Log.StartedExecutionOfPipeline" ) );

          updateGui();
        }
      } else {
        modalMessageDialog( BaseMessages.getString( PKG, "PipelineLog.Dialog.DoNoStartPipelineTwice.Title" ),
          BaseMessages.getString( PKG, "PipelineLog.Dialog.DoNoStartPipelineTwice.Message" ), SWT.OK | SWT.ICON_WARNING );
      }
    } else {
      if ( pipelineMeta.hasChanged() ) {
        showSaveFileMessage();
      }
    }
  }

  public void showSaveFileMessage() {
    modalMessageDialog( BaseMessages.getString( PKG, "PipelineLog.Dialog.SavePipelineBeforeRunning.Title" ),
      BaseMessages.getString( PKG, "PipelineLog.Dialog.SavePipelineBeforeRunning.Message" ), SWT.OK | SWT.ICON_WARNING );
  }

  public void addAllTabs() {

    CTabItem tabItemSelection = null;
    if ( extraViewTabFolder != null && !extraViewTabFolder.isDisposed() ) {
      tabItemSelection = extraViewTabFolder.getSelection();
    }

    pipelineLogDelegate.addPipelineLog();
    pipelineGridDelegate.addPipelineGrid();
    pipelineMetricsDelegate.addPipelineMetrics();
    pipelinePreviewDelegate.addPipelinePreview();
    pipelinePerfDelegate.addPipelinePerf();

    /*
    List<HopUiExtenderPluginInterface> relevantExtenders = HopUiExtenderPluginType.getInstance().getRelevantExtenders( HopGuiPipelineGraph.class, LOAD_TAB );
    for ( HopUiExtenderPluginInterface relevantExtender : relevantExtenders ) {
      relevantExtender.uiEvent( this, LOAD_TAB );
    }
     */

    if ( tabItemSelection != null ) {
      extraViewTabFolder.setSelection( tabItemSelection );
    } else {
      extraViewTabFolder.setSelection( pipelineGridDelegate.getPipelineGridTab() );
    }

    ToolItem item = toolBarWidgets.findToolItem( TOOLBAR_ITEM_SHOW_EXECUTION_RESULTS );
    item.setImage( GUIResource.getInstance().getImageHideResults() );
    item.setToolTipText( BaseMessages.getString( PKG, "HopGui.Tooltip.HideExecutionResults" ) );
  }

  public synchronized void debug( PipelineExecutionConfiguration executionConfiguration, PipelineDebugMeta pipelineDebugMeta ) {
    if ( !running ) {
      try {
        this.lastPipelineDebugMeta = pipelineDebugMeta;

        log.setLogLevel( executionConfiguration.getLogLevel() );
        if ( log.isDetailed() ) {
          log.logDetailed( BaseMessages.getString( PKG, "PipelineLog.Log.DoPreview" ) );
        }
        pipelineMeta.injectVariables( executionConfiguration.getVariables() );

        // Set the named parameters
        Map<String, String> paramMap = executionConfiguration.getParams();
        Set<String> keys = paramMap.keySet();
        for ( String key : keys ) {
          pipelineMeta.setParameterValue( key, Const.NVL( paramMap.get( key ), "" ) );
        }

        pipelineMeta.activateParameters();

        // Do we need to clear the log before running?
        //
        if ( executionConfiguration.isClearingLog() ) {
          pipelineLogDelegate.clearLog();
        }

        // Do we have a previous execution to clean up in the logging registry?
        //
        if ( pipeline != null ) {
          HopLogStore.discardLines( pipeline.getLogChannelId(), false );
          LoggingRegistry.getInstance().removeIncludingChildren( pipeline.getLogChannelId() );
        }

        // Create a new pipeline to execution
        //
        pipeline = new Pipeline( pipelineMeta );
        pipeline.setPreview( true );
        pipeline.setMetaStore( hopUi.getMetaStore() );
        pipeline.prepareExecution();

        // Add the row listeners to the allocated threads
        //
        pipelineDebugMeta.addRowListenersToPipeline( pipeline );

        // What method should we call back when a break-point is hit?

        pipelineDebugMeta.addBreakPointListers( ( pipelineDebugMeta1, stepDebugMeta, rowBufferMeta, rowBuffer )
          -> showPreview( pipelineDebugMeta1, stepDebugMeta, rowBufferMeta, rowBuffer ) );

        // Capture data?
        //
        pipelinePreviewDelegate.capturePreviewData( pipeline, pipelineMeta.getSteps() );

        // Start the threads for the steps...
        //
        startThreads();

        debug = true;

        // Show the execution results view...
        //
        hopDisplay().asyncExec( new Runnable() {
          @Override
          public void run() {
            addAllTabs();
          }
        } );
      } catch ( Exception e ) {
        new ErrorDialog( hopShell(), BaseMessages.getString( PKG, "PipelineLog.Dialog.UnexpectedErrorDuringPreview.Title" ),
          BaseMessages.getString( PKG, "PipelineLog.Dialog.UnexpectedErrorDuringPreview.Message" ), e );
      }
    } else {
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineLog.Dialog.DoNoPreviewWhileRunning.Title" ),
        BaseMessages.getString( PKG, "PipelineLog.Dialog.DoNoPreviewWhileRunning.Message" ), SWT.OK | SWT.ICON_WARNING );
    }
    checkErrorVisuals();
  }

  public synchronized void showPreview( final PipelineDebugMeta pipelineDebugMeta, final StepDebugMeta stepDebugMeta,
                                        final RowMetaInterface rowBufferMeta, final List<Object[]> rowBuffer ) {
    hopDisplay().asyncExec( new Runnable() {

      @Override
      public void run() {

        if ( isDisposed() ) {
          return;
        }

        // hopUi.enableMenus(); TODO Make this automatic

        // The pipeline is now paused, indicate this in the log dialog...
        //
        pausing = true;

        updateGui();
        checkErrorVisuals();

        PreviewRowsDialog previewRowsDialog =
          new PreviewRowsDialog(
            hopShell(), pipelineMeta, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL | SWT.SHEET,
            stepDebugMeta.getStepMeta().getName(), rowBufferMeta, rowBuffer );
        previewRowsDialog.setProposingToGetMoreRows( true );
        previewRowsDialog.setProposingToStop( true );
        previewRowsDialog.open();

        if ( previewRowsDialog.isAskingForMoreRows() ) {
          // clear the row buffer.
          // That way if you click resume, you get the next N rows for the step :-)
          //
          rowBuffer.clear();

          // Resume running: find more rows...
          //
          pauseResume();
        }

        if ( previewRowsDialog.isAskingToStop() ) {
          // Stop running
          //
          stop();
        }
      }
    } );
  }

  private String[] convertArguments( Map<String, String> arguments ) {
    String[] argumentNames = arguments.keySet().toArray( new String[ arguments.size() ] );
    Arrays.sort( argumentNames );

    String[] args = new String[ argumentNames.length ];
    for ( int i = 0; i < args.length; i++ ) {
      String argumentName = argumentNames[ i ];
      args[ i ] = arguments.get( argumentName );
    }
    return args;
  }

  @GuiToolbarElement(
    id = TOOLBAR_ITEM_STOP,
    type = GuiElementType.TOOLBAR_BUTTON,
    label = "Stop",
    toolTip = "Stop the execution of the pipeline",
    image = "ui/images/toolbar/stop.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @Override
  public void stop() {
    if ( safeStopping ) {
      modalMessageDialog( BaseMessages.getString( PKG, "PipelineLog.Log.SafeStopAlreadyStarted.Title" ),
        BaseMessages.getString( PKG, "PipelineLog.Log.SafeStopAlreadyStarted" ), SWT.ICON_ERROR | SWT.OK );
      return;
    }
    if ( ( running && !halting ) ) {
      halting = true;
      pipeline.stopAll();
      log.logMinimal( BaseMessages.getString( PKG, "PipelineLog.Log.ProcessingOfPipelineStopped" ) );

      running = false;
      initialized = false;
      halted = false;
      halting = false;

      updateGui();

      pipelineMeta.setInternalHopVariables(); // set the original vars back as they may be changed by a mapping
    }
  }

  public synchronized void pauseResume() {
    if ( running ) {
      // Get the pause toolbar item
      //
      if ( !pausing ) {
        pausing = true;
        pipeline.pauseRunning();
        updateGui();
      } else {
        pausing = false;
        pipeline.resumeRunning();
        updateGui();
      }
    }
  }

  private synchronized void preparePipeline( final Thread parentThread ) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          pipeline.prepareExecution();

          // Capture data?
          //
          pipelinePreviewDelegate.capturePreviewData( pipeline, pipelineMeta.getSteps() );

          initialized = true;
        } catch ( HopException e ) {
          log.logError( pipeline.getSubject().getName() + ": preparing pipeline execution failed", e );
          checkErrorVisuals();
        }
        halted = pipeline.hasHaltedComponents();
        if ( pipeline.isReadyToStart() ) {
          checkStartThreads(); // After init, launch the threads.
        } else {
          initialized = false;
          running = false;
          checkErrorVisuals();
        }
      }
    };
    Thread thread = new Thread( runnable );
    thread.start();
  }

  private void checkStartThreads() {
    if ( initialized && !running && pipeline != null ) {
      startThreads();
    }
  }

  private synchronized void startThreads() {
    running = true;
    try {
      // Add a listener to the pipeline.
      // If the pipeline is done, we want to do the end processing, etc.
      //
      pipeline.addFinishedListener( ( pipeline ) -> {
          checkPipelineEnded();
          checkErrorVisuals();
          stopRedrawTimer();

          pipelineMetricsDelegate.resetLastRefreshTime();
          pipelineMetricsDelegate.updateGraph();
        }
      );

      pipeline.startThreads();
      startRedrawTimer();

      updateGui();
    } catch ( HopException e ) {
      log.logError( "Error starting step threads", e );
      checkErrorVisuals();
      stopRedrawTimer();
    }

    // See if we have to fire off the performance graph updater etc.
    //
    getDisplay().asyncExec( new Runnable() {
      @Override
      public void run() {
        if ( pipelinePerfDelegate.getPipelinePerfTab() != null ) {
          // If there is a tab open, try to the correct content on there now
          //
          pipelinePerfDelegate.setupContent();
          pipelinePerfDelegate.layoutPerfComposite();
        }
      }
    } );
  }

  private void startRedrawTimer() {

    redrawTimer = new Timer( "HopGuiPipelineGraph: redraw timer" );
    TimerTask timtask = new TimerTask() {
      @Override
      public void run() {
        if ( !hopDisplay().isDisposed() ) {
          hopDisplay().asyncExec( new Runnable() {
            @Override
            public void run() {
              if ( !HopGuiPipelineGraph.this.canvas.isDisposed() ) {
                HopGuiPipelineGraph.this.canvas.redraw();
              }
            }
          } );
        }
      }
    };

    redrawTimer.schedule( timtask, 0L, ConstUI.INTERVAL_MS_PIPELINE_CANVAS_REFRESH );

  }

  protected void stopRedrawTimer() {
    if ( redrawTimer != null ) {
      redrawTimer.cancel();
      redrawTimer.purge();
      redrawTimer = null;
    }

  }

  private void checkPipelineEnded() {
    if ( pipeline != null ) {
      if ( pipeline.isFinished() && ( running || halted ) ) {
        log.logMinimal( BaseMessages.getString( PKG, "PipelineLog.Log.PipelineHasFinished" ) );

        running = false;
        initialized = false;
        halted = false;
        halting = false;
        safeStopping = false;

        updateGui();

        // OK, also see if we had a debugging session going on.
        // If so and we didn't hit a breakpoint yet, display the show
        // preview dialog...
        //
        if ( debug && lastPipelineDebugMeta != null && lastPipelineDebugMeta.getTotalNumberOfHits() == 0 ) {
          debug = false;
          showLastPreviewResults();
        }
        debug = false;

        checkErrorVisuals();

        hopDisplay().asyncExec( new Runnable() {
          @Override
          public void run() {
            // hopUi.fireMenuControlers();
            updateGui();
          }
        } );
      }
    }
  }

  private void checkErrorVisuals() {
    if ( pipeline.getErrors() > 0 ) {
      // Get the logging text and filter it out. Store it in the stepLogMap...
      //
      stepLogMap = new HashMap<>();
      hopDisplay().syncExec( new Runnable() {

        @Override
        public void run() {
          for ( IEngineComponent component : pipeline.getComponents() ) {
            if ( component.getErrors() > 0 ) {
              String logText = component.getLogText();
              stepLogMap.put( component.getName(), logText );
            }
          }
        }
      } );

    } else {
      stepLogMap = null;
    }
    // Redraw the canvas to show the error icons etc.
    //
    hopDisplay().asyncExec( new Runnable() {
      @Override
      public void run() {
        redraw();
      }
    } );
  }

  public synchronized void showLastPreviewResults() {
    if ( lastPipelineDebugMeta == null || lastPipelineDebugMeta.getStepDebugMetaMap().isEmpty() ) {
      return;
    }

    final List<String> stepnames = new ArrayList<>();
    final List<RowMetaInterface> rowMetas = new ArrayList<>();
    final List<List<Object[]>> rowBuffers = new ArrayList<>();

    // Assemble the buffers etc in the old style...
    //
    for ( StepMeta stepMeta : lastPipelineDebugMeta.getStepDebugMetaMap().keySet() ) {
      StepDebugMeta stepDebugMeta = lastPipelineDebugMeta.getStepDebugMetaMap().get( stepMeta );

      stepnames.add( stepMeta.getName() );
      rowMetas.add( stepDebugMeta.getRowBufferMeta() );
      rowBuffers.add( stepDebugMeta.getRowBuffer() );
    }

    hopDisplay().asyncExec( () -> {
      EnterPreviewRowsDialog dialog = new EnterPreviewRowsDialog( hopShell(), SWT.NONE, stepnames, rowMetas, rowBuffers );
      dialog.open();
    } );
  }

  /**
   * @return the running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * @param running the running to set
   */
  public void setRunning( boolean running ) {
    this.running = running;
  }

  /**
   * @return the lastPipelineDebugMeta
   */
  public PipelineDebugMeta getLastPipelineDebugMeta() {
    return lastPipelineDebugMeta;
  }

  /**
   * @return the halting
   */
  public boolean isHalting() {
    return halting;
  }

  /**
   * @param halting the halting to set
   */
  public void setHalting( boolean halting ) {
    this.halting = halting;
  }

  /**
   * @return the stepLogMap
   */
  public Map<String, String> getStepLogMap() {
    return stepLogMap;
  }

  /**
   * @param stepLogMap the stepLogMap to set
   */
  public void setStepLogMap( Map<String, String> stepLogMap ) {
    this.stepLogMap = stepLogMap;
  }

  @Override
  public HasLogChannelInterface getLogChannelProvider() {
    return new HasLogChannelInterface() {
      @Override
      public LogChannelInterface getLogChannel() {
        return getPipeline() != null ? getPipeline().getLogChannel() : getPipelineMeta().getLogChannel();
      }
    };
  }

  public synchronized void setPipeline( Pipeline pipeline ) {
    this.pipeline = pipeline;
    if ( pipeline != null ) {
      pausing = pipeline.isPaused();
      initialized = pipeline.isInitializing();
      running = pipeline.isRunning();
      halted = pipeline.isStopped();

      if ( running ) {
        pipeline.addPipelineListener( new ExecutionAdapter<PipelineMeta>() {

          @Override
          public void finished( IPipelineEngine<PipelineMeta> pipeline ) {
            checkPipelineEnded();
            checkErrorVisuals();
          }
        } );
      }
    }
  }

  @GuiContextAction(
    id = "pipeline-graph-step-12000-sniff-output",
    parentId = HopGuiPipelineStepContext.CONTEXT_ID,
    type = GuiActionType.Info,
    name = "Sniff output",
    tooltip = "Take a look at 50 rows coming out of the selected step",
    image = "ui/images/preview.svg"
  )
  public void sniff( HopGuiPipelineStepContext context ) {
    StepMeta stepMeta = context.getStepMeta();

    try {
      pipeline.retrieveComponentOutput( stepMeta.getName(), 0, 50, ( ( pipelineEngine, rowBuffer ) -> {
        hopDisplay().asyncExec( () -> {
          PreviewRowsDialog dialog = new PreviewRowsDialog( hopShell(), hopUi.getVariableSpace(), SWT.NONE, stepMeta.getName(), rowBuffer.getRowMeta(), rowBuffer.getBuffer() );
          dialog.open();
        } );
      } ) );
    } catch ( HopException e ) {
      new ErrorDialog( hopShell(), "Error", "Error sniffing rows", e );
    }
  }

  @Override public LogChannelInterface getLogChannel() {
    return log;
  }

  /**
   * Edit the step of the given pipeline
   *
   * @param pipelineMeta
   * @param stepMeta
   */
  public void editStep( PipelineMeta pipelineMeta, StepMeta stepMeta ) {
    // TODO: implement this
  }

  public String buildTabName() throws HopException {
    String tabName = null;
    String realFilename = pipelineMeta.environmentSubstitute( pipelineMeta.getFilename() );
    if ( StringUtils.isEmpty( realFilename ) ) {
      tabName = pipelineMeta.getName();
    } else {
      try {
        FileObject fileObject = HopVFS.getFileObject( pipelineMeta.getFilename() );
        FileName fileName = fileObject.getName();
        tabName = fileName.getBaseName();
      } catch ( Exception e ) {
        throw new HopException( "Unable to get information from file name '" + pipelineMeta.getFilename() + "'", e );
      }
    }
    return tabName;
  }

  public void handlePipelineMetaChanges( PipelineMeta pipelineMeta ) throws HopException {
    if ( pipelineMeta.hasChanged() ) {
      if ( hopUi.getProps().getAutoSave() ) {
        save();
      } else {
        MessageDialogWithToggle md =
          new MessageDialogWithToggle( hopShell(), BaseMessages.getString( PKG, "PipelineLog.Dialog.FileHasChanged.Title" ),
            null, BaseMessages.getString( PKG, "PipelineLog.Dialog.FileHasChanged1.Message" ) + Const.CR
            + BaseMessages.getString( PKG, "PipelineLog.Dialog.FileHasChanged2.Message" ) + Const.CR,
            MessageDialog.QUESTION, new String[] { BaseMessages.getString( PKG, "System.Button.Yes" ),
            BaseMessages.getString( PKG, "System.Button.No" ) }, 0, BaseMessages.getString( PKG,
            "PipelineLog.Dialog.Option.AutoSavePipeline" ), hopUi.getProps().getAutoSave() );
        MessageDialogWithToggle.setDefaultImage( GUIResource.getInstance().getImageHopUi() );
        int answer = md.open();
        if ( ( answer & 0xFF ) == 0 ) {
          save();
        }
        hopUi.getProps().setAutoSave( md.getToggleState() );
      }
    }
  }

  private StepMeta lastChained = null;

  public void addStepToChain( PluginInterface stepPlugin, boolean shift ) {
    // Is the lastChained entry still valid?
    //
    if ( lastChained != null && pipelineMeta.findStep( lastChained.getName() ) == null ) {
      lastChained = null;
    }

    // If there is exactly one selected step, pick that one as last chained.
    //
    List<StepMeta> sel = pipelineMeta.getSelectedSteps();
    if ( sel.size() == 1 ) {
      lastChained = sel.get( 0 );
    }

    // Where do we add this?

    Point p = null;
    if ( lastChained == null ) {
      p = pipelineMeta.getMaximum();
      p.x -= 100;
    } else {
      p = new Point( lastChained.getLocation().x, lastChained.getLocation().y );
    }

    p.x += 200;

    // Which is the new step?

    StepMeta newStep = pipelineStepDelegate.newStep( pipelineMeta, stepPlugin.getIds()[ 0 ], stepPlugin.getName(), stepPlugin.getName(), false, true, p );
    if ( newStep == null ) {
      return;
    }
    newStep.setLocation( p.x, p.y );

    if ( lastChained != null ) {
      PipelineHopMeta hop = new PipelineHopMeta( lastChained, newStep );
      pipelineHopDelegate.newHop( pipelineMeta, hop );
    }

    lastChained = newStep;

    if ( shift ) {
      editStep( newStep );
    }

    pipelineMeta.unselectAll();
    newStep.setSelected( true );

    updateGui();
  }

  public HopGui getHopUi() {
    return hopUi;
  }

  public void setHopUi( HopGui hopUi ) {
    this.hopUi = hopUi;
  }

  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  public IPipelineEngine<PipelineMeta> getPipeline() {
    return pipeline;
  }

  private void setHopEnabled( PipelineHopMeta hop, boolean enabled ) {
    hop.setEnabled( enabled );
    pipelineMeta.clearCaches();
  }

  private void modalMessageDialog( String title, String message, int swtFlags ) {
    MessageBox messageBox = new MessageBox( hopShell(), swtFlags );
    messageBox.setMessage( message );
    messageBox.setText( title );
    messageBox.open();
  }

  /**
   * Gets fileType
   *
   * @return value of fileType
   */
  public HopPipelineFileType getFileType() {
    return fileType;
  }

  /**
   * @param fileType The fileType to set
   */
  public void setFileType( HopPipelineFileType fileType ) {
    this.fileType = fileType;
  }

  /**
   * Gets perspective
   *
   * @return value of perspective
   */
  public HopDataOrchestrationPerspective getPerspective() {
    return perspective;
  }

  @Override public boolean equals( Object o ) {
    if ( this == o ) {
      return true;
    }
    if ( o == null || getClass() != o.getClass() ) {
      return false;
    }
    HopGuiPipelineGraph that = (HopGuiPipelineGraph) o;
    return Objects.equals( pipelineMeta, that.pipelineMeta ) &&
      Objects.equals( id, that.id );
  }

  @Override public int hashCode() {
    return Objects.hash( pipelineMeta, id );
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = TOOLBAR_ITEM_UNDO_ID,
    label = "Undo",
    toolTip = "Undo an operation",
    image = "ui/images/toolbar/Antu_edit-undo.svg",
    disabledImage = "ui/images/toolbar/Antu_edit-undo-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID,
    separator = true
  )
  @GuiKeyboardShortcut( control = true, key = 'z' )
  @Override public void undo() {
    pipelineUndoDelegate.undoPipelineAction( this, pipelineMeta );
    forceFocus();
  }

  @GuiToolbarElement(
    type = GuiElementType.TOOLBAR_BUTTON,
    id = TOOLBAR_ITEM_REDO_ID,
    label = "Redo",
    toolTip = "Redo an operation",
    image = "ui/images/toolbar/Antu_edit-redo.svg",
    disabledImage = "ui/images/toolbar/Antu_edit-redo-disabled.svg",
    parentId = GUI_PLUGIN_TOOLBAR_PARENT_ID
  )
  @GuiKeyboardShortcut( control = true, shift = true, key = 'z' )
  @Override public void redo() {
    pipelineUndoDelegate.redoPipelineAction( this, pipelineMeta );
    forceFocus();
  }

  /**
   * Update the representation, toolbar, menus and so on. This is needed after a file, context or capabilities changes
   */
  @Override public void updateGui() {

    if ( hopUi == null || toolBarWidgets == null || toolBar == null || toolBar.isDisposed() ) {
      return;
    }

    hopDisplay().asyncExec( new Runnable() {
      @Override public void run() {
        setZoomLabel();

        // Enable/disable the undo/redo toolbar buttons...
        //
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_UNDO_ID, pipelineMeta.viewThisUndo() != null );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_REDO_ID, pipelineMeta.viewNextUndo() != null );

        // Enable/disable the align/distribute toolbar buttons
        //
        boolean selectedStep = !pipelineMeta.getSelectedSteps().isEmpty();
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_SNAP_TO_GRID, selectedStep );

        boolean selectedSteps = pipelineMeta.getSelectedSteps().size() > 1;
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_ALIGN_LEFT, selectedSteps );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_ALIGN_RIGHT, selectedSteps );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_ALIGN_TOP, selectedSteps );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_ALIGN_BOTTOM, selectedSteps );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_DISTRIBUTE_HORIZONTALLY, selectedSteps );
        toolBarWidgets.enableToolbarItem( TOOLBAR_ITEM_DISTRIBUTE_VERTICALLY, selectedSteps );

        hopUi.setUndoMenu( pipelineMeta );
        hopUi.handleFileCapabilities( fileType );

        HopGuiPipelineGraph.super.redraw();
      }
    } );

  }

  public boolean forceFocus() {
    return canvas.forceFocus();
  }

  @GuiKeyboardShortcut( control = true, key = 'a' )
  @GuiOSXKeyboardShortcut( command = true, key = 'a' )
  @Override public void selectAll() {
    pipelineMeta.selectAll();
    updateGui();
  }

  @GuiKeyboardShortcut( key = SWT.ESC )
  @Override public void unselectAll() {
    clearSettings();
    updateGui();
  }

  @GuiKeyboardShortcut( control = true, key = 'c' )
  @GuiOSXKeyboardShortcut( command = true, key = 'c' )
  @Override public void copySelectedToClipboard() {
    if ( pipelineLogDelegate.hasSelectedText() ) {
      pipelineLogDelegate.copySelected();
    } else {
      pipelineClipboardDelegate.copySelected( pipelineMeta, pipelineMeta.getSelectedSteps(), pipelineMeta.getSelectedNotes() );
    }
  }

  @GuiKeyboardShortcut( control = true, key = 'x' )
  @GuiOSXKeyboardShortcut( command = true, key = 'x' )
  @Override public void cutSelectedToClipboard() {
    pipelineClipboardDelegate.copySelected( pipelineMeta, pipelineMeta.getSelectedSteps(), pipelineMeta.getSelectedNotes() );
    pipelineStepDelegate.delSteps( pipelineMeta, pipelineMeta.getSelectedSteps() );
    notePadDelegate.deleteNotes( pipelineMeta, pipelineMeta.getSelectedNotes() );
  }

  @GuiKeyboardShortcut( key = SWT.DEL )
  @Override public void deleteSelected() {
    delSelected( null );
    updateGui();
  }

  @GuiKeyboardShortcut( control = true, key = 'v' )
  @GuiOSXKeyboardShortcut( command = true, key = 'v' )
  @Override public void pasteFromClipboard() {
    pasteFromClipboard( new Point( currentMouseX, currentMouseY ) );
  }

  public void pasteFromClipboard( Point location ) {
    final String clipboard = pipelineClipboardDelegate.fromClipboard();
    pipelineClipboardDelegate.pasteXML( pipelineMeta, clipboard, location );
  }

  @GuiContextAction(
    id = "pipeline-graph-pipeline-paste",
    parentId = HopGuiPipelineContext.CONTEXT_ID,
    type = GuiActionType.Modify,
    name = "Paste from the clipboard",
    tooltip = "Paste steps, notes or a whole pipeline from the clipboard",
    image = "ui/images/CPY.svg"
  )
  public void pasteFromClipboard( HopGuiPipelineContext context ) {
    pasteFromClipboard( context.getClick() );
  }

  @Override public List<IGuiContextHandler> getContextHandlers() {
    List<IGuiContextHandler> handlers = new ArrayList<>();
    return handlers;
  }
}
