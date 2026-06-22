package com.postflow.roi;

import com.postflow.roi.dto.CreateConversionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/conversions")
public class ConversionController {

    private final RoiService roiService;

    public ConversionController(RoiService roiService) {
        this.roiService = roiService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody CreateConversionRequest request) {
        Conversion c = roiService.createConversion(userId, request);
        return Map.of("id", c.getId(), "postId", c.getPostId(), "amount", c.getAmount());
    }
}
