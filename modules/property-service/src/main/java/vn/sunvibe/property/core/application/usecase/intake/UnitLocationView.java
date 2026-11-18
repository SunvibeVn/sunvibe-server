package vn.sunvibe.property.core.application.usecase.intake;

/**
 * Where a unit is, as its owner reads it (brokerage-agreement E05-S5 spec S5-D2): its code, its location label inside
 * the property ("&lt;code&gt; · &lt;location&gt;" of an intake unit, null on a legacy one), the property's street and ward
 * name. The values are the owner's own property, private: never logged.
 */
public record UnitLocationView(String unitId, String unitCodeOrNull, String locationLabelOrNull, String streetOrNull,
                               String wardNameOrNull) {

    @Override
    public String toString() {
        return "UnitLocationView[" + unitId + "]";
    }
}
