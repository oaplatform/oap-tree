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

import com.google.common.base.Joiner;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import oap.tree.Dimension.OperationType;
import oap.util.Lists;
import oap.util.Pair;
import oap.util.Stream;
import oap.util.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static oap.tree.Consts.ANY_AS_ARRAY;
import static oap.tree.Dimension.Direction.EQUAL;
import static oap.tree.Dimension.Direction.LEFT;
import static oap.tree.Dimension.Direction.RIGHT;
import static oap.tree.Dimension.OperationType.CONTAINS;
import static oap.tree.Dimension.OperationType.CONTAINS_ALL;
import static oap.tree.Dimension.OperationType.NOT_CONTAINS;
import static oap.util.Pair.__;

public class Tree<T> {
    private final int maxTraceListCount;
    private final ArrayList<PreFilter> preFilters = new ArrayList<>();
    private final List<? extends Dimension<?>> dimensions;
    private final double hashFillFactor;
    TreeNode<T> root = new Leaf<>( emptyList() );
    private boolean preFilter;
    private long nodeCount = 0;
    private long leafCount = 0;
    public boolean fullDebug;

    Tree( List<? extends Dimension<?>> dimensions, boolean preFilter ) {
        this( dimensions, 0.25, 10, preFilter );
    }

    Tree( List<? extends Dimension<?>> dimensions, double hashFillFactor, int maxTraceListCount, boolean preFilter ) {
        this.dimensions = dimensions;
        this.hashFillFactor = hashFillFactor;
        this.maxTraceListCount = maxTraceListCount;
        this.preFilter = preFilter;
    }

    public static <T> ValueData<T> v( T selection, Object... data ) {
        return v( selection, asList( data ) );
    }

    public static <T> ValueData<T> v( T selection, List<?> data ) {
        return new ValueData<>( data, selection );
    }

    @SafeVarargs
    public static <T> List<T> l( T... data ) {
        return Lists.of( data );
    }

    public static <T> TreeBuilder<T> build( List<Dimension<?>> dimensions ) {
        return new TreeBuilder<T>( dimensions );
    }

    public static <T> TreeBuilder<T> build( Dimension<?>... dimensions ) {
        return new TreeBuilder<>( List.of( dimensions ) );
    }

    @SafeVarargs
    public static <T> Array a( ArrayOperation operationType, T... values ) {
        return new Array( l( values ), operationType );
    }

    public boolean isPreFilter() {
        return preFilter;
    }

    public void setPreFilter( boolean preFilter ) {
        this.preFilter = preFilter;
    }

    public List<PreFilter> getPreFilters() {
        return Collections.unmodifiableList( preFilters );
    }

    public long getNodeCount() {
        return nodeCount;
    }

    public long getLeafCount() {
        return leafCount;
    }

    public TreeArrayStatistic getArrayStatistics() {
        var tas = new TreeArrayStatistic();

        arrayStatistics( root, tas );

        return tas;
    }

    private void arrayStatistics( TreeNode<T> root, TreeArrayStatistic tas ) {
        if( root == null ) return;

        if( root instanceof Tree.Node ) {
            var sets = ( ( Node ) root ).sets;

            tas.update( ArrayOperation.OR, Lists.count( sets, s -> s.operation == ArrayOperation.OR ) );
            tas.update( ArrayOperation.AND, Lists.count( sets, s -> s.operation == ArrayOperation.AND ) );
            tas.update( ArrayOperation.NOT, Lists.count( sets, s -> s.operation == ArrayOperation.NOT ) );

            sets.forEach( s -> tas.updateSize( s.operation, s.bitSet.stream().count() ) );

            arrayStatistics( ( ( Node ) root ).any, tas );
            arrayStatistics( ( ( Node ) root ).left, tas );
            arrayStatistics( ( ( Node ) root ).right, tas );
            arrayStatistics( ( ( Node ) root ).equal, tas );

            sets.forEach( s -> arrayStatistics( s.equal, tas ) );
        }
    }

    @SuppressWarnings( "checkstyle:UnnecessaryParentheses" )
    public void load( List<ValueData<T>> data ) {
        var newData = fixEmptyAsFailed( data );
        init( newData );
        var uniqueCount = getUniqueCount( newData );
        root = toNode( newData, uniqueCount, new BitSet( dimensions.size() ) );

        updateCount( root );

        if( preFilter ) {
            this.preFilters.clear();
            for( var i = 0; i < dimensions.size(); i++ ) {
                var dimension = dimensions.get( i );
                if( !dimension.preFilter ) continue;


                var res = new ArrayList<>();
                var notRes = new ArrayList<>();

                var ok = true;
                for( var v : data ) {
                    var dv = v.data.get( i );
                    if( dv == null
                        || ( dv instanceof Optional<?> odv && odv.isEmpty() )
                        || ( dv instanceof Collection<?> cdv && cdv.isEmpty() )
                    ) {
                        ok = false;
                        break;
                    }
                    if( dv instanceof Array ) {
                        if( ( ( Array ) dv ).operation == ArrayOperation.NOT ) {
                            notRes.addAll( ( Collection<?> ) dv );
                        } else {
                            res.addAll( ( Collection<?> ) dv );
                        }
                    } else {
                        res.add( dv );
                    }
                }

                if( ok ) {
                    var bs = dimension.toBitSet( res );
                    var notBs = dimension.toBitSet( notRes );

                    this.preFilters.add( new PreFilter( dimension, i, bs, notBs ) );
                }
            }
        }
    }

