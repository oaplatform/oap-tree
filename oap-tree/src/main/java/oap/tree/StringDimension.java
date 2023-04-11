package oap.tree;

import lombok.NonNull;
import oap.util.StringBits;

public class StringDimension extends Dimension<StringDimension> {
    private final StringBits bits;
    private final int initialCapacity;
    private final float loadFactor;

    public StringDimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                            boolean emptyAsFailed, int initialCapacity, float loadFactor, boolean preFilter, String groupName ) {
        super( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );
        this.initialCapacity = initialCapacity;
        this.loadFactor = loadFactor;

        bits = new StringBits( initialCapacity, loadFactor );
    }

    private StringDimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                             boolean emptyAsFailed, boolean preFilter, String groupName, int initialCapacity, float loadFactor ) {
        super( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );

        this.initialCapacity = initialCapacity;
        this.loadFactor = loadFactor;

        bits = new StringBits( initialCapacity, loadFactor );
    }

    @Override
    public String toString( long value ) {
        return bits.valueOf( value );
    }

    @Override
    protected void _init( Object value ) {
        bits.computeIfAbsent( ( String ) value );
    }

    @Override
    protected long _getOrDefault( Object value ) {
        if ( value instanceof String str ) {
            return bits.get( str );
        }
        throw new IllegalArgumentException( "dimension value '" + value + "' for '" + name + "' must be String" );
    }

    @Override
    public StringDimension cloneAndReset() {
        return new StringDimension( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName,
            initialCapacity, loadFactor );
    }
}
