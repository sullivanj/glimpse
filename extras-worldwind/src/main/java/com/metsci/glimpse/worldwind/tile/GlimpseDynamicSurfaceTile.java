/*
 * Copyright (c) 2012, Metron, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Metron, Inc. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL METRON, INC. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.metsci.glimpse.worldwind.tile;

import static com.metsci.glimpse.util.logging.LoggerUtils.logWarning;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.AbstractLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.PreRenderable;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.util.OGLStackHandler;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GLContext;

import com.metsci.glimpse.axis.Axis2D;
import com.metsci.glimpse.canvas.GlimpseCanvas;
import com.metsci.glimpse.context.GlimpseTargetStack;
import com.metsci.glimpse.context.TargetStackUtil;
import com.metsci.glimpse.gl.GLSimpleFrameBufferObject;
import com.metsci.glimpse.layout.GlimpseLayout;
import com.metsci.glimpse.painter.decoration.BackgroundPainter;
import com.metsci.glimpse.util.geo.LatLonGeo;
import com.metsci.glimpse.util.geo.projection.GeoProjection;
import com.metsci.glimpse.util.units.Azimuth;
import com.metsci.glimpse.util.units.Length;
import com.metsci.glimpse.util.vector.Vector2d;
import com.metsci.glimpse.worldwind.canvas.SimpleOffscreenCanvas;

/**
 * Displays the content of a GlimpseLayout onto the surface of the Worldwind globe
 * and dynamically adjusts the surface area of the tile to just fill the screen (and no more)
 * to ensure that the visible areas receive maximum texture resolution.
 * 
 * @author ulman
 */
public class GlimpseDynamicSurfaceTile extends AbstractLayer implements GlimpseSurfaceTile, Renderable, PreRenderable
{
    private static final Logger logger = Logger.getLogger( GlimpseDynamicSurfaceTile.class.getSimpleName( ) );

    protected static final int HEURISTIC_ALTITUDE_CUTOFF = 800;

    protected GlimpseLayout background;
    protected GlimpseLayout mask;
    protected GlimpseLayout layout;
    protected Axis2D axes;
    protected GeoProjection projection;
    protected int width, height;

    protected LatLonBounds maxBounds;
    protected List<LatLon> maxCorners;

    protected LatLonBounds bounds;
    protected List<LatLon> corners;

    protected SimpleOffscreenCanvas offscreenCanvas;
    protected TextureSurfaceTile tile;
    protected GLContext context;

    public GlimpseDynamicSurfaceTile( GlimpseLayout layout, Axis2D axes, GeoProjection projection, int width, int height, double minLat, double maxLat, double minLon, double maxLon )
    {
        this( layout, axes, projection, width, height, getCorners( new LatLonBounds( minLat, maxLat, minLon, maxLon ) ) );
    }

    public GlimpseDynamicSurfaceTile( GlimpseLayout layout, Axis2D axes, GeoProjection projection, int width, int height, List<LatLon> corners )
    {
        this.axes = axes;
        this.projection = projection;
        this.layout = layout;

        this.width = width;
        this.height = height;

        updateMaxCorners( corners );
        
        this.mask = new GlimpseLayout( );
        this.mask.setLayoutData( String.format( "pos 0 0 %d %d", width, height ) );
        this.mask.addLayout( layout );
        
        this.background = new GlimpseLayout( );
        this.background.addPainter( new BackgroundPainter( ).setColor( 1f, 0f, 0f, 1f ) ); //0f, 0f, 0f, 0f ) );
        this.background.addLayout( mask );
        
        this.offscreenCanvas = new SimpleOffscreenCanvas( width, height, false, false, context );
        this.offscreenCanvas.addLayout( this.background );
    }

    public void updateMaxCorners( List<LatLon> corners )
    {
        this.maxBounds = getCorners( corners );
        this.maxCorners = getCorners( this.maxBounds );
    }

