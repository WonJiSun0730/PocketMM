package com.example.projectmm.api.world;

import org.springframework.test.context.ActiveProfiles;

/** Re-run developer/ordinary-user HTTP checks under the explicit production opt-in profile. */
@ActiveProfiles({"prod", "qa-ui"})
class PocketExperienceQaProductionAccessTest extends PocketExperienceQaHttpAccessTest { }
