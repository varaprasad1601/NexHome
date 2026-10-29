package com.nexhome.app.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeAddressCreateRequest;
import com.nexhome.app.dto.NexHomeAddressResponse;
import com.nexhome.app.dto.NexHomeAddressUpdateRequest;
import com.nexhome.app.exception.DuplicateResourceException;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeAddressMapper;
import com.nexhome.app.model.NexHomeAccess;
import com.nexhome.app.model.NexHomeAddress;
import com.nexhome.app.model.NexHomeLookupCodes.Types;
import com.nexhome.app.model.NexHomeRole;
import com.nexhome.app.model.NexHomeUser;
import com.nexhome.app.repository.NexHomeAddressRepository;
import com.nexhome.app.repository.NexHomeBookingRepository;
import com.nexhome.app.repository.NexHomeUserRepository;
import com.nexhome.app.repository.NexHomeUserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeAddressServiceImpl implements NexHomeAddressService {

	private final NexHomeAddressRepository addressRepository;
	private final NexHomeUserRepository userRepository;
	private final NexHomeUserRoleRepository userRoleRepository;
	private final NexHomeLookupService lookupService;
	private final NexHomePermissionService permissionService;
	private final NexHomeAddressMapper addressMapper;
	private final NexHomeBookingRepository bookingRepository;

	@Override
	@Transactional
	public NexHomeAddressResponse createAddress(NexHomeAddressCreateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeUser user = userRepository.findById(request.userId())
				.orElseThrow(() -> new ResourceNotFoundException("User does not exist."));
		if (!userRoleRepository.existsByUserIdAndRoleCodeAndActiveTrueAndRoleActiveFlagTrue(user.getId(),
				NexHomeRole.CUSTOMER)) {
			throw new InvalidRequestException("Addresses are only for accounts with the CUSTOMER role.");
		}
		requireCoordinatePair(request.latitude() == null, request.longitude() == null);

		NexHomeAddress address = addressMapper.toAddressEntity(request, user, actingUserId);
		address.setAddressType(lookupService.validateCode(Types.ADDRESS_TYPE, request.addressType(), "Address type"));
		NexHomeProfileServiceImpl.requirePostalCodeForCountry(address.getCountryCode(), address.getPostalCode());

		boolean hasDefault = addressRepository.findByUserIdAndDefaultAddressTrue(user.getId()).isPresent();
		boolean makeDefault = request.isDefault() == null ? !hasDefault : request.isDefault();
		if (makeDefault) {
			clearCurrentDefault(user.getId(), actingUserId);
			address.setDefaultAddress(true);
		}
		address = addressRepository.save(address);

		log.info("Address {} created for User ID {}", address.getPublicId(), user.getId());
		return addressMapper.toAddressDto(address);
	}

	@Override
	@Transactional
	public NexHomeAddressResponse updateAddress(NexHomeAddressUpdateRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeAddress address = findAddress(request.addressId());
		requireSelfOrCan(address.getUser().getId(), NexHomeAccess.WRITE);
		requireCoordinatePair(request.latitude() == null, request.longitude() == null);

		// Validated before the entity changes, so the lookup query does not flush a half-applied update
		String addressType = request.addressType() == null ? null
				: lookupService.validateCode(Types.ADDRESS_TYPE, request.addressType(), "Address type");

		addressMapper.updateAddressEntity(address, request, actingUserId);
		if (addressType != null) {
			address.setAddressType(addressType);
		}
		NexHomeProfileServiceImpl.requirePostalCodeForCountry(address.getCountryCode(), address.getPostalCode());
		if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
			clearCurrentDefault(address.getUser().getId(), actingUserId);
			address.setDefaultAddress(true);
		}
		else if (Boolean.FALSE.equals(request.isDefault())) {
			address.setDefaultAddress(false);
		}

		log.info("Address {} updated", address.getPublicId());
		return addressMapper.toAddressDto(address);
	}

	@Override
	@Transactional
	public void deleteAddress(Long addressId) {
		NexHomeAddress address = findAddress(addressId);
		requireSelfOrCan(address.getUser().getId(), NexHomeAccess.DELETE);
		// Bookings keep their address (they also hold a snapshot of it)
		if (bookingRepository.existsByAddressId(address.getId())) {
			throw new DuplicateResourceException("The address is used by bookings and cannot be deleted.");
		}
		addressRepository.delete(address);
		log.info("Address {} deleted", address.getPublicId());
	}

	@Override
	@Transactional(readOnly = true)
	public NexHomeAddressResponse getAddress(Long addressId) {
		NexHomeAddress address = findAddress(addressId);
		requireSelfOrCan(address.getUser().getId(), NexHomeAccess.READ);
		return addressMapper.toAddressDto(address);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAddressResponse> getUserAddresses(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new ResourceNotFoundException("User does not exist.");
		}
		return addressRepository.findByUserIdOrderByDefaultAddressDescCreationDateDescIdDesc(userId).stream()
				.map(addressMapper::toAddressDto)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<NexHomeAddressResponse> getCurrentUserAddresses() {
		return getUserAddresses(permissionService.currentUserId());
	}

	/**
	 * Only one default per customer: the current one is cleared and flushed before the new default is written,
	 * because the one-default index would otherwise reject it.
	 */
	private void clearCurrentDefault(Long userId, Long actingUserId) {
		addressRepository.findByUserIdAndDefaultAddressTrue(userId).ifPresent(current -> {
			current.setDefaultAddress(false);
			current.setUpdatedBy(actingUserId);
			addressRepository.saveAndFlush(current);
		});
	}

	private static void requireCoordinatePair(boolean noLatitude, boolean noLongitude) {
		if (noLatitude != noLongitude) {
			throw new InvalidRequestException("Latitude and longitude must be given together.");
		}
	}

	private void requireSelfOrCan(Long ownerId, NexHomeAccess access) {
		if (!permissionService.isSelfOrCan(ownerId, NexHomeProfileServiceImpl.CUSTOMER_MANAGE, access.name())) {
			throw new AccessDeniedException("Not the owner and no CUSTOMER_MANAGE " + access + " access.");
		}
	}

	private NexHomeAddress findAddress(Long addressId) {
		return addressRepository.findById(addressId)
				.orElseThrow(() -> new ResourceNotFoundException("Address does not exist."));
	}
}
