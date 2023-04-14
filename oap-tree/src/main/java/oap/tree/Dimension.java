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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import oap.util.StringBits;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@SuppressWarnings( { "checkstyle:AbstractClassName", "checkstyle:MethodName" } )
@EqualsAndHashCode
public abstract class Dimension<Self extends Dimension<Self>> {
    public static final int PRIORITY_DEFAULT = 0;
    public static final String EMPTY = "";
    public static final int PRIORITY_LOW = Integer.MIN_VALUE;

    public final String name;
    public final int priority;
    public final long[] nullAsLong;
    public final boolean emptyAsFailed;
    public final boolean preFilter;
    public final Counter preFilterRejectCounter;
    public final String groupName;
    public OperationType operationType;

    @Deprecated
    public Dimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                      boolean emptyAsFailed, boolean preFilter ) {
        this.name = name;
        this.operationType = operationType;
        this.priority = priority;
        this.nullAsLong = nullAsLong;
        this.emptyAsFailed = emptyAsFailed;
        this.preFilter = preFilter;
        this.groupName = EMPTY;

        preFilterRejectCounter = Metrics.counter( "tree.prefilter", "name", name, "type", "reject" );
    }

    public Dimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                      boolean emptyAsFailed, boolean preFilter, String groupName ) {
        this.name = name;
        this.priority = priority;
        this.nullAsLong = nullAsLong;
        this.emptyAsFailed = emptyAsFailed;
        this.preFilter = preFilter;
        this.operationType = operationType;
        this.groupName = groupName;

        preFilterRejectCounter = Metrics.counter( "tree.prefilter", "name", name, "type", "reject" );
    }

    public static <T extends Enum<?>> Dimension ARRAY_ENUM( String name, Class<T> clazz, T nullValue, boolean emptyAsFailed, String groupName ) {
        return ENUM( name, clazz, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed, groupName );
    }

    public static <T extends Enum<?>> Dimension ARRAY_ENUM( String name, Class<T> clazz, T nullValue, boolean emptyAsFailed ) {
        return ENUM( name, clazz, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed );
    }

    public static <T extends Enum<?>> Dimension ARRAY_ENUM( String name, Class<T> clazz, T nullValue ) {
        return ENUM( name, clazz, null, PRIORITY_DEFAULT, nullValue, false );
    }

    public static <T extends Enum<?>> Dimension ENUM( String name, Class<T> clazz, OperationType operationType, T nullValue ) {
        return ENUM( name, clazz, operationType, PRIORITY_DEFAULT, nullValue, false );
    }

    public static <T extends Enum<?>> Dimension ENUM( String name, Class<T> clazz, OperationType operationType, int priority, T nullValue, boolean emptyAsFailed ) {
        return ENUM( name, clazz, operationType, priority, nullValue, emptyAsFailed, EMPTY );
    }

    public static <T extends Enum<?>> Dimension ENUM( String name, Class<T> clazz, OperationType operationType,
                                                      int priority, T nullValue, boolean emptyAsFailed, String groupName ) {
        return new EnumDimension<T>( name, clazz, operationType, priority, nullValue, emptyAsFailed, groupName );
    }

    public static Dimension ARRAY_STRING( String name, boolean emptyAsFailed, boolean preFilter ) {
        return STRING( name, null, PRIORITY_DEFAULT, emptyAsFailed, preFilter );
    }

    public static Dimension ARRAY_STRING( String name, boolean emptyAsFailed, int initialCapacity, float loadFactor, boolean preFilter, String groupName ) {
        return STRING( name, null, PRIORITY_DEFAULT, emptyAsFailed, initialCapacity, loadFactor, preFilter, groupName );
    }

    public static Dimension ARRAY_STRING( String name, boolean emptyAsFailed, int initialCapacity, float loadFactor, boolean preFilter ) {
        return STRING( name, null, PRIORITY_DEFAULT, emptyAsFailed, initialCapacity, loadFactor, preFilter );
    }

    public static Dimension ARRAY_STRING( String name, boolean preFilter ) {
        return STRING( name, null, PRIORITY_DEFAULT, false, preFilter );
    }

    public static Dimension STRING( String name, OperationType operationType, boolean preFilter ) {
        return STRING( name, operationType, PRIORITY_DEFAULT, false, preFilter );
    }

    public static Dimension STRING( String name, OperationType operationType, int priority,
                                    boolean emptyAsFailed, boolean preFilter ) {
        return STRING( name, operationType, priority, emptyAsFailed, 16, 0.75f, preFilter );
    }

    public static Dimension STRING( String name, OperationType operationType, int priority,
                                    boolean emptyAsFailed, int initialCapacity, float loadFactor, boolean preFilter ) {
        return STRING( name, operationType, priority, emptyAsFailed, initialCapacity, loadFactor, preFilter, EMPTY );
    }

    public static Dimension STRING( String name, OperationType operationType, int priority,
                                    boolean emptyAsFailed, int initialCapacity, float loadFactor, boolean preFilter, String groupName ) {
        return new StringDimension( name, operationType, priority, new long[] { StringBits.UNKNOWN },
            emptyAsFailed, initialCapacity, loadFactor, preFilter, groupName );
    }

    public static Dimension ARRAY_LONG( String name, Long nullValue ) {
        return LONG( name, null, PRIORITY_DEFAULT, nullValue, false );
    }

    public static Dimension ARRAY_LONG( String name, Long nullValue, boolean emptyAsFailed ) {
        return LONG( name, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed );
    }

    public static Dimension ARRAY_LONG( String name, Long nullValue, boolean emptyAsFailed, String groupName ) {
        return LONG( name, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed, groupName );
    }

    public static Dimension LONG( String name, OperationType operationType, Long nullValue ) {
        return LONG( name, operationType, PRIORITY_DEFAULT, nullValue, false );
    }

    public static Dimension LONG( String name, OperationType operationType, int priority, Long nullValue, boolean emptyAsFailed ) {
        return LONG( name, operationType, priority, nullValue, emptyAsFailed, EMPTY );
    }

    public static Dimension LONG( String name, OperationType operationType, int priority, Long nullValue, boolean emptyAsFailed, String groupName ) {
        return new LongDimension( name, operationType, priority, nullValue, emptyAsFailed, groupName );
    }

    public static Dimension ARRAY_BOOLEAN( String name, Boolean nullValue ) {
        return BOOLEAN( name, null, PRIORITY_DEFAULT, nullValue, false );
    }

    public static Dimension ARRAY_BOOLEAN( String name, Boolean nullValue, boolean emptyAsFailed ) {
        return BOOLEAN( name, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed );
    }

    public static Dimension ARRAY_BOOLEAN( String name, Boolean nullValue, boolean emptyAsFailed, String groupName ) {
        return BOOLEAN( name, null, PRIORITY_DEFAULT, nullValue, emptyAsFailed, groupName );
    }

    public static Dimension BOOLEAN( String name, OperationType operationType, Boolean nullValue ) {
        return BOOLEAN( name, operationType, PRIORITY_DEFAULT, nullValue, false );
    }

    public static Dimension BOOLEAN( String name, OperationType operationType, int priority, Boolean nullValue, boolean emptyAsFailed ) {
        return BOOLEAN( name, operationType, priority, nullValue, emptyAsFailed, EMPTY );
    }

    public static Dimension BOOLEAN( String name, OperationType operationType, int priority, Boolean nullValue, boolean emptyAsFailed, String groupName ) {
        return new BooleanDimension( name, operationType, priority, nullValue, emptyAsFailed, groupName );
    }

    public static long[][] convertQueryToLong( List<? extends Dimension<?>> dimensions, List<?> query ) {
        var size = dimensions.size();
        var longData = new long[size][];

        for( var i = 0; i < size; i++ ) {
            var value = query.get( i );
            var dimension = dimensions.get( i );
            longData[i] = dimension.getOrNullValue( value );
        }

        return longData;
    }

    public abstract String toString( long value );

    @Override
    public String toString() {
        return name;
    }

    public final void init( Object value ) {
        if( value == null ) return;
        if( value instanceof Optional<?> ) {
            ( ( Optional<?> ) value ).ifPresent( this::_init );
        } else
            _init( value );
    }

    protected abstract void _init( Object value );

    public final long[] getOrNullValue( Object value ) {
        return getOrDefault( value, nullAsLong );
    }

    public final long[] getOrDefault( Object value, long[] emptyValue ) {
        if( value == null ) return emptyValue;

        if( value instanceof Optional<?> optValue ) {
            return optValue.map( v -> getOrDefault( v, emptyValue ) ).orElse( emptyValue );
        }
        if( value instanceof Collection list ) {
            if( list.isEmpty() ) return emptyValue;
            var res = new long[list.size()];
            var i = 0;
            for( var item : list ) {
                res[i] = _getOrDefault( item );
                i++;
            }
//            if( res.length > 1 ) {
//                Arrays.sort( res );
//            }
            return res;
        }
        if( value instanceof int[] arr ) {
            if( arr.length == 0 ) return emptyValue;
            var res = new long[arr.length];
            for( var i = 0; i < arr.length; i++ ) {
                res[i] = arr[i];
            }
//            if( res.length > 1 ) {
//                Arrays.sort( res );
//            }
            return res;
        }
        if( value instanceof long[] arr ) {
            if( arr.length == 0 ) return emptyValue;

            var res = Arrays.copyOf( arr, arr.length );

//            if( res.length > 1 ) {
//                Arrays.sort( res );
//            }

            return res;
        }
        return new long[] { _getOrDefault( value ) };
    }

    protected abstract long _getOrDefault( Object value );

    @SuppressWarnings( "unchecked" )
    oap.util.BitSet toBitSet( List list ) {
        var bitSet = new oap.util.BitSet();
        list.forEach( item -> bitSet.set( ( int ) this._getOrDefault( item ) ) );
        return bitSet;
    }

    @SuppressWarnings( "checkstyle:UnnecessaryParentheses" )
    public final int direction( long[] qValue, long nodeValue ) {
        var qValueLength = qValue.length;

        var head = qValue[0];
        switch( operationType ) {
            case CONTAINS, CONTAINS_ALL:
                if( qValueLength == 1 ) {
                    if( head > nodeValue ) return Direction.RIGHT;
                    else if( head < nodeValue ) return Direction.LEFT;
                    return Direction.EQUAL;
                }
                var v = 0;
                var last = qValue[qValueLength - 1];
                if( last > nodeValue ) v |= Direction.RIGHT;
                if( head < nodeValue ) v |= Direction.LEFT;

                for( long item : qValue ) {
                    if( item == nodeValue ) {
                        v |= Direction.EQUAL;
                        break;
                    }
                }
                return v;

            case NOT_CONTAINS:
                return qValueLength > 1 || head != nodeValue
                    ? Direction.EQUAL | Direction.LEFT | Direction.RIGHT
                    : Direction.LEFT | Direction.RIGHT;

            case GREATER_THEN:
                if( qValueLength != 1 ) throw new IllegalArgumentException( "GREATER_THEN must be exact 1 argument, but was " + qValueLength );

                if( head < nodeValue ) return Direction.RIGHT | Direction.EQUAL | Direction.LEFT;
                return Direction.RIGHT;

            case GREATER_THEN_OR_EQUAL_TO:
                if( qValueLength != 1 ) throw new IllegalArgumentException( "GREATER_THEN_OR_EQUAL_TO must be exact 1 argument, but was " + qValueLength );

                if( head < nodeValue ) return Direction.EQUAL | Direction.RIGHT | Direction.LEFT;
                else if( head == nodeValue ) return Direction.EQUAL | Direction.RIGHT;
                else return Direction.RIGHT;

            case LESS_THEN_OR_EQUAL_TO:
                if( qValueLength != 1 ) throw new IllegalArgumentException( "LESS_THEN_OR_EQUAL_TO must be exact 1 argument, but was " + qValueLength );

                if( head > nodeValue ) return Direction.EQUAL | Direction.RIGHT | Direction.LEFT;
                else if( head == nodeValue ) return Direction.EQUAL | Direction.LEFT;
                else return Direction.LEFT;

            case LESS_THEN:
                if( qValueLength != 1 ) throw new IllegalArgumentException( "LESS_THEN must be exact 1 argument, but was " + qValueLength );

                if( head > nodeValue ) return Direction.RIGHT | Direction.EQUAL | Direction.LEFT;
                return Direction.LEFT;

            case BETWEEN_INCLUSIVE:
                if( qValueLength != 2 ) throw new IllegalArgumentException( "BETWEEN_INCLUSIVE must be 2 arguments, but was " + qValueLength );

                int ret = 0;
                var right = qValue[1];
                if( right > nodeValue ) ret |= Direction.RIGHT;
                if( head < nodeValue ) ret |= Direction.LEFT;
                if( right == nodeValue || head == nodeValue || ( ret == ( Direction.RIGHT | Direction.LEFT ) ) )
                    ret |= Direction.EQUAL;

                return ret;

            default:
                throw new IllegalStateException( "Unknown OperationType " + operationType );
        }
    }

    public abstract Self cloneAndReset();

    public enum OperationType {
        CONTAINS,
        CONTAINS_ALL,
        NOT_CONTAINS,
        GREATER_THEN,
        GREATER_THEN_OR_EQUAL_TO,
        LESS_THEN,
        LESS_THEN_OR_EQUAL_TO,
        BETWEEN_INCLUSIVE
    }

    @SuppressWarnings( "checkstyle:MemberName" )
    public abstract static class Direction {
        public static final int NONE = 0;
        public static final int LEFT = 1;
        public static final int EQUAL = 1 << 1;
        public static final int RIGHT = 1 << 2;
    }
}