    @Override
    public GlimpseLayout getGlimpseLayout( )
    {
        return this.layout;
    }

    @Override
    public GlimpseCanvas getGlimpseCanvas( )
    {
        return this.offscreenCanvas;
    }

    @Override
    public GlimpseTargetStack getTargetStack( )
    {
        return TargetStackUtil.newTargetStack( this.offscreenCanvas, this.layout );
    }

    @Override
    public void preRender( DrawContext dc )
    {
        if ( tile == null )
        {

            if ( context == null )
            {
                GLContext oldcontext = dc.getGLContext( );
                context = dc.getGLDrawable( ).createContext( oldcontext );
            }

            offscreenCanvas.initialize( context );
        }

        updateGeometry( dc );

        drawOffscreen( dc );

        if ( tile == null )
        {
            int textureHandle = getTextureHandle( );
            tile = newTextureSurfaceTile( textureHandle, corners );
        }
    }
    
    protected int getTextureHandle( )
    {
        return offscreenCanvas.getFrameBuffer( ).getTextureId( );
    }

    protected TextureSurfaceTile newTextureSurfaceTile( int textureHandle, Iterable<? extends LatLon> corners )
    {
        return new TextureSurfaceTile( textureHandle, corners );
    }

    protected void updateGeometry( DrawContext dc )
    {
        List<LatLon> screenCorners = getCorners( dc );

        if ( !isValid( screenCorners ) )
        {
            corners = maxCorners;
            bounds = maxBounds;
        }
        else
        {
            // two heuristic methods of calculating the screen corners
            LatLonBounds screenBounds = bufferCorners( getCorners( getCorners0( dc ) ), 0.5 );
            bounds = getIntersectedCorners( maxBounds, screenBounds );
            corners = getCorners( bounds );
        }

        if ( tile != null )
        {
            setAxes( axes, bounds, projection );
            tile.setCorners( corners );
        }
    }

    protected void setAxes( Axis2D axes, LatLonBounds bounds, GeoProjection projection )
    {
        Vector2d c1 = projection.project( LatLonGeo.fromDeg( bounds.minLat, bounds.minLon ) );
        Vector2d c2 = projection.project( LatLonGeo.fromDeg( bounds.maxLat, bounds.minLon ) );
        Vector2d c3 = projection.project( LatLonGeo.fromDeg( bounds.maxLat, bounds.maxLon ) );
        Vector2d c4 = projection.project( LatLonGeo.fromDeg( bounds.minLat, bounds.maxLon ) );

        double minX = minX( c1, c2, c3, c4 );
        double maxX = maxX( c1, c2, c3, c4 );
        double minY = minY( c1, c2, c3, c4 );
        double maxY = maxY( c1, c2, c3, c4 );

        axes.set( minX, maxX, minY, maxY );
        axes.getAxisX( ).validate( );
        axes.getAxisY( ).validate( );
    }

    public static double minX( Vector2d... corners )
    {
        double min = Double.POSITIVE_INFINITY;
        for ( Vector2d corner : corners )
        {
            if ( corner.getX( ) < min ) min = corner.getX( );
        }
        return min;
    }

    public static double minY( Vector2d... corners )
    {
        double min = Double.POSITIVE_INFINITY;
        for ( Vector2d corner : corners )
        {
            if ( corner.getY( ) < min ) min = corner.getY( );
        }
        return min;
    }

    public static double maxX( Vector2d... corners )
    {
        double max = Double.NEGATIVE_INFINITY;
        for ( Vector2d corner : corners )
        {
            if ( corner.getX( ) > max ) max = corner.getX( );
        }
        return max;
    }

    public static double maxY( Vector2d... corners )
    {
        double max = Double.NEGATIVE_INFINITY;
        for ( Vector2d corner : corners )
        {
            if ( corner.getY( ) > max ) max = corner.getY( );
        }
        return max;
    }

