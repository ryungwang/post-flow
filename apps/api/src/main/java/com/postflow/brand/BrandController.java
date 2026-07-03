package com.postflow.brand;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD for the user's promoted products/campaigns (brand profiles). */
@RestController
@RequestMapping("/brands")
public class BrandController {

    private final BrandRepository repository;

    public BrandController(BrandRepository repository) {
        this.repository = repository;
    }

    public record BrandDto(Long id, String name, String description, String audience,
                           String keyPoints, String ctaText, String url, boolean isDefault) {
        static BrandDto from(Brand b) {
            return new BrandDto(b.getId(), b.getName(), b.getDescription(), b.getAudience(),
                    b.getKeyPoints(), b.getCtaText(), b.getUrl(), b.isDefault());
        }
    }

    public record BrandRequest(String name, String description, String audience,
                               String keyPoints, String ctaText, String url, boolean isDefault) {
    }

    @GetMapping
    public List<BrandDto> list(@AuthenticationPrincipal Long userId) {
        return repository.findByUserIdOrderByIdAsc(userId).stream().map(BrandDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BrandDto create(@AuthenticationPrincipal Long userId, @RequestBody BrandRequest req) {
        Brand b = repository.save(Brand.create(userId, req.name(), req.description(), req.audience(),
                req.keyPoints(), req.ctaText(), req.url(), req.isDefault()));
        if (req.isDefault()) {
            clearOtherDefaults(userId, b.getId());
        }
        return BrandDto.from(b);
    }

    @PutMapping("/{id}")
    @Transactional
    public BrandDto update(@AuthenticationPrincipal Long userId, @PathVariable Long id, @RequestBody BrandRequest req) {
        Brand b = owned(userId, id);
        b.apply(req.name(), req.description(), req.audience(), req.keyPoints(), req.ctaText(), req.url(), req.isDefault());
        if (req.isDefault()) {
            clearOtherDefaults(userId, id);
        }
        return BrandDto.from(b);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        repository.delete(owned(userId, id));
    }

    private Brand owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("brand not found"));
    }

    private void clearOtherDefaults(Long userId, Long keepId) {
        for (Brand other : repository.findByUserIdOrderByIdAsc(userId)) {
            if (!other.getId().equals(keepId) && other.isDefault()) {
                other.apply(other.getName(), other.getDescription(), other.getAudience(),
                        other.getKeyPoints(), other.getCtaText(), other.getUrl(), false);
            }
        }
    }
}
