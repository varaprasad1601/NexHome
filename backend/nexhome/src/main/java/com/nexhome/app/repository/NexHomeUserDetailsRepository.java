package com.nexhome.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexhome.app.model.NexHomeUserDetails;

@Repository
public interface NexHomeUserDetailsRepository extends JpaRepository<NexHomeUserDetails, Long> {
}