    private List<ValueData<T>> fixEmptyAsFailed( List<ValueData<T>> data ) {
        if( Lists.find2( dimensions, d -> d.emptyAsFailed ) == null ) return data;

        var res = new ArrayList<ValueData<T>>( data.size() );

        next:
        for( var vd : data ) {
            for( var i = 0; i < dimensions.size(); i++ ) {
                if( !dimensions.get( i ).emptyAsFailed ) continue;

                var v = vd.data.get( i );
                if( v == null ) break next;
                if( v instanceof Optional ov && ov.isEmpty() ) break next;
                if( v instanceof Array av && av.isEmpty() ) break next;
            }
            res.add( vd );
        }

        return res;
    }

    private long[] getUniqueCount( List<ValueData<T>> data ) {
        final long[] longs = new long[dimensions.size()];


        for( int i = 0; i < longs.length; i++ ) {
            int finalI = i;
            longs[i] = data.stream().map( d -> d.data.get( finalI ) ).distinct().count();
        }
        return longs;
    }

    private void updateCount( TreeNode<T> node ) {
        if( node == null ) return;

        if( node instanceof Tree.Node ) {
            nodeCount++;
            final Node n = ( Node ) node;
            updateCount( n.any );
            updateCount( n.left );
            updateCount( n.right );
            updateCount( n.equal );
            n.sets.forEach( s -> updateCount( s.equal ) );
        } else {
            leafCount++;
        }
    }

    private void init( List<ValueData<T>> data ) {
        for( int i = 0; i < dimensions.size(); i++ ) {
            var p = dimensions.get( i );

            for( var dv : data ) {
                var v = dv.data.get( i );
                try {
                    if( v instanceof Array av ) {
                        for( var item : av ) {
                            p.init( item );
                        }
                    } else {
                        p.init( v );
                    }
                } catch( ClassCastException cce ) {
                    throw new RuntimeException( "Cannot process dimension field " + v, cce );
                }
            }
        }
    }

    @SuppressWarnings( "unchecked" )
    private TreeNode<T> toNode( List<ValueData<T>> data, long[] uniqueCount, BitSet eq ) {
        if( data.isEmpty() ) return null;

        final SplitDimension splitDimension = findSplitDimension( data, uniqueCount, eq );

        if( splitDimension == null ) return new Leaf<>( Lists.map( data, sd -> sd.value ) );

        var bitSetWithDimension = withSet( eq, splitDimension.dimension );

        var dimension = dimensions.get( splitDimension.dimension );


        if( splitDimension.hash.isEmpty() ) {
            var sets = Lists.map( Lists.groupBy(
                splitDimension.sets,
                s -> s.data.get( splitDimension.dimension )
            ).entrySet(), es -> {
                var key = ( Array ) es.getKey();
                return new ArrayBitSet( dimension.toBitSet( key ), key.operation, toNode( es.getValue(), uniqueCount, bitSetWithDimension ) );
            } );

            return new Node(
                splitDimension.dimension,
                splitDimension.value,
                toNode( splitDimension.left, uniqueCount, eq ),
                toNode( splitDimension.right, uniqueCount, eq ),
                toNode( splitDimension.equal, uniqueCount, bitSetWithDimension ),
                toNode( splitDimension.any, uniqueCount, bitSetWithDimension ),
                sets
            );
        } else {

            var map = Lists.groupBy( splitDimension.hash,
                d -> ( int ) dimension.getOrDefault( d.data.get( splitDimension.dimension ), ANY_AS_ARRAY )[0] );

            var max = Collections.max( map.keySet() );

            var array = new TreeNode[max + 1];
            Arrays.fill( array, null );

            map.forEach( ( p, l ) -> array[p] = toNode( l, uniqueCount, bitSetWithDimension ) );

            return new HashNode(
                splitDimension.dimension,
                array,
                toNode( splitDimension.any, uniqueCount, bitSetWithDimension )
            );
        }
    }

    private BitSet withSet( BitSet eq, int dimension ) {
        var bitSet = BitSet.valueOf( eq.toLongArray() );
        bitSet.set( dimension );
        return bitSet;
    }

