package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeFunction;

@Repository
public interface NexHomeFunctionRepository extends JpaRepository<NexHomeFunction, Long> {

	Optional<NexHomeFunction> findByFunctionCode(String functionCode);
}
