package com.postflow.faq;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only help center content (DB-managed). */
@RestController
@RequestMapping("/faqs")
public class FaqController {

    private final FaqRepository repository;

    public FaqController(FaqRepository repository) {
        this.repository = repository;
    }

    public record FaqDto(Long id, String category, String question, String answer) {
    }

    @GetMapping
    public List<FaqDto> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(f -> new FaqDto(f.getId(), f.getCategory(), f.getQuestion(), f.getAnswer()))
                .toList();
    }
}
