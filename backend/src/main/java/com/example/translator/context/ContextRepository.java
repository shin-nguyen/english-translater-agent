package com.example.translator.context;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContextRepository extends JpaRepository<Context, Long> {

    List<Context> findAllByOrderByNameAsc();
}
