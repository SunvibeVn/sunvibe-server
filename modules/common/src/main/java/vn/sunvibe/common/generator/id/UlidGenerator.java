package vn.sunvibe.common.generator.id;

import com.github.f4b6a3.ulid.UlidCreator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("Ulid")
public class UlidGenerator implements IdGenerator<String> {

    @Override
    public String generateId() {
        return UlidCreator.getUlid().toString();
    }
}
