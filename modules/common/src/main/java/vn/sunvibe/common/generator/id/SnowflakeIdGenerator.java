package vn.sunvibe.common.generator.id;

import io.snowflake.core.SnowflakeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Qualifier("Snowflake")
public class SnowflakeIdGenerator implements IdGenerator<Long> {

    private final SnowflakeGenerator snowflakeGenerator;

    @Override
    public Long generateId() {
        return snowflakeGenerator.nextId(); // Return Snowflake ID
    }
}