    private SplitDimension findSplitDimension( List<ValueData<T>> data, long[] uniqueCount, BitSet eqBitSet ) {
        int priority = Dimension.PRIORITY_LOW;
        int priorityArray = Dimension.PRIORITY_LOW;
        long uniqueSize = -1;
        long uniqueArraySize = Long.MAX_VALUE;
        int splitDimension = -1;
        int splitArrayDimension = -1;

        for( int i = 0; i < dimensions.size(); i++ ) {
            if( eqBitSet.get( i ) ) continue;

            var dimension = dimensions.get( i );

            var isArray = dimension.operationType == null;

            if( isArray && splitDimension >= 0 ) continue;

            var unique = new HashSet<>();
            var uniqueArray = new HashSet<>();

            for( var vd : data ) {
                var value = vd.data.get( i );
                if( value instanceof Array ) {
                    var array = ( Array ) value;
                    if( !array.isEmpty() ) uniqueArray.add( array );
                } else {
                    var longValue = dimension.getOrDefault( value, ANY_AS_ARRAY );
                    if( longValue != ANY_AS_ARRAY ) unique.add( longValue[0] );
                }

            }

            if( !isArray && unique.size() > 0 && ( unique.size() > uniqueSize || dimension.priority > priority ) ) {
                uniqueSize = unique.size();
                splitDimension = i;
                priority = dimension.priority;
            } else if( isArray && uniqueArray.size() > 0 && ( uniqueArray.size() < uniqueArraySize || dimension.priority > priorityArray ) ) {
                uniqueArraySize = uniqueArray.size();
                splitArrayDimension = i;
                priorityArray = dimension.priority;
            }
        }

        if( splitDimension < 0 && splitArrayDimension < 0 ) return null;

        final int finalSplitDimension = splitDimension >= 0 ? splitDimension : splitArrayDimension;

        var dimension = dimensions.get( finalSplitDimension );

        if( dimension.operationType == null ) { //array

            Pair<List<ValueData<T>>, List<ValueData<T>>> partition = Lists.partition( data, vd -> !getArrayFromDataList( finalSplitDimension, vd ).isEmpty() );

            return new SplitDimension( finalSplitDimension, Consts.ANY, emptyList(), emptyList(), emptyList(), partition._2, partition._1, emptyList() );
        } else {

            var partitionAnyOther = Stream.of( data ).partition( sd -> dimension.getOrDefault( sd.data.get( finalSplitDimension ), ANY_AS_ARRAY ) == ANY_AS_ARRAY );

            var sorted = partitionAnyOther._2
                .sorted( Comparator.comparingLong( sd -> dimension.getOrDefault( sd.data.get( finalSplitDimension ), ANY_AS_ARRAY )[0] ) )
                .collect( toList() );

            final long[] unique = sorted
                .stream()
                .mapToLong( sd -> dimension.getOrDefault( sd.data.get( finalSplitDimension ), ANY_AS_ARRAY )[0] ).distinct().toArray();

            if( ( dimension.operationType == CONTAINS || dimension.operationType == CONTAINS_ALL )
                && unique.length > 1
                && ( double ) unique.length / uniqueCount[finalSplitDimension] > hashFillFactor ) {
                final List<ValueData<T>> any = partitionAnyOther._1.collect( toList() );

                return new SplitDimension( finalSplitDimension, Consts.ANY, emptyList(), emptyList(), emptyList(), any, emptyList(), sorted );
            } else {

//                final long splitValue = dimension.getOrDefault( sorted.get( sorted.size() / 2).data.get( finalSplitDimension ), ANY_AS_ARRAY )[0];
                final long splitValue = unique[unique.length / 2];

                var partitionLeftEqRight = Stream.of( sorted )
                    .partition( sd -> dimension.getOrDefault( sd.data.get( finalSplitDimension ), ANY_AS_ARRAY )[0] < splitValue );
                var partitionEqRight = partitionLeftEqRight._2
                    .partition( sd -> dimension.getOrDefault( sd.data.get( finalSplitDimension ), ANY_AS_ARRAY )[0] == splitValue );

                final List<ValueData<T>> left = partitionLeftEqRight._1.collect( toList() );
                final List<ValueData<T>> right = partitionEqRight._2.collect( toList() );
                final List<ValueData<T>> eq = partitionEqRight._1.collect( toList() );
                final List<ValueData<T>> any = Stream.of( partitionAnyOther._1 ).collect( toList() );

                return new SplitDimension( finalSplitDimension, splitValue, left, right, eq, any, emptyList(), emptyList() );
            }
        }
    }

