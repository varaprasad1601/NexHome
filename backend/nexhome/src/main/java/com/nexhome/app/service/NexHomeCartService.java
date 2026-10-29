package com.nexhome.app.service;

import com.nexhome.app.dto.NexHomeCartItemAddRequest;
import com.nexhome.app.dto.NexHomeCartResponse;
import com.nexhome.app.dto.NexHomeCheckoutRequest;
import com.nexhome.app.dto.NexHomeCheckoutResponse;

/** Module 6: the signed-in customer's cart and its checkout into bookings. */
public interface NexHomeCartService {

	NexHomeCartResponse getMyCart();

	NexHomeCartResponse addItem(NexHomeCartItemAddRequest request);

	NexHomeCartResponse removeItem(Long cartItemId);

	/** One booking per cart item, sharing a checkout id; the cart is then CHECKED_OUT. */
	NexHomeCheckoutResponse checkout(NexHomeCheckoutRequest request);
}
