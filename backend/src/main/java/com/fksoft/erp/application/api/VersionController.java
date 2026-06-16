package com.fksoft.erp.application.api;

import com.fksoft.erp.application.api.dto.VersionResponse;
import com.fksoft.erp.infra.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint exposing the running application version (SemVer), so the UI can show it (footer /
 * about). No authentication required.
 */
@RestController
@RequiredArgsConstructor
public class VersionController {

    private final AppProperties app;

    /**
     * The running application version, sourced from {@code APP_VERSION} (SemVer MAJOR.MINOR.PATCH).
     *
     * @return the version payload
     */
    @GetMapping("/api/version")
    public VersionResponse version() {
        return new VersionResponse(app.version());
    }
}