    private Array getArrayFromDataList( int finalSplitDimension, ValueData<T> vd ) {
        Object o = vd.data.get( finalSplitDimension );
        return o instanceof Array arr ? arr : new Array( Collections.emptyList(), ArrayOperation.OR );
    }

    public Set<T> find( List<?> query ) {
        return find( query, new ArrayList<>() );
    }

    /**
     * Paths will collect all path to be walked in order to make it easy to understand how and why the result was reached.
     * NOTE: in order to have node name you have to set 'fullDebug' to true.
     * @param query
     * @param paths list where debug info is collected
     * @return selections for given query
     */
    public Set<T> find( List<?> query, List<String> paths ) {
        var result = new HashSet<T>();
        var longQuery = getLongQuery( query );

        if( preFilter ) {
            for( var pd : preFilters ) {
                var vals = longQuery[pd.index];
                var found = false;
                for( var v : vals ) {
                    if( ( pd.bitSet.isEmpty() || pd.bitSet.get( v ) )
                        && ( pd.notBitSet.isEmpty() || !pd.notBitSet.get( v ) ) ) {
                        found = true;
                        break;
                    }
                }

                if( !found ) {
                    pd.dimension.preFilterRejectCounter.increment();
                    return Set.of();
                }
            }
        }

        find( root, longQuery, result, paths );
        return result;
    }

    private void find( TreeNode<T> node, long[][] query, HashSet<T> result, List<String> paths ) {
        if( node == null ) return;

        if( node instanceof Leaf ) {
            List<T> selections = ( ( Leaf<T> ) node ).selections;
            result.addAll( selections );
            paths.add( nodeToString( node ) + " -> success: " + Joiner.on( ", " ).join( selections ) );
        } else if( node instanceof Tree.Node ) {
            final Node n = ( Node ) node;
            find( n.any, query, result, paths );

            final long[] qValue = query[n.dimension];

            final var dimension = dimensions.get( n.dimension );

            if( qValue == ANY_AS_ARRAY ) return;

            var sets = n.sets;
            if( !sets.isEmpty() ) {
                for( ArrayBitSet set : sets ) {
                    if( set.find( qValue ) ) {
                        paths.add( nodeToString( set.equal ) + " -> go equal" );
                        find( set.equal, query, result, paths );
                    }
                }
            } else {
                var direction = dimension.direction( qValue, n.eqValue );
                if( ( direction & LEFT ) > 0 ) {
                    paths.add( nodeToString( n.left ) + " -> go left" );
                    find( n.left, query, result, paths );
                }
                if( ( direction & EQUAL ) > 0 ) {
                    paths.add( nodeToString( n.equal ) + " -> go equal" );
                    find( n.equal, query, result, paths );
                }
                if( ( direction & RIGHT ) > 0 ) {
                    paths.add( nodeToString( n.right ) + " -> go right" );
                    find( n.right, query, result, paths );
                }
            }
        } else {
            final HashNode n = ( HashNode ) node;
            paths.add( nodeToString( n.any ) + " -> go any" );
            find( n.any, query, result, paths );

            final long[] qValue = query[n.dimension];

            final TreeNode<T>[] hash = n.hash;
            if( qValue == ANY_AS_ARRAY ) return;

            for( long aQValue : qValue ) {
                final int index = ( int ) aQValue;
                if( index < hash.length ) {
                    paths.add( nodeToString( hash[index] ) + " -> go index" );
                    find( hash[index], query, result, paths );
                }
            }
        }
    }

    private String nodeToString( TreeNode<T> node ) {
        if ( !fullDebug || node == null ) return "";
        StringBuilder res = new StringBuilder();
        node.print( res );
        return res.toString();
    }

    public String trace( List<?> query ) {
        return trace( query, key -> true );
    }

