package oap.tree;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Comparator;

import static oap.tree.Consts.ANY_AS_ARRAY;

@Slf4j
public class EnumDimension<T extends Enum<?>> extends Dimension<EnumDimension<T>> {

    private final String[] sortedToName;
    private final int[] ordinalToSorted;

    public EnumDimension( String name, Class<T> clazz, OperationType operationType,
                          int priority, T nullValue, boolean emptyAsFailed, String groupName ) {
        super( name, operationType, priority,
            nullValue == null ? ANY_AS_ARRAY
                : new long[] { -1 }, emptyAsFailed, false, groupName );

        var enumConstantsSortedByName = clazz.getEnumConstants();
        Arrays.sort( enumConstantsSortedByName, Comparator.comparing( Enum::name ) );

        sortedToName = new String[enumConstantsSortedByName.length];
        ordinalToSorted = new int[enumConstantsSortedByName.length];

        for( int i = 0; i < enumConstantsSortedByName.length; i++ ) {
            sortedToName[i] = enumConstantsSortedByName[i].name();
            ordinalToSorted[enumConstantsSortedByName[i].ordinal()] = i;
        }

        if( nullValue != null )
            nullAsLong[0] = ordinalToSorted[nullValue.ordinal()];
    }

    private EnumDimension( @NonNull String name, OperationType operationType, int priority, long[] nullAsLong,
                           boolean emptyAsFailed, boolean preFilter, String groupName, String[] sortedToName,
                           int[] ordinalToSorted ) {
        super( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName );

        this.sortedToName = sortedToName;
        this.ordinalToSorted = ordinalToSorted;
    }

    @Override
    public String toString( long value ) {
        return sortedToName[( int ) value];
    }

    @Override
    protected void _init( Object value ) {
    }

    @Override
    protected long _getOrDefault( Object value ) {
        if ( value instanceof Enum<?> enumVal ) {
            return ordinalToSorted[ ( enumVal ).ordinal() ];
        }
        throw new IllegalArgumentException( "dimension value '" + value + "' for '" + name + "' must be Enum" );
    }

    @Override
    public EnumDimension<T> cloneAndReset() {
        return new EnumDimension<>( name, operationType, priority, nullAsLong, emptyAsFailed, preFilter, groupName,
            sortedToName, ordinalToSorted );
    }
}
