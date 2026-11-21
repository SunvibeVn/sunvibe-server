package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementSuspension;
import vn.sunvibe.brokerage.core.domain.repository.AgreementSuspensionDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Suspension records in a list, under the rule the database's partial unique index holds: a second open record of an
 * agreement throws, as the adapter's translation does (E05-S8 spec S8-D6).
 */
public final class InMemoryAgreementSuspensionRepository implements AgreementSuspensionDomainRepository {

    private final List<AgreementSuspension> records = new ArrayList<>();

    /** Stores a record as if an earlier transaction had written it. */
    public InMemoryAgreementSuspensionRepository store(AgreementSuspension suspension) {
        records.add(suspension);
        return this;
    }

    @Override
    public void open(AgreementSuspension suspension) {
        if (records.stream().anyMatch(record -> record.agreementId().equals(suspension.agreementId())
                && record.isOpen())) {
            throw new IllegalStateException("The agreement already holds an open suspension.");
        }
        records.add(suspension);
    }

    @Override
    public boolean release(String agreementId, String releasedBy, Instant releasedAt, String reason) {
        for (int index = 0; index < records.size(); index++) {
            AgreementSuspension record = records.get(index);
            if (record.agreementId().equals(agreementId) && record.isOpen()) {
                records.set(index, record.released(releasedBy, releasedAt, reason));
                return true;
            }
        }
        return false;
    }

    @Override
    public List<AgreementSuspension> newestOf(String agreementId, int limit) {
        return records.stream().filter(record -> record.agreementId().equals(agreementId))
                .sorted(Comparator.comparing(AgreementSuspension::suspendedAt).reversed()
                        .thenComparing(AgreementSuspension::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    /** Every record, in insertion order. */
    public List<AgreementSuspension> all() {
        return List.copyOf(records);
    }
}
