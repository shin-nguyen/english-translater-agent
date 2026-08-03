package com.example.translator.translation;

import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/translate")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping
    public TranslateResponse translate(@Valid @RequestBody TranslateRequest request) {
        return translationService.translate(request);
    }
}
