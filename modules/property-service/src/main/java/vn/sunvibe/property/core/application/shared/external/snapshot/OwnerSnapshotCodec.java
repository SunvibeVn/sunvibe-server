package vn.sunvibe.property.core.application.shared.external.snapshot;

import vn.sunvibe.property.core.application.usecase.intake.OwnerRequestSnapshot;

/** The stored text of a request's display snapshot (property-create intake spec 4.6 step 1). */
public interface OwnerSnapshotCodec {

    String write(OwnerRequestSnapshot snapshot);

    OwnerRequestSnapshot read(String stored);
}
