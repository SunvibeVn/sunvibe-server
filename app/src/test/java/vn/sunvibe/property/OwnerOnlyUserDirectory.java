package vn.sunvibe.property;

import vn.sunvibe.property.core.application.shared.external.user.OwnerAccountFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerNotice;
import vn.sunvibe.property.core.application.shared.external.user.OwnerRoute;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.PasswordCheck;
import vn.sunvibe.property.core.application.shared.external.user.UserDirectoryPort;

/** A directory for tests of the legacy create, which asks only the two owner questions; anything else is a test defect. */
abstract class OwnerOnlyUserDirectory implements UserDirectoryPort {

	@Override
	public OwnerRoute lookupConfirmable(String email) {
		throw new UnsupportedOperationException();
	}

	@Override
	public OwnerAccountFacts accountContext(String userId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public OwnerStandingFacts ownerStanding(String userId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void notifyOwner(String userId, OwnerNotice notice) {
		throw new UnsupportedOperationException();
	}

	@Override
	public PasswordCheck checkPassword(String userId, String password) {
		throw new UnsupportedOperationException();
	}
}