    private void trace( TreeNode<T> node,
                        long[][] query,
                        HashMap<T, HashMap<Integer, TraceOperationTypeValues>> result,
                        Set<T> fitsForQuery,
                        TraceBuffer buffer,
                        boolean success ) {
        if( node == null ) return;

        if( node instanceof Leaf ) {
            var selections = ( ( Leaf<T> ) node ).selections;
            if( !success ) {
                selections.forEach( s -> {
                    var dv = result.computeIfAbsent( s, ss -> new HashMap<>() );
                    buffer.forEach( ( d, otv ) ->
                        otv.forEach( ( ot, v ) ->
                            dv.computeIfAbsent( d, dd -> new TraceOperationTypeValues() ).addAll( ot, v )
                        )
                    );
                } );
            } else {
                selections.forEach( s -> {
                    fitsForQuery.add( s );
                    result.remove( s );
                } );
            }
        } else if( node instanceof Tree.Node ) {
            var n = ( Node ) node;
            trace( n.any, query, result, fitsForQuery, buffer.clone(), success );

            var qValue = query[n.dimension];

            var dimension = dimensions.get( n.dimension );

            if( qValue == ANY_AS_ARRAY ) {
                trace( n.equal, query, result, fitsForQuery, buffer.cloneWith( n.dimension, n.eqValue, dimension.operationType, false ), false );
                trace( n.right, query, result, fitsForQuery, buffer.clone(), false );
                trace( n.left, query, result, fitsForQuery, buffer.clone(), false );

                for( var set : n.sets ) {
                    trace( set.equal, query, result, fitsForQuery, buffer.cloneWith( n.dimension, set.bitSet.stream(), set.operation.operationType, false ), false );
                }
            } else if( !n.sets.isEmpty() ) {
                for( var set : n.sets ) {
                    var eqSuccess = set.find( qValue );
                    trace( set.equal, query, result, fitsForQuery, buffer.cloneWith( n.dimension, set.bitSet.stream(), set.operation.operationType, eqSuccess ), success && eqSuccess );
                }
            } else {
                var direction = dimension.direction( qValue, n.eqValue );

                var left = ( direction & LEFT ) > 0;
                trace( n.left, query, result, fitsForQuery, buffer.clone(), success && left );

                var right = ( direction & RIGHT ) > 0;
                trace( n.right, query, result, fitsForQuery, buffer.clone(), success && right );

                var eq = ( direction & EQUAL ) > 0;
                trace( n.equal, query, result, fitsForQuery, buffer.cloneWith( n.dimension, n.eqValue, dimension.operationType, eq ), success && eq );
            }
        } else {
            var n = ( HashNode ) node;
            trace( n.any, query, result, fitsForQuery, buffer.clone(), success );

            var qValue = query[n.dimension];

            var dimension = dimensions.get( n.dimension );

            if( qValue == ANY_AS_ARRAY ) {
                for( var s : n.hash ) {
                    trace( s, query, result, fitsForQuery, buffer.clone(), false );
                }
            } else {
                for( var i = 0; i < n.hash.length; i++ ) {
                    var contains = ArrayUtils.contains( qValue, i );
                    trace( n.hash[i], query, result, fitsForQuery, buffer.cloneWith( n.dimension, i, dimension.operationType, contains ), success && contains );
                }
            }
        }
    }

    @SuppressWarnings( "checkstyle:UnnecessaryParentheses" )
    public String trace( List<?> query, Predicate<T> filter ) {
        var result = new HashMap<T, HashMap<Integer, TraceOperationTypeValues>>();
        var fitsForQuery = new TreeSet<T>();
        var longQuery = getLongQuery( query );

        var queryStr = "query = " + Stream.of( query )
            .zipWithIndex()
            .map( p -> dimensions.get( p._2 ).name + ":" + printValue( p._1 ) )
            .collect( joining( ",", "[", "]" ) ) + "\n";

        if( root == null ) {
            return queryStr + "Tree is empty";
        }

        var outPF = new StringBuilder();
        if( preFilter ) {
            for( var pd : preFilters ) {
                var vals = longQuery[pd.index];
                var found = false;
                for( var v : vals ) {
                    if( ( pd.bitSet.isEmpty() || pd.bitSet.get( v ) )
                        && ( pd.notBitSet.isEmpty() || !pd.notBitSet.get( v ) ) ) {
                        found = true;
                        break;
                    }
                }

                if( !found ) {
                    outPF.append( "  Dimension: " ).append( pd.dimension.name ).append( ", q: " ).append( queryToString( query, pd.index ) ).append( "\n" );
                }
            }
        }

        if( outPF.length() == 0 ) {
            trace( root, longQuery, result, fitsForQuery, new TraceBuffer(), true );
        }
        result
            .entrySet()
            .stream()
            .map( e -> e.getKey() + " -> " + filter.test( e.getKey() ) )
            .toList();

        var out = result
            .entrySet()
            .stream()
            .filter( e -> filter.test( e.getKey() ) )
            .map( e -> e.getKey().toString() + ": \n"
                    + e.getValue().entrySet().stream().map( dv -> {
                        var dimension = dimensions.get( dv.getKey() );
                        return "    " + dimension.name + "/" + dv.getKey() + ": "
                            + dv.getValue().toString( dimension ) + " " + queryToString( query, dv.getKey() );
                    }
                ).collect( joining( "\n" ) )
            ).collect( joining( "\n" ) );

        return queryStr
            + ( outPF.length() > 0 ? "Tree Prefilters:\n" + outPF : "" )
            + ( out.length() > 0 ? "Expecting:\n" + out : ( outPF.length() == 0 ? "ALL OK" : "" ) )
            + ( !fitsForQuery.isEmpty() ? "\nFound:\n" + Joiner.on( ", " ).join( fitsForQuery ) + "\n" : "\nFound:\n0 selections\n" );
    }

