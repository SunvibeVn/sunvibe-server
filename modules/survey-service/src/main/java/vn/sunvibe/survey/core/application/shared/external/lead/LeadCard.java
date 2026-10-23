package vn.sunvibe.survey.core.application.shared.external.lead;

/** The lead fields one survey list row shows: request code, owner name, ward name. The name is personal data. */
public record LeadCard(String leadId, String requestCode, String contactName, String wardName) {

    @Override
    public String toString() {
        return "LeadCard[" + leadId + "]";
    }
}
