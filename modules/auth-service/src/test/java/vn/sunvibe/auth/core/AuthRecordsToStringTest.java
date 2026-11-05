package vn.sunvibe.auth.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * auth-email-access 4.10: no record of the module shows a contact or a secret through {@code toString}, which any log line
 * or debugger can call. Every record that holds one is built with a marker value and printed.
 */
class AuthRecordsToStringTest {

    private static final String MARKER = "SECRET-MARKER";
    private static final Set<String> GUARDED = Set.of("email", "newEmail", "username", "phoneNumber", "phone", "password",
            "currentPassword", "newPassword", "confirmPassword", "code", "otpCode", "resetToken", "registrationToken",
            "accessToken", "refreshToken", "token");

    private static List<Class<?>> records() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        List<Class<?>> records = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("vn.sunvibe.auth.core")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            boolean fixture = type.getName().contains(".testsupport.");
            if (!fixture && Arrays.stream(type.getRecordComponents()).anyMatch(component -> GUARDED.contains(component.getName()))) {
                records.add(type);
            }
        }
        return records;
    }

    private static Object valueFor(RecordComponent component) {
        Class<?> type = component.getType();
        if (type == String.class) {
            return GUARDED.contains(component.getName()) ? MARKER : "plain";
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @Test
    void toString_recordsWithAContactOrASecret_hideIt() throws Exception {
        List<Class<?>> records = records();
        assertTrue(records.size() > 10, "the scan found " + records.size());

        List<String> leaking = new ArrayList<>();
        for (Class<?> type : records) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            Object[] arguments = Arrays.stream(components).map(AuthRecordsToStringTest::valueFor).toArray();
            try {
                if (constructor.newInstance(arguments).toString().contains(MARKER)) {
                    leaking.add(type.getSimpleName());
                }
            } catch (InvocationTargetException refusedByItsOwnChecks) {
                leaking.add(type.getSimpleName() + " (could not be built)");
            }
        }

        assertEquals(List.of(), leaking);
    }
}
