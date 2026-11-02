package com.nexhome.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexhome.app.model.NexHomeAppSetting;

public interface NexHomeAppSettingRepository extends JpaRepository<NexHomeAppSetting, Long> {

	Optional<NexHomeAppSetting> findBySettingKey(String settingKey);
}
