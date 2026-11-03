package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.application.shared.MediaSwitches;

/** The survey-media switches of a unit test (spec A-1): uploads on unless a test turns them off. */
public final class MutableMediaSwitches implements MediaSwitches {

    public boolean uploads = true;
    public boolean serving = true;
    public boolean processing = true;

    @Override
    public boolean uploadsOn() {
        return uploads;
    }

    @Override
    public boolean servingOn() {
        return serving;
    }

    @Override
    public boolean processingOn() {
        return processing;
    }
}
