package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * An {@link InMemoryAgreementRepository} behind two one-shot hooks run before the next {@link #findByIdForUpdate}
 * (brokerage-agreement E05-S8 plan E05S8-P16): a change another transaction commits between a command's first read and
 * its locked read, or the row deleted there. Every other call goes to the delegate as it is.
 */
public final class ChangeBeforeLockAgreementRepository implements AgreementDomainRepository {

    private final InMemoryAgreementRepository delegate;
    private Runnable beforeNextLock;
    private boolean vanishOnNextLock;

    public ChangeBeforeLockAgreementRepository(InMemoryAgreementRepository delegate) {
        this.delegate = delegate;
    }

    /** Runs the change once, right before the next locked read; the change stores through the delegate. */
    public ChangeBeforeLockAgreementRepository beforeNextLock(Runnable change) {
        beforeNextLock = change;
        return this;
    }

    /** The next locked read finds no row, as when the row was deleted after the first read. */
    public ChangeBeforeLockAgreementRepository vanishOnNextLock() {
        vanishOnNextLock = true;
        return this;
    }

    @Override
    public Optional<BrokerageAgreement> findByIdForUpdate(String id) {
        Runnable change = beforeNextLock;
        beforeNextLock = null;
        if (change != null) {
            change.run();
        }
        if (vanishOnNextLock) {
            vanishOnNextLock = false;
            delegate.findByIdForUpdate(id);
            return Optional.empty();
        }
        return delegate.findByIdForUpdate(id);
    }

    @Override
    public void create(BrokerageAgreement agreement) {
        delegate.create(agreement);
    }

    @Override
    public Optional<BrokerageAgreement> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public void update(BrokerageAgreement agreement) {
        delegate.update(agreement);
    }

    @Override
    public List<UnfinishedAgreement> unfinishedOf(Collection<String> unitIds, TransactionType type) {
        return delegate.unfinishedOf(unitIds, type);
    }

    @Override
    public List<BrokerageAgreement> sentToOwner(String ownerUserId, int limit) {
        return delegate.sentToOwner(ownerUserId, limit);
    }

    @Override
    public List<String> dueLapseAgreementIds(Instant sentAtOrBefore, int limit) {
        return delegate.dueLapseAgreementIds(sentAtOrBefore, limit);
    }

    @Override
    public List<BrokerageAgreement> findAllById(Collection<String> ids) {
        return delegate.findAllById(ids);
    }

    @Override
    public boolean lockForShare(String agreementId) {
        return delegate.lockForShare(agreementId);
    }

    @Override
    public List<String> dueActivationLapseAgreementIds(Instant answeredAtOrBefore, int limit) {
        return delegate.dueActivationLapseAgreementIds(answeredAtOrBefore, limit);
    }

    @Override
    public List<String> dueStartAgreementIds(LocalDate startOnOrBefore, int limit) {
        return delegate.dueStartAgreementIds(startOnOrBefore, limit);
    }

    @Override
    public List<String> dueExpiryAgreementIds(LocalDate endBefore, int limit) {
        return delegate.dueExpiryAgreementIds(endBefore, limit);
    }
}
