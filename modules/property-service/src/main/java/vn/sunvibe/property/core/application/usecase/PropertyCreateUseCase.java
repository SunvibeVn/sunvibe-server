package vn.sunvibe.property.core.application.usecase;

import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.domain.model.property.vo.PropertyId;

public interface PropertyCreateUseCase {
    PropertyId execute(PropertyCreateCommand command);
}
