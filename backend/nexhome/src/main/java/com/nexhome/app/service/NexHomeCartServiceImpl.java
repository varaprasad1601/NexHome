package com.nexhome.app.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexhome.app.dto.NexHomeBookingResponse;
import com.nexhome.app.dto.NexHomeCartItemAddRequest;
import com.nexhome.app.dto.NexHomeCartItemResponse;
import com.nexhome.app.dto.NexHomeCartResponse;
import com.nexhome.app.dto.NexHomeCheckoutRequest;
import com.nexhome.app.dto.NexHomeCheckoutResponse;
import com.nexhome.app.dto.NexHomeServiceQuoteResponse;
import com.nexhome.app.exception.InvalidRequestException;
import com.nexhome.app.exception.ResourceNotFoundException;
import com.nexhome.app.mapper.NexHomeBookingMapper;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeCart;
import com.nexhome.app.model.NexHomeCartItem;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomeLookupCodes.CartStatus;
import com.nexhome.app.model.NexHomeLookupCodes.NotificationType;
import com.nexhome.app.model.NexHomeLookupCodes.PricingType;
import com.nexhome.app.model.NexHomeService;
import com.nexhome.app.model.NexHomeServiceVariant;
import com.nexhome.app.repository.NexHomeCartItemRepository;
import com.nexhome.app.repository.NexHomeCartRepository;
import com.nexhome.app.repository.NexHomeCustomerProfileRepository;
import com.nexhome.app.repository.NexHomeServiceRepository;
import com.nexhome.app.repository.NexHomeServiceVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexHomeCartServiceImpl implements NexHomeCartService {

	/** A cart holds at most this many items. */
	static final int MAX_ITEMS = 20;

	private final NexHomeCartRepository cartRepository;
	private final NexHomeCartItemRepository cartItemRepository;
	private final NexHomeCustomerProfileRepository customerProfileRepository;
	private final NexHomeServiceRepository serviceRepository;
	private final NexHomeServiceVariantRepository variantRepository;
	private final NexHomeCatalogService catalogService;
	private final NexHomeBookingCreator bookingCreator;
	private final NexHomePermissionService permissionService;
	private final NexHomeBookingMapper bookingMapper;
	private final NexHomeNotifier notifier;

	@Override
	@Transactional(readOnly = true)
	public NexHomeCartResponse getMyCart() {
		NexHomeCustomerProfile customer = myCustomerProfile();
		return cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
				.map(this::toCartDto)
				.orElse(new NexHomeCartResponse(null, customer.getId(), CartStatus.ACTIVE, List.of(), BigDecimal.ZERO));
	}

	@Override
	@Transactional
	public NexHomeCartResponse addItem(NexHomeCartItemAddRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeCustomerProfile customer = myCustomerProfile();
		NexHomeService service = serviceRepository.findWithCategoryById(request.serviceId())
				.filter(found -> found.isActive() && found.getCategory().isActive())
				.orElseThrow(() -> new ResourceNotFoundException("Service does not exist or is not offered."));
		NexHomeServiceVariant variant = null;
		if (request.serviceVariantId() != null) {
			variant = variantRepository.findById(request.serviceVariantId())
					.filter(found -> found.getService().getId().equals(service.getId()))
					.orElseThrow(() -> new InvalidRequestException("The variant is not an option of this service."));
			if (!variant.isActive()) {
				// A switched-off hourly package is booked as one hour at the service's price; other options must be on
				if (!PricingType.HOURLY.equals(service.getPricingType())) {
					throw new InvalidRequestException("The variant is not an active option of this service.");
				}
				variant = null;
			}
		}
		NexHomeServiceQuoteResponse quote = catalogService.quote(service.getId(),
				variant == null ? null : variant.getId(), null);

		NexHomeCart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
				.orElseGet(() -> {
					NexHomeCart created = new NexHomeCart();
					created.setCustomer(customer);
					created.setStatus(CartStatus.ACTIVE);
					created.setCreatedBy(actingUserId);
					created.setUpdatedBy(actingUserId);
					return cartRepository.save(created);
				});
		if (cartItemRepository.findByCartIdOrderByIdAsc(cart.getId()).size() >= MAX_ITEMS) {
			throw new InvalidRequestException("A cart can hold at most " + MAX_ITEMS + " services.");
		}
		NexHomeCartItem item = new NexHomeCartItem();
		item.setCart(cart);
		item.setService(service);
		item.setVariant(variant);
		item.setEstimatedUnitPrice(quote.total());
		item.setCreatedBy(actingUserId);
		item.setUpdatedBy(actingUserId);
		cartItemRepository.save(item);
		cart.setUpdatedBy(actingUserId);
		return toCartDto(cart);
	}

	@Override
	@Transactional
	public NexHomeCartResponse removeItem(Long cartItemId) {
		NexHomeCartItem item = cartItemRepository.findById(cartItemId)
				.orElseThrow(() -> new ResourceNotFoundException("Cart item does not exist."));
		NexHomeCart cart = item.getCart();
		if (!cart.getCustomer().getUser().getId().equals(permissionService.currentUserId())) {
			throw new AccessDeniedException("Only the customer changes their cart.");
		}
		if (!CartStatus.ACTIVE.equals(cart.getStatus())) {
			throw new InvalidRequestException("The cart is " + cart.getStatus() + " and cannot be changed.");
		}
		cartItemRepository.delete(item);
		cartItemRepository.flush();
		return toCartDto(cart);
	}

	@Override
	@Transactional
	public NexHomeCheckoutResponse checkout(NexHomeCheckoutRequest request) {
		Long actingUserId = permissionService.currentUserId();
		NexHomeCustomerProfile customer = myCustomerProfile();
		NexHomeCart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
				.orElseThrow(() -> new InvalidRequestException("The cart is empty."));
		List<NexHomeCartItem> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId());
		if (items.isEmpty()) {
			throw new InvalidRequestException("The cart is empty.");
		}

		// Every item is priced again on the server; one failure books nothing (one transaction)
		UUID checkoutId = UUID.randomUUID();
		List<NexHomeBookingResponse> bookings = new ArrayList<>();
		for (NexHomeCartItem item : items) {
			NexHomeBooking booking = bookingCreator.create(new NexHomeBookingCreator.Order(customer,
					item.getService().getId(), item.getVariant() == null ? null : item.getVariant().getId(),
					request.addressId(), request.bookingType(), request.scheduledStartAt(), request.paymentMethod(),
					null, checkoutId, false), actingUserId);
			bookings.add(bookingMapper.toBookingDto(booking));
		}
		cart.setStatus(CartStatus.CHECKED_OUT);
		cart.setUpdatedBy(actingUserId);

		notifier.notifyStaff("BOOKING_MANAGE", new NexHomeNotifier.Message(NotificationType.BOOKING,
				bookings.size() == 1 ? "New booking " + bookings.get(0).publicId()
						: bookings.size() + " new bookings from " + customer.getPublicId(),
				bookings.stream().map(NexHomeBookingResponse::serviceName).distinct()
						.collect(java.util.stream.Collectors.joining(", ")),
				NexHomeNotifier.BOOKING, bookings.get(0).bookingId()), actingUserId);
		log.info("Checkout {}: {} booking(s) for customer {}", checkoutId, bookings.size(), customer.getPublicId());
		return new NexHomeCheckoutResponse(checkoutId, bookings);
	}

	private NexHomeCartResponse toCartDto(NexHomeCart cart) {
		List<NexHomeCartItemResponse> items = cartItemRepository.findByCartIdOrderByIdAsc(cart.getId()).stream()
				.map(bookingMapper::toCartItemDto)
				.toList();
		BigDecimal total = items.stream().map(NexHomeCartItemResponse::estimatedUnitPrice)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new NexHomeCartResponse(cart.getId(), cart.getCustomer().getId(), cart.getStatus(), items, total);
	}

	private NexHomeCustomerProfile myCustomerProfile() {
		return customerProfileRepository.findByUserId(permissionService.currentUserId())
				.orElseThrow(() -> new AccessDeniedException("Only customers have a cart."));
	}
}
