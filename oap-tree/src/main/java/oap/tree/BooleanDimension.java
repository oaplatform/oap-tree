package oap.tree;

import lombok.NonNull;

import static oap.tree.Consts.ANY_AS_ARRAY;

public class BooleanDimension extends Dimension<BooleanDimension> {
    public BooleanDimension( String name, OperationType operationType, int priority,
                             Boolean nullValue, boolean emptyAsFailed, String groupName ) {
        super( name, operationType, priority, nullValue == null ? ANY_AS_ARRAY : new long[] { nullValue ? 1 : 0 },
            emptyAsFailed, false, groupName );
    }

    private BooleanDimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                              boolean emptyAsFailed, boolean preFilter, String groupName ) {
        super( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
    }

    @Override
    public String toString( long value ) {
        return value == 0 ? "false" : "true";
    }

    @Override
    protected void _init( Object value ) {
    }

    @Override
    protected long _getOrDefault( Object value ) {
        assert value instanceof Boolean : "[" + name + "] value (" + value.getClass() + " ) must be Boolean";

        return Boolean.TRUE.equals( value ) ? 1 : 0;
    }

    @Override
    public BooleanDimension cloneAndReset() {
        return new BooleanDimension( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
    }
}
