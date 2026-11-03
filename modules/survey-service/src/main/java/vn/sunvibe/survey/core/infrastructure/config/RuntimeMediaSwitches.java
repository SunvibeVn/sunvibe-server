package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.shared.MediaSwitches;

/**
 * The survey-media switches as the start-up guard left them (spec A-1): uploads on only when switched on and every
 * adapter is configured.
 */
@Component
public class RuntimeMediaSwitches implements MediaSwitches {

    private final MediaRuntimeMode mode;

    public RuntimeMediaSwitches(MediaRuntimeMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean uploadsOn() {
        return mode.uploadsOn();
    }

    @Override
    public boolean servingOn() {
        return mode.servingOn();
    }

    @Override
    public boolean processingOn() {
        return mode.processingOn();
    }
}
