package com.example.translator.aimodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {

    List<AiModelConfig> findAllByEnabledTrueOrderByLabelAsc();

    List<AiModelConfig> findAllByOrderByLabelAsc();
}