    public static boolean isValid( List<LatLon> screenCorners )
    {
        if ( screenCorners == null ) return false;

        for ( LatLon latlon : screenCorners )
        {
            if ( latlon == null ) return false;
        }

        return true;
    }

    public static LatLonBounds bufferCorners( LatLonBounds corners, double bufferFraction )
    {
        double diffLat = corners.maxLat - corners.minLat;
        double diffLon = corners.maxLon - corners.minLon;

        double buffMinLat = corners.minLat - diffLat * bufferFraction;
        double buffMaxLat = corners.maxLat + diffLat * bufferFraction;
        double buffMinLon = corners.minLon - diffLon * bufferFraction;
        double buffMaxLon = corners.maxLon + diffLon * bufferFraction;

        return new LatLonBounds( buffMinLat, buffMaxLat, buffMinLon, buffMaxLon );
    }

    public static LatLonBounds getCorners( List<LatLon> screenCorners )
    {
        double minLat = Double.POSITIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for ( LatLon latlon : screenCorners )
        {
            double lat = latlon.getLatitude( ).getDegrees( );
            double lon = latlon.getLongitude( ).getDegrees( );

            if ( lat < minLat ) minLat = lat;
            if ( lat > maxLat ) maxLat = lat;
            if ( lon < minLon ) minLon = lon;
            if ( lon > maxLon ) maxLon = lon;
        }

        return new LatLonBounds( minLat, maxLat, minLon, maxLon );
    }

    public static LatLonBounds getUnionedCorners( LatLonBounds corners1, LatLonBounds corners2 )
    {
        double minLat = Math.min( corners1.minLat, corners2.minLat );
        double minLon = Math.min( corners1.minLon, corners2.minLon );
        double maxLat = Math.max( corners1.maxLat, corners2.maxLat );
        double maxLon = Math.max( corners1.maxLon, corners2.maxLon );

        return new LatLonBounds( minLat, maxLat, minLon, maxLon );
    }

    public static LatLonBounds getIntersectedCorners( LatLonBounds corners1, LatLonBounds corners2 )
    {
        double minLat = Math.max( corners1.minLat, corners2.minLat );
        double minLon = Math.max( corners1.minLon, corners2.minLon );
        double maxLat = Math.min( corners1.maxLat, corners2.maxLat );
        double maxLon = Math.min( corners1.maxLon, corners2.maxLon );

        return new LatLonBounds( minLat, maxLat, minLon, maxLon );
    }

    public static List<LatLon> getCorners( LatLonBounds bounds )
    {
        List<LatLon> corners = new ArrayList<LatLon>( );

        corners.add( LatLon.fromDegrees( bounds.minLat, bounds.minLon ) );
        corners.add( LatLon.fromDegrees( bounds.minLat, bounds.maxLon ) );
        corners.add( LatLon.fromDegrees( bounds.maxLat, bounds.maxLon ) );
        corners.add( LatLon.fromDegrees( bounds.maxLat, bounds.minLon ) );

        return corners;
    }

    public static List<LatLon> getCorners( DrawContext dc )
    {
        View view = dc.getView( );
        Rectangle viewport = view.getViewport( );

        List<LatLon> corners = new ArrayList<LatLon>( 4 );
        corners.add( view.computePositionFromScreenPoint( viewport.getMinX( ), viewport.getMinY( ) ) );
        corners.add( view.computePositionFromScreenPoint( viewport.getMinX( ), viewport.getMaxY( ) ) );
        corners.add( view.computePositionFromScreenPoint( viewport.getMaxX( ), viewport.getMaxY( ) ) );
        corners.add( view.computePositionFromScreenPoint( viewport.getMaxX( ), viewport.getMinY( ) ) );

        return corners;
    }

