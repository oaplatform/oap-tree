package oap.tree;

import lombok.NonNull;

import static oap.tree.Consts.ANY_AS_ARRAY;

public class LongDimension extends Dimension<LongDimension> {
    public LongDimension( String name, OperationType operationType, int priority, Long nullValue, boolean emptyAsFailed, String groupName ) {
        super( name, operationType, priority,
            nullValue == null ? ANY_AS_ARRAY : new long[] { nullValue }, emptyAsFailed, false, groupName );
    }

    private LongDimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                           boolean emptyAsFailed, boolean preFilter, String groupName ) {
        super( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
    }

    @Override
    public String toString( long value ) {
        return String.valueOf( value );
    }

    @Override
    protected void _init( Object value ) {
    }

    @Override
    protected long _getOrDefault( Object value ) {
        if ( value instanceof Number numb ) {
            return ( numb ).longValue();
        }
        throw new IllegalArgumentException( "dimension value '" + value + "' for '" + name + "' must be Number" );
    }

    @Override
    public LongDimension cloneAndReset() {
        return new LongDimension( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
    }
}
