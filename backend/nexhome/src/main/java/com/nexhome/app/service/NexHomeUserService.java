package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeUserRegisterRequest;
import com.nexhome.app.dto.NexHomeUserResponse;

public interface NexHomeUserService {

	NexHomeUserResponse register(NexHomeUserRegisterRequest request);
}