    // another possible heuristic for calculating the corners of the visible region
    // inspired by: gov.nasa.worldwind.layers.ScalebarLayer
    public static List<LatLon> getCorners0( DrawContext dc )
    {
        // Compute scale size in real world
        Position referencePosition = dc.getViewportCenterPosition( );
        if ( referencePosition == null ) return null;

        Vec4 groundTarget = dc.getGlobe( ).computePointFromPosition( referencePosition );
        Double distance = dc.getView( ).getEyePoint( ).distanceTo3( groundTarget );
        double metersPerPixel = dc.getView( ).computePixelSizeAtDistance( distance );

        // now assume this size roughly holds across the whole screen
        // (which is an ok assumption when we're zoomed in)
        View view = dc.getView( );
        Rectangle viewport = view.getViewport( );
        double viewportHeightMeters = viewport.getHeight( ) * metersPerPixel;
        double viewportWidthMeters = viewport.getWidth( ) * metersPerPixel;

        // in order to not worry about how the viewport is rotated
        // (which direction is north) just take the largest dimension
        double viewportSizeMeters = Math.max( viewportHeightMeters, viewportWidthMeters );

        LatLonGeo centerLatLon = LatLonGeo.fromDeg( referencePosition.latitude.getDegrees( ), referencePosition.longitude.getDegrees( ) );
        LatLonGeo swLatLon = centerLatLon.displacedBy( Length.fromMeters( viewportSizeMeters ), Azimuth.southwest );
        LatLonGeo seLatLon = centerLatLon.displacedBy( Length.fromMeters( viewportSizeMeters ), Azimuth.southeast );
        LatLonGeo nwLatLon = centerLatLon.displacedBy( Length.fromMeters( viewportSizeMeters ), Azimuth.northwest );
        LatLonGeo neLatLon = centerLatLon.displacedBy( Length.fromMeters( viewportSizeMeters ), Azimuth.northeast );

        Position swPos = Position.fromDegrees( swLatLon.getLatDeg( ), swLatLon.getLonDeg( ) );
        Position sePos = Position.fromDegrees( seLatLon.getLatDeg( ), seLatLon.getLonDeg( ) );
        Position nwPos = Position.fromDegrees( nwLatLon.getLatDeg( ), nwLatLon.getLonDeg( ) );
        Position nePos = Position.fromDegrees( neLatLon.getLatDeg( ), neLatLon.getLonDeg( ) );

        List<LatLon> corners = new ArrayList<LatLon>( 4 );
        corners.add( swPos );
        corners.add( sePos );
        corners.add( nwPos );
        corners.add( nePos );

        return corners;
    }

    @Override
    protected void doRender( DrawContext dc )
    {
        tile.render( dc );

    }

    protected void drawOffscreen( DrawContext dc )
    {
        context.makeCurrent( );
        try
        {
            drawOffscreen( dc.getGLContext( ) );
        }
        finally
        {
            dc.getGLContext( ).makeCurrent( );
        }
    }

    protected void drawOffscreen( GLContext glContext )
    {
        GLSimpleFrameBufferObject fbo = offscreenCanvas.getFrameBuffer( );
        OGLStackHandler stack = new OGLStackHandler( );
        GL gl = glContext.getGL( );

        stack.pushAttrib( gl, GL.GL_ALL_ATTRIB_BITS );
        stack.pushClientAttrib( gl, ( int ) GL.GL_ALL_CLIENT_ATTRIB_BITS );
        stack.pushTexture( gl );
        stack.pushModelview( gl );
        stack.pushProjection( gl );

        fbo.bind( glContext );
        try
        {
            background.paintTo( offscreenCanvas.getGlimpseContext( ) );
        }
        catch ( Exception e )
        {
            logWarning( logger, "Trouble drawing to offscreen buffer", e );
        }
        finally
        {
            fbo.unbind( glContext );
            stack.pop( gl );
        }
    }

    public static class LatLonBounds
    {
        public double minLat, maxLat, minLon, maxLon;

        public LatLonBounds( double minLat, double maxLat, double minLon, double maxLon )
        {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        @Override
        public String toString( )
        {
            return String.format( "%f %f %f %f", minLat, maxLat, minLon, maxLon );
        }
    }
}
