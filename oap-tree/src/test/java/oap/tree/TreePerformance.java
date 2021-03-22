/*
 * The MIT License (MIT)
 *
 * Copyright (c) Open Application Platform Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package oap.tree;

import org.apache.commons.lang3.RandomUtils;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import static oap.benchmark.Benchmark.benchmark;
import static oap.tree.Dimension.LONG;
import static oap.tree.Dimension.OperationType.CONTAINS;
import static oap.tree.Dimension.STRING;

public class TreePerformance {
    @Test
    public void tree() {
//        benchmarkTree( 0.25, 100, 5, new int[] { 10000, 100000, 1000000 } );
//        benchmarkTree( 0.25, 100, 10, new int[] { 10000, 100000, 1000000 } );
//        benchmarkTree( 0.25, 100, 20, new int[] { 10000, 100000, 1000000 } );
        benchmarkTree( 0.25, 2000, 50, 1000000, 1.0, true );
        benchmarkTree( 0.25, 2000, 50, 1000000, 1.0, false );
        benchmarkTree( 0.25, 2000, 50, 1000000, 0.3, true );
        benchmarkTree( 0.25, 2000, 50, 1000000, 0.3, false );
//        benchmarkTree( 0.25, 100, 100, new int[] { /*1000, 10000, */100000 } );
    }

    public void benchmarkTree( double fillFactor, int selections, int dimensions, int requests, double rate, boolean preFilter ) {
        var id = new ArrayList<Dimension>();

        for( int i = 0; i < dimensions; i++ ) {
            if( i % 2 == 0 ) {
                id.add( LONG( "s" + i, CONTAINS, null ) );
            } else {
                id.add( STRING( "s" + i, CONTAINS, preFilter ) );
            }
        }


        var qData = new ArrayList<ArrayList<Object>>();

        var gen = new HashMap<String, ArrayList<Object>>();

        var random = new Random();

        for( int i = 0; i < selections; i++ ) {
            var data = new ArrayList<>();

            for( int x = 0; x < dimensions; x++ ) {
                var v = 1L + random.nextInt( selections / 2 - 1 );
                if( x % 2 == 0 ) {
                    data.add( v );
                } else {
                    data.add( String.valueOf( v ) );
                }
            }

            gen.put( "selection" + i, data );
            qData.add( data );
        }

        var sData = new ArrayList<>( qData );

        var bids = ( long ) ( ( requests - selections ) * rate );
        var nobids = requests - selections - bids;

        for( var i = 0; i < bids; i++ ) {
            qData.add( sData.get( RandomUtils.nextInt( 0, selections ) ) );
        }

        for( var i = 0; i < nobids; i++ ) {
            var r = new ArrayList<>( sData.get( RandomUtils.nextInt( 0, selections ) ) );
            for( var x = 0; x < r.size(); x++ ) {
                var item = r.get( x );
                r.set( x, item instanceof Long ? ( Long ) item + 1 : item + "1" );
            }
            qData.add( r );
        }

        var data = new ArrayList<Tree.ValueData<Object>>();

        gen.forEach( ( selection, v ) -> {
            data.add( new Tree.ValueData<>( v, selection ) );
        } );

        var tree = Tree
            .tree( id )
            .withHashFillFactor( fillFactor )
            .load( data );

        benchmark( "dims = " + dimensions + ", selections = " + selections
            + ", queries = " + requests + ", rate = " + rate + ", preFilter = " + preFilter, requests, i -> {
            tree.find( qData.get( RandomUtils.nextInt( 0, qData.size() ) ) );
        } ).experiments( 5 ).run();

        System.out.println( tree.getMaxDepth() );
    }
}
