package vn.sunvibe.survey.core.application.shared;

/**
 * This instance's survey-media settings the intake needs (plan PD-12, spec A-3): its id in body slot rows and error
 * logs, and how many content bodies it reads at once.
 */
public record MediaInstanceSettings(String instanceId, int bodiesPerInstance) {

    public MediaInstanceSettings {
        if (instanceId == null || instanceId.isBlank() || bodiesPerInstance < 1) {
            throw new IllegalArgumentException("An instance has an id and room for at least one body.");
        }
    }
}