    @SuppressWarnings( "checkstyle:UnnecessaryParentheses" )
    private String printValue( Object o ) {
        if( o == null
            || ( o instanceof Optional<?> && ( ( Optional<?> ) o ).isEmpty() )
            || ( o instanceof List<?> && ( ( List<?> ) o ).isEmpty() )
        ) {
            return Strings.UNKNOWN;
        }
        return o.toString();
    }

    public Map<T, Map<String, Integer>> traceStatistics( List<List<?>> queries ) {
        var resultStats = new HashMap<T, Map<String, Integer>>();

        for( List<?> query : queries ) {
            var result = new HashMap<T, HashMap<Integer, TraceOperationTypeValues>>();
            var fitsForQuery = new LinkedHashSet<T>();
            var longQuery = getLongQuery( query );
            trace( root, longQuery, result, fitsForQuery, new TraceBuffer(), true );

            var stats = result
                .entrySet()
                .stream()
                .collect( toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().entrySet().stream().collect( toMap(
                        dv -> dimensions.get( dv.getKey() ).name,
                        dv -> 1
                    ) )
                ) );

            mergeInto( stats, resultStats );
        }

        return resultStats;
    }

    private void mergeInto( Map<T, Map<String, Integer>> stat, HashMap<T, Map<String, Integer>> result ) {
        stat.forEach( ( s, m ) -> {
            final Map<String, Integer> statBySelection = result.computeIfAbsent( s, ss -> new HashMap<>() );

            m.forEach( ( dimension, count ) ->
                statBySelection.compute( dimension, ( d, c ) -> c == null ? count : c + count ) );
        } );

    }

    private String queryToString( List<?> query, int key ) {
        final Object value = query.get( key );
        if( value instanceof List<?> ) {
            return ( ( List<?> ) value ).stream().map( v -> v == null ? Strings.UNKNOWN
                : String.valueOf( v ) ).collect( joining( ",", "[", "]" ) );
        } else {
            return queryValueToString( value );
        }
    }

    private String queryValueToString( Object value ) {
        return value == null || value == Optional.empty() ? Strings.UNKNOWN : String.valueOf( value );
    }

    @Override
    public String toString() {
        return toString( -1 );
    }

    public String toString( int depth ) {
        var out = new StringBuilder();

        print( root, out, depth );

        return out.toString();
    }

    private void print( String prefix, boolean isTail, TreeNode<T> node, StringBuilder out, String type, int level, int depth ) {
        out.append( prefix ).append( isTail ? "└── " : "├── " ).append( type ).append( ":" );
        if( node != null ) {
            node.print( out );
            out.append( "\n" );

            if( depth > 0 && level >= depth ) return;

            var children = Lists.filter( node.children(), p -> p._2 != null );

            for( int i = 0; i < children.size(); i++ ) {
                var child = children.get( i );
                var name = child._1;
                var value = child._2;

                if( value != null )
                    print( prefix + ( isTail ? "    "
                        : "│   " ), i + 1 >= children.size(), value, out, name, level + 1, depth );

            }
        }
    }

    private void print( TreeNode<T> node, StringBuilder out, int depth ) {
        print( "", true, node, out, "root", 1, depth );
    }

    public int getMaxDepth() {
        var depth = new AtomicInteger( 0 );
        findMaxDepth( root, depth, 1 );

        return depth.get();
    }

    private void findMaxDepth( TreeNode<T> node, AtomicInteger maxDepth, int currentDepth ) {
        if( node == null ) {
            if( currentDepth - 1 > maxDepth.get() ) maxDepth.set( currentDepth - 1 );
            return;
        }

        if( node instanceof Leaf ) {
            if( currentDepth > maxDepth.get() ) maxDepth.set( currentDepth );
        } else if( node instanceof Tree.Node ) {
            var n = ( Node ) node;
            findMaxDepth( n.left, maxDepth, currentDepth + 1 );
            findMaxDepth( n.right, maxDepth, currentDepth + 1 );
            findMaxDepth( n.any, maxDepth, currentDepth + 1 );
            findMaxDepth( n.equal, maxDepth, currentDepth + 1 );

            for( var abs : n.sets ) {
                findMaxDepth( abs.equal, maxDepth, currentDepth + 1 );
            }
        } else {
            var n = ( HashNode ) node;

            findMaxDepth( n.any, maxDepth, currentDepth + 1 );
            for( var i = 0; i < n.hash.length; i++ ) {
                findMaxDepth( n.hash[i], maxDepth, currentDepth + 1 );
            }
        }
    }


    public enum ArrayOperation {
        OR( CONTAINS ), AND( CONTAINS_ALL ), NOT( NOT_CONTAINS );

        public final OperationType operationType;

        ArrayOperation( OperationType operationType ) {
            this.operationType = operationType;
        }
    }

    private interface TreeNode<T> {
        List<Pair<String, TreeNode<T>>> children();

        void print( StringBuilder out );
    }

    public static class PreFilter {
        public final Dimension<?> dimension;
        public final int index;
        public final oap.util.BitSet bitSet;
        public final oap.util.BitSet notBitSet;

        public PreFilter( Dimension<?> dimension, int index, oap.util.BitSet bitSet, oap.util.BitSet notBitSet ) {
            this.dimension = dimension;
            this.index = index;
            this.bitSet = bitSet;
            this.notBitSet = notBitSet;
        }
    }

    @ToString( callSuper = true )
    @EqualsAndHashCode( callSuper = true )
    public static class Array extends ArrayList<Object> {
        public final ArrayOperation operation;

        public Array( Collection<?> c, ArrayOperation operation ) {
            super( c );
            this.operation = operation;
        }
    }

    @ToString
    public static class ValueData<T> {
        public final List<?> data;
        public final T value;

        public ValueData( List<?> data, T value ) {
            this.data = data;
            this.value = value;
        }

        public ValueData<T> cloneWith( int index, Object item ) {
            var data = new ArrayList<Object>( this.data );
            data.set( index, item );
            return new ValueData<>( data, value );
        }
    }

    @ToString
    static class Leaf<T> implements TreeNode<T> {
        final List<T> selections;

        private Leaf( List<T> selections ) {
            this.selections = selections;
        }

        @Override
        public List<Pair<String, TreeNode<T>>> children() {
            return Collections.emptyList();
        }

        @Override
        public void print( StringBuilder out ) {
            var collect = selections.stream()
                .map( Object::toString )
                .collect( java.util.stream.Collectors.joining( "," ) );
            out.append( "dn|[" )
                .append( collect )
                .append( "]" );
        }
    }

    public static class TreeArrayStatistic {
        public final HashMap<ArrayOperation, HashMap<Long, AtomicInteger>> counts = new HashMap<>();
        public final HashMap<ArrayOperation, HashMap<Long, AtomicInteger>> size = new HashMap<>();

        public void update( ArrayOperation operationType, long count ) {
            if( count > 0 )
                counts
                    .computeIfAbsent( operationType, op -> new HashMap<>() )
                    .computeIfAbsent( count, i -> new AtomicInteger() )
                    .incrementAndGet();
        }

        public void updateSize( ArrayOperation operationType, long count ) {
            size
                .computeIfAbsent( operationType, op -> new HashMap<>() )
                .computeIfAbsent( count, i -> new AtomicInteger() )
                .incrementAndGet();
        }
    }

    private class TraceOperationTypeValues extends HashMap<OperationType, HashSet<Long>> {
        public void add( OperationType operationType, long eqValue ) {
            this.computeIfAbsent( operationType, ot -> new HashSet<>() ).add( eqValue );
        }

        public void addAll( OperationType operationType, HashSet<Long> v ) {
            this.computeIfAbsent( operationType, ot -> new HashSet<>() ).addAll( v );
        }

        public String toString( Dimension<?> dimension ) {
            return entrySet().stream()
                .map( e -> {
                        var size = e.getValue().size();

                        return e.getValue().stream()
                            .limit( maxTraceListCount )
                            .map( dimension::toString )
                            .collect( joining( ",", "[", size > maxTraceListCount ? ",...]" : "]" ) ) + " " + e.getKey();
                    }
                )
                .collect( joining( ", " ) );
        }
    }

    private class TraceBuffer extends HashMap<Integer, TraceOperationTypeValues> {


        private TraceBuffer() {
        }

        private TraceBuffer( Map<Integer, TraceOperationTypeValues> m ) {
            super( m );
        }

        @Override
        public TraceBuffer clone() {
            return new TraceBuffer( this );
        }

        public TraceBuffer cloneWith( int dimension, long eqValue, OperationType operationType, boolean success ) {
            return cloneWith( dimension, LongStream.of( eqValue ), operationType, success );
        }

        public TraceBuffer cloneWith( int dimension, IntStream eqValue, OperationType operationType, boolean success ) {
            return cloneWith( dimension, eqValue.mapToLong( v -> v ), operationType, success );
        }

        public TraceBuffer cloneWith( int dimension, LongStream eqValue, OperationType operationType, boolean success ) {
            final TraceBuffer clone = clone();
            if( !success ) {
                final TraceOperationTypeValues v = clone
                    .computeIfAbsent( dimension, d -> new TraceOperationTypeValues() );

                eqValue.forEach( eqv -> v.add( operationType, eqv ) );
            }
            return clone;
        }
    }

    @ToString
    private class ArrayBitSet {
        private final BitSet bitSet;
        private final ArrayOperation operation;
        private final TreeNode<T> equal;

        private ArrayBitSet( BitSet bitSet, ArrayOperation operation, TreeNode<T> equal ) {
            this.bitSet = bitSet;
            this.operation = operation;
            this.equal = equal;
        }

        public final boolean find( long[] qValue ) {
            switch( operation ) {
                case OR -> {
                    for( long value : qValue ) {
                        if( bitSet.get( ( int ) value ) ) return true;
                    }
                    return false;
                }
                case AND -> {
                    var newBitSet = ( BitSet ) bitSet.clone();
                    for( long value : qValue ) {
                        newBitSet.clear( ( int ) value );
                    }
                    return newBitSet.isEmpty();
                }
                case NOT -> {
                    for( long value : qValue ) {
                        if( bitSet.get( ( int ) value ) ) return false;
                    }
                    return true;
                }
                default -> throw new IllegalStateException( "Unknown Operation type " + operation.name() );
            }
        }
    }

    private class SplitDimension {
        private final List<ValueData<T>> left;
        private final List<ValueData<T>> right;
        private final List<ValueData<T>> equal;
        private final List<ValueData<T>> any;
        private final List<ValueData<T>> sets;
        private final List<ValueData<T>> hash;
        private final int dimension;
        private final long value;

        private SplitDimension(
            int dimension,
            long value,
            List<ValueData<T>> left,
            List<ValueData<T>> right,
            List<ValueData<T>> equal,
            List<ValueData<T>> any,
            List<ValueData<T>> sets,
            List<ValueData<T>> hash
        ) {
            this.dimension = dimension;
            this.value = value;

            this.left = left;
            this.right = right;
            this.equal = equal;
            this.any = any;
            this.sets = sets;
            this.hash = hash;
        }
    }

    @ToString
    class HashNode implements TreeNode<T> {
        final TreeNode<T>[] hash;
        final int dimension;
        final TreeNode<T> any;

        HashNode( int dimension, TreeNode<T>[] hash, TreeNode<T> any ) {
            this.hash = hash;
            this.dimension = dimension;
            this.any = any;
        }

        @Override
        public List<Pair<String, TreeNode<T>>> children() {
            var result = new ArrayList<Pair<String, TreeNode<T>>>();
            result.add( __( "a", any ) );

            for( var i = 0; i < hash.length; i++ ) {
                var heq = hash[i];
                result.add( __( "h" + i, heq ) );
            }

            return result;
        }

        @Override
        public void print( StringBuilder out ) {
            var dimension = dimensions.get( this.dimension );
            out.append( "kdh|" )
                .append( "d:" )
                .append( dimension.name ).append( '/' ).append( this.dimension );
        }
    }

    @ToString
    class Node implements TreeNode<T> {
        final List<ArrayBitSet> sets;
        final TreeNode<T> left;
        final TreeNode<T> right;
        final TreeNode<T> equal;
        final TreeNode<T> any;
        final int dimension;
        final long eqValue;

        private Node( int dimension, long eqValue, TreeNode<T> left, TreeNode<T> right,
                      TreeNode<T> equal, TreeNode<T> any, List<ArrayBitSet> sets ) {
            this.dimension = dimension;
            this.eqValue = eqValue;
            this.left = left;
            this.right = right;
            this.equal = equal;
            this.any = any;
            this.sets = sets;
        }

        @Override
        public List<Pair<String, TreeNode<T>>> children() {
            var result = new ArrayList<Pair<String, TreeNode<T>>>();
            result.add( __( "l", left ) );
            result.add( __( "r", right ) );
            result.add( __( "eq", equal ) );
            result.add( __( "a", any ) );

            for( var set : sets )
                result.add( __( ( set.operation.name() + ":" ) + bitSetToData( set.bitSet ), set.equal ) );

            return result;
        }

        private String bitSetToData( BitSet bitSet ) {
            var dimension = dimensions.get( this.dimension );

            var size = bitSet.stream().limit( maxTraceListCount + 1 ).count();
            return bitSet
                .stream()
                .limit( maxTraceListCount )
                .mapToObj( dimension::toString ).collect( joining( ",", "[",
                    size > maxTraceListCount ? ",...]" : "]" ) );
        }

        @Override
        public void print( StringBuilder out ) {
            var dimension = dimensions.get( this.dimension );
            out.append( "kdn|" )
                .append( "d:" )
                .append( dimension.name ).append( '/' ).append( this.dimension )
                .append( ",sv:" ).append( dimension.toString( eqValue ) );
        }
    }

    @NotNull
    public long[][] getLongQuery( List<?> query ) {
        return Dimension.convertQueryToLong( dimensions, query );
    }

    public List<? extends Dimension<?>> getDimensions() {
        return new ArrayList<>( dimensions );
    }
}
