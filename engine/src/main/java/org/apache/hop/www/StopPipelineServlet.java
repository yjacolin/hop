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

import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XMLHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.owasp.encoder.Encode;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;


public class StopPipelineServlet extends BaseHttpServlet implements HopServerPluginInterface {
  private static Class<?> PKG = StopPipelineServlet.class; // for i18n purposes, needed by Translator!!

  private static final long serialVersionUID = 3634806745372015720L;
  public static final String CONTEXT_PATH = "/hop/stopPipeline";

  public StopPipelineServlet() {
  }

  public StopPipelineServlet( PipelineMap pipelineMap ) {
    super( pipelineMap );
  }

  /**
   * <div id="mindtouch">
   * <h1>/hop/stopPipeline</h1>
   * <a name="GET"></a>
   * <h2>GET</h2>
   * <p>Stops pipeline execution on HopServer server.</p>
   *
   * <p><b>Example Request:</b><br />
   * <pre function="syntax.xml">
   * GET /hop/stopPipeline/?name=dummy-pipeline&xml=Y
   * </pre>
   *
   * </p>
   * <h3>Parameters</h3>
   * <table class="pentaho-table">
   * <tbody>
   * <tr>
   * <th>name</th>
   * <th>description</th>
   * <th>type</th>
   * </tr>
   * <tr>
   * <td>name</td>
   * <td>Name of the pipeline to be stopped.</td>
   * <td>query</td>
   * </tr>
   * <tr>
   * <td>xml</td>
   * <td>Boolean flag which sets the output format required. Use <code>Y</code> to receive XML response.</td>
   * <td>boolean, optional</td>
   * </tr>
   * <tr>
   * <td>id</td>
   * <td>HopServer pipeline ID of the pipeline to be stopped. This parameter is optional when xml=Y is used.</td>
   * <td>query, optional</td>
   * </tr>
   * <tr>
   * <td>inputOnly</td>
   * <td>Boolean flag indicates which steps to stop.  Use <code>Y</code> to Stop input processing</td>
   * <td>boolean, optional</td>
   * </tr>
   * </tbody>
   * </table>
   *
   * <h3>Response Body</h3>
   *
   * <table class="pentaho-table">
   * <tbody>
   * <tr>
   * <td align="right">text:</td>
   * <td>HTML</td>
   * </tr>
   * <tr>
   * <td align="right">media types:</td>
   * <td>text/xml, text/html</td>
   * </tr>
   * </tbody>
   * </table>
   * <p>Response XML or HTML containing operation result. When using xml=Y <code>result</code> field indicates whether
   * operation was successful (<code>OK</code>) or not (<code>ERROR</code>).</p>
   *
   * <p><b>Example Response:</b></p>
   * <pre function="syntax.xml">
   * <?xml version="1.0" encoding="UTF-8"?>
   * <webresult>
   * <result>OK</result>
   * <message>Pipeline &#x5b;dummy-pipeline&#x5d; stop requested.</message>
   * <id>c56961b2-c848-49b8-abde-76c8015e29b0</id>
   * </webresult>
   * </pre>
   *
   * <h3>Status Codes</h3>
   * <table class="pentaho-table">
   * <tbody>
   * <tr>
   * <th>code</th>
   * <th>description</th>
   * </tr>
   * <tr>
   * <td>200</td>
   * <td>Request was processed.</td>
   * </tr>
   * <tr>
   * <td>500</td>
   * <td>Internal server error occurs during request processing.</td>
   * </tr>
   * </tbody>
   * </table>
   * </div>
   */
  public void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
    IOException {
    if ( isJettyMode() && !request.getContextPath().startsWith( CONTEXT_PATH ) ) {
      return;
    }

    if ( log.isDebug() ) {
      logDebug( BaseMessages.getString( PKG, "StopPipelineServlet.StopOfPipelineRequested" ) );
    }

    String pipelineName = request.getParameter( "name" );
    String id = request.getParameter( "id" );
    boolean inputOnly = "Y".equalsIgnoreCase( request.getParameter( "inputOnly" ) );
    boolean useXML = "Y".equalsIgnoreCase( request.getParameter( "xml" ) );

    PrintWriter out = response.getWriter();
    try {
      if ( useXML ) {
        response.setContentType( "text/xml" );
        response.setCharacterEncoding( Const.XML_ENCODING );
        out.print( XMLHandler.getXMLHeader( Const.XML_ENCODING ) );
      } else {
        response.setContentType( "text/html;charset=UTF-8" );
        out.println( "<HTML>" );
        out.println( "<HEAD>" );
        out.println( "<TITLE>" + BaseMessages.getString( PKG, "StopPipelineServlet.StopPipeline" ) + "</TITLE>" );
        out.println( "<META http-equiv=\"Refresh\" content=\"2;url="
          + convertContextPath( GetPipelineStatusServlet.CONTEXT_PATH ) + "?name="
          + URLEncoder.encode( pipelineName, "UTF-8" ) + "\">" );
        out.println( "<META http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" );
        out.println( "</HEAD>" );
        out.println( "<BODY>" );
      }

      // ID is optional...
      //
      Pipeline pipeline;
      HopServerObjectEntry entry;
      if ( Utils.isEmpty( id ) ) {
        // get the first pipeline that matches...
        //
        entry = getPipelineMap().getFirstCarteObjectEntry( pipelineName );
        if ( entry == null ) {
          pipeline = null;
        } else {
          id = entry.getId();
          pipeline = getPipelineMap().getPipeline( entry );
        }
      } else {
        // Take the ID into account!
        //
        entry = new HopServerObjectEntry( pipelineName, id );
        pipeline = getPipelineMap().getPipeline( entry );
      }

      if ( pipeline != null ) {
        if ( inputOnly ) {
          pipeline.safeStop();
        } else {
          pipeline.stopAll();
        }

        String message = BaseMessages.getString( PKG, "StopPipelineServlet.PipelineStopRequested", pipelineName );

        if ( useXML ) {
          out.println( new WebResult( WebResult.STRING_OK, message, id ).getXML() );
        } else {
          out.println( "<H1>" + Encode.forHtml( message ) + "</H1>" );
          out.println( "<a href=\""
            + convertContextPath( GetPipelineStatusServlet.CONTEXT_PATH ) + "?name="
            + URLEncoder.encode( pipelineName, "UTF-8" ) + "&id=" + URLEncoder.encode( id, "UTF-8" ) + "\">"
            + BaseMessages.getString( PKG, "PipelineStatusServlet.BackToPipelineStatusPage" ) + "</a><p>" );
        }
      } else {
        String message = BaseMessages.getString( PKG, "StopPipelineServlet.CanNotFindPipeline", pipelineName );

        if ( useXML ) {
          out.println( new WebResult( WebResult.STRING_ERROR, message, id ).getXML() );
        } else {
          out.println( "<H1>" + Encode.forHtml( message ) + "</H1>" );
          out.println( "<a href=\""
            + convertContextPath( GetStatusServlet.CONTEXT_PATH ) + "\">"
            + BaseMessages.getString( PKG, "PipelineStatusServlet.BackToStatusPage" ) + "</a><p>" );
          response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
        }
      }
    } catch ( Exception ex ) {
      if ( useXML ) {
        out.println( new WebResult( WebResult.STRING_ERROR, Const.getStackTracker( ex ) ).getXML() );
      } else {
        out.println( "<p>" );
        out.println( "<pre>" );
        out.println( Encode.forHtml( Const.getStackTracker( ex ) ) );
        out.println( "</pre>" );
        response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
      }
    }

    if ( !useXML ) {
      out.println( "<p>" );
      out.println( "</BODY>" );
      out.println( "</HTML>" );
    }
  }

  public String toString() {
    return "Stop pipeline";
  }

  public String getService() {
    return CONTEXT_PATH + " (" + toString() + ")";
  }

  public String getContextPath() {
    return CONTEXT_PATH;
  }
}
