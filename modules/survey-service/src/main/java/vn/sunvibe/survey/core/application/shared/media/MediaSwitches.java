package vn.sunvibe.survey.core.application.shared.media;

/**
 * The survey-media switches the commands read at each request (spec A-1): what the configuration and the start-up
 * guard leave on. A port, so a test varies them at run time.
 */
public interface MediaSwitches {

    /** spec A-1: uploads are on (create and the content upload); off answers 409 MEDIA_UPLOAD_DISABLED. */
    boolean uploadsOn();

    /** spec A-24: content GETs are served; off answers 503 MEDIA_SERVING_DISABLED. */
    boolean servingOn();

    /** spec A-1: processing effectively on (the switch on and every adapter configured). */
    boolean processingOn();
}
