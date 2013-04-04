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
package com.metsci.glimpse.plot.stacked;

import static com.metsci.glimpse.plot.stacked.StackedPlot2D.Orientation.HORIZONTAL;
import static com.metsci.glimpse.plot.stacked.StackedPlot2D.Orientation.VERTICAL;

import java.util.Comparator;

import com.metsci.glimpse.axis.Axis1D;
import com.metsci.glimpse.context.GlimpseTargetStack;
import com.metsci.glimpse.layout.GlimpseAxisLayout2D;
import com.metsci.glimpse.layout.GlimpseLayout;
import com.metsci.glimpse.plot.stacked.StackedPlot2D.Orientation;
import com.metsci.glimpse.support.settings.LookAndFeel;

public class PlotInfoImpl implements PlotInfo
{
    protected boolean visible;
    protected boolean grow;
    protected Object id;
    protected int order;
    protected int size;
    protected int spacing;
    protected int indentLevel;
    protected String layoutData;
    protected GlimpseAxisLayout2D layout;
    protected StackedPlot2D parent;
    
    public PlotInfoImpl( StackedPlot2D parent, Object id, int order, int size, int spacing, GlimpseAxisLayout2D layout )
    {
        this.parent = parent;
        this.id = id;
        this.order = order;
        this.size = size;
        this.spacing = spacing;
        this.layout = layout;
        this.grow = size < 0;
        this.visible = true;
    }
    
    @Override
    public String getLayoutData( )
    {
        return this.layoutData;
    }
    
    @Override
    public void setLayoutData( String layoutData )
    {
        this.layoutData = layoutData;
    }
    
    @Override
    public void setIndentLevel( int level )
    {
        this.indentLevel = Math.max( 0, level );
    }
    
    @Override
    public int getIndentLevel( )
    {
        return this.indentLevel;
    }

    @Override
    public StackedPlot2D getStackedPlot( )
    {
        return parent;
    }

    @Override
    public Object getId( )
    {
        return id;
    }

    @Override
    public int getOrder( )
    {
        return order;
    }

    @Override
    public int getSize( )
    {
        return size;
    }

    @Override
    public void setGrow( boolean grow )
    {
        this.grow = grow;
    }

    @Override
    public boolean isGrow( )
    {
        return grow && isVisible( );
    }

    @Override
    public void setOrder( int order )
    {
        this.order = order;
        
        if ( this.parent.isAutoValidate( ) ) this.parent.validate( );
    }

    @Override
    public void setSize( int size )
    {
        this.size = size;
        this.grow = size < 0;
        
        if ( this.parent.isAutoValidate( ) )  this.parent.validate( );
    }

    @Override
    public void setPlotSpacing( int spacing )
    {
        this.spacing = spacing;
    }

    @Override
    public int getPlotSpacing( )
    {
        return this.spacing;
    }

    @Override
    public GlimpseAxisLayout2D getLayout( )
    {
        return this.layout;
    }
    
    @Override
    public GlimpseLayout getBaseLayout( )
    {
        return this.layout;
    }

    @Override
    public Axis1D getCommonAxis( GlimpseTargetStack stack )
    {
        return parent.getCommonAxis( layout.getAxis( stack ) );
    }

    @Override
    public Axis1D getOrthogonalAxis( GlimpseTargetStack stack )
    {
        return parent.getOrthogonalAxis( layout.getAxis( stack ) );
    }

    @Override
    public Axis1D getCommonAxis( )
    {
        return parent.getCommonAxis( layout.getAxis( ) );
    }

    @Override
    public Axis1D getOrthogonalAxis( )
    {
        return parent.getOrthogonalAxis( layout.getAxis( ) );
    }

    @Override
    public void addLayout( GlimpseAxisLayout2D childLayout )
    {
        if ( childLayout.getAxis( ) != null )
        {
            Axis1D childCommonAxis = this.parent.getCommonAxis( childLayout.getAxis( ) );
            Axis1D parentCommonAxis = this.parent.getCommonAxis( this.layout.getAxis( ) );
            childCommonAxis.setParent( parentCommonAxis );
        }

        this.layout.addLayout( childLayout );
    }

    @Override
    public void setLookAndFeel( LookAndFeel laf )
    {
        this.layout.setLookAndFeel( laf );
    }
    
    @Override
    public void setVisible( boolean visible )
    {
        this.visible = visible;
        this.layout.setVisible( visible );
    }

    @Override
    public boolean isVisible( )
    {
        return visible;
    }
    
    @Override
    public void deletePlot( )
    {
        if ( this.parent != null )
        {
            StackedPlot2D oldParent = this.parent;
            this.parent = null;
            
            oldParent.removeLayout( layout );
            oldParent.deletePlot( id );
        }
    }
    
    @Override
    public void updateLayout( int index )
    {
        Orientation orient = getStackedPlot( ).getOrientation( );

        int plotCount = getStackedPlot( ).getAllPlots( ).size( );
        
        int plotSpacing = getPlotSpacing( );
        int plotSize = getSize( );
        
        if ( !isVisible( ) )
        {
            plotSpacing = 0;
            plotSize = 0;
        }
        
        // no spacing for the last plot (there's no plot beyond it and spacing between
        // it and the edge of the stacked plot is controlled by setBorderSize(int)
        if ( index == plotCount - 1 && orient == HORIZONTAL ) plotSpacing = 0;
        else if ( index == 0 && orient == VERTICAL ) plotSpacing = 0;

        String layoutData = null;
        if ( orient == VERTICAL )
        {
            if ( isGrow( ) )
            {
                String format = "cell %d %d, spanx %d, growx, pushy, growy, gaptop %d";
                layoutData = String.format( format, indentLevel, index, Integer.MAX_VALUE, plotSpacing );
            }
            else
            {
                String format = "cell %d %d, spanx %d, growx, height %d!, gaptop %d";
                layoutData = String.format( format, indentLevel, index, Integer.MAX_VALUE, plotSize, plotSpacing );
            }
        }
        else if ( orient == HORIZONTAL )
        {
            if ( isGrow( ) )
            {
                String format = "cell %d %d, spany %d, growy, pushx, growx, gapright %d";
                layoutData = String.format( format, index, indentLevel, Integer.MAX_VALUE, plotSpacing );
            }
            else
            {
                String format = "cell %d %d, spany %d, growy, width %d!, gapright %d";
                layoutData = String.format( format, index, indentLevel, Integer.MAX_VALUE, plotSize, plotSpacing );
            }
        }

        getLayout( ).setLayoutData( layoutData );
    }

    @Override
    public int hashCode( )
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( id == null ) ? 0 : id.hashCode( ) );
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass( ) != obj.getClass( ) ) return false;
        PlotInfoImpl other = ( PlotInfoImpl ) obj;
        if ( id == null )
        {
            if ( other.id != null ) return false;
        }
        else if ( !id.equals( other.id ) ) return false;
        return true;
    }

    public static Comparator<PlotInfo> getComparator( )
    {
        return new Comparator<PlotInfo>( )
        {
            @Override
            public int compare( PlotInfo axis0, PlotInfo axis1 )
            {
                if ( axis0.getOrder( ) < axis1.getOrder( ) )
                {
                    return -1;
                }
                else if ( axis0.getOrder( ) > axis1.getOrder( ) )
                {
                    return 1;
                }
                else
                {
                    return 0;
                }
            }
        };
    }
}