package vn.sunvibe.listing.core.presentation.dto.response;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The allowlist of the anonymous answer (listing-publish E06-S7 spec E06S7-D6, criterion PB-12, its DTO part; ADR-012
 * decision 1): the record components of {@link ListingPublicResponse} and of every nested response it reaches are
 * exactly the public data of listing-publish BR-14 this slice serves and the KT-3 date. A new component fails this test
 * until a review shows it public.
 */
class ListingPublicResponseFieldsTest {

    private static final Map<Class<?>, List<String>> PUBLIC = Map.of(
            ListingPublicResponse.class, List.of("code", "transactionType", "propertyType", "provinceCode", "wardCode",
                    "bedrooms", "bathrooms", "areaSqm", "terms", "title", "description", "features", "liveOn",
                    "lastConfirmedOn"),
            ListingTermsResponse.class, List.of("monthlyRent", "deposit", "minimumTermMonths", "paymentPeriodMonths",
                    "mandatoryFees"),
            ListingRentResponse.class, List.of("amount", "currency", "unit"),
            ListingDepositResponse.class, List.of("basis", "months", "amount", "currency"),
            ListingFeeResponse.class, List.of("name", "basis", "amount", "currency", "chargeUnit"),
            ListingFeaturesResponse.class, List.of("furnishing", "airConditioner", "waterHeater", "fridge",
                    "washingMachine", "kitchen", "balcony", "elevator", "motorbikeParking", "carParking", "internet",
                    "pets", "noSharedLandlord"));

    @Test
    void components_ofTheAnswerAndEveryNestedResponse_exactlyThePublicList() {
        Map<Class<?>, List<String>> found = new LinkedHashMap<>();
        Deque<Class<?>> toVisit = new ArrayDeque<>(List.of(ListingPublicResponse.class));
        while (!toVisit.isEmpty()) {
            Class<?> type = toVisit.pop();
            if (found.containsKey(type)) {
                continue;
            }
            RecordComponent[] components = type.getRecordComponents();
            found.put(type, Arrays.stream(components).map(RecordComponent::getName).toList());
            for (RecordComponent component : components) {
                nestedResponse(component.getGenericType()).forEach(toVisit::add);
            }
        }

        assertEquals(PUBLIC, found);
    }

    /** The response records a component's type names, its list element included. */
    private static List<Class<?>> nestedResponse(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            return Arrays.stream(parameterized.getActualTypeArguments())
                    .flatMap(argument -> nestedResponse(argument).stream())
                    .toList();
        }
        if (type instanceof Class<?> candidate && candidate.isRecord()) {
            return List.of(candidate);
        }
        return List.of();
    }
}
