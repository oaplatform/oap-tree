package oap.tree;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import static oap.tree.Consts.ANY_AS_ARRAY;

@Slf4j
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
        if( value instanceof Boolean boolVal ) {
            return Boolean.TRUE.equals( boolVal ) ? 1 : 0;
        }
        log.warn( "dimension value '{}' for '{}' must be Boolean, but was '{}'", value, name, value.getClass().getCanonicalName() );
        return 0;
    }

    @Override
    public BooleanDimension cloneAndReset() {
        return new BooleanDimension( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
    }
}
