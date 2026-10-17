package vn.sunvibe.acquisition.core.application.usecase;

import vn.sunvibe.acquisition.core.application.command.OwnerIntakeCommand;
import vn.sunvibe.acquisition.core.application.query.OwnerIntakeResult;

/**
 * Use Case - Submit the public owner intake form: create an owner lead with its consent record, or return the code of
 * the lead an identical earlier request created (acquisition-owner-intake BR-10).
 */
public interface OwnerIntakeSubmitUseCase {

    OwnerIntakeResult execute(OwnerIntakeCommand command);
}
