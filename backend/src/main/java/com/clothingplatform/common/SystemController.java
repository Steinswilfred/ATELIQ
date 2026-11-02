package com.clothingplatform.common;

import com.clothingplatform.config.AppProperties;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final AppProperties props;
    public SystemController(AppProperties props) { this.props = props; }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
            "service", "clothing-platform",
            "environment", props.environment(),
            "time", Instant.now().toString()));
    }
}
