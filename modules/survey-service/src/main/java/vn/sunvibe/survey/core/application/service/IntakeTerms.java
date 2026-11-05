package vn.sunvibe.survey.core.application.service;

import vn.sunvibe.survey.core.application.usecase.IntakeSourceView;
import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.Deposit;
import vn.sunvibe.survey.core.domain.model.checklist.DepositBasis;
import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.Money;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Flattens G8 terms into the reference list of an inventory intake (property-create intake spec 5.1, BR-10): money stays
 * amount, currency and unit (ADR-006), anything else is text, and a term still unknown stays listed with no value.
 */
final class IntakeTerms {

    private final List<IntakeSourceView.Term> terms;
    private final String spaceCodeOrNull;

    IntakeTerms(List<IntakeSourceView.Term> terms, String spaceCodeOrNull) {
        this.terms = terms;
        this.spaceCodeOrNull = spaceCodeOrNull;
    }

    void charge(String name, SourcedField<Charge> field) {
        if (field == null || !field.isValue()) {
            add(name, null, null, null, null, field);
            return;
        }
        Charge charge = field.value();
        Money money = charge.amount();
        add(name, money == null ? null : money.amount(), money == null ? null : money.currency(),
                charge.chargeUnit() == null ? null : charge.chargeUnit().name(), charge.basis().name(), field);
    }

    /** A space's override is listed only when it holds a value: an empty one leaves the common term in force. */
    void overridden(String name, SourcedField<Charge> field) {
        if (field != null && field.isValue()) {
            charge(name, field);
        }
    }

    void rent(String name, SourcedField<RentMoney> field) {
        if (field == null || !field.isValue()) {
            add(name, null, null, null, null, field);
            return;
        }
        RentMoney rent = field.value();
        add(name, rent.amount(), rent.currency(), rent.unit(), null, field);
    }

    void deposit(String name, SourcedField<Deposit> field) {
        if (field == null || !field.isValue()) {
            add(name, null, null, null, null, field);
            return;
        }
        Deposit deposit = field.value();
        if (deposit.basis() == DepositBasis.MONTHS) {
            add(name, null, null, null, deposit.months() + " months", field);
        } else {
            add(name, deposit.amount().amount(), deposit.amount().currency(), null, null, field);
        }
    }

    void fees(String name, SourcedField<List<Fee>> field) {
        if (field == null || !field.isValue()) {
            add(name, null, null, null, null, field);
            return;
        }
        for (Fee fee : field.value()) {
            Money money = fee.amount();
            add(name + ":" + fee.name(), money == null ? null : money.amount(), money == null ? null : money.currency(),
                    fee.chargeUnit() == null ? null : fee.chargeUnit().name(), null, field);
        }
    }

    <V> void text(String name, SourcedField<V> field, Function<V, String> render) {
        add(name, null, null, null, field != null && field.isValue() ? render.apply(field.value()) : null, field);
    }

    private void add(String name, Long amount, String currency, String unit, String text, SourcedField<?> field) {
        boolean known = field != null && field.isValue();
        String source = known && field.source() != null ? field.source().name() : null;
        LocalDate date = known ? field.confirmedOn() : null;
        terms.add(new IntakeSourceView.Term(name, spaceCodeOrNull, amount, currency, unit, text, source, date));
    }
}
