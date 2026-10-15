package vn.sunvibe.shared.generator.id;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Qualifier("Uuid")
public class UuidGenerator implements IdGenerator<UUID> {
    @Override
    public UUID generateId() {
        return UUID.randomUUID();
    }
}
