// Survey service module - site-survey appointments of owner leads (survey-schedule); business logic only
dependencies {
    implementation(project(":modules:shared-service"))
    // survey-media spec 4.12, A-15, A-25: the S3 API for Cloudflare R2 (version pinned on 2026-11-02, plan Task A7);
    // the Apache client gives each of the three clients its own bounded pool and acquisition timeout
    implementation(platform("software.amazon.awssdk:bom:2.57.8"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:apache-client")
}
dependencies {
    // survey-media plan Task A13: the servlet request mocks of the JSON body filter test (version from the Boot BOM)
    testImplementation("org.springframework:spring-test")
}
