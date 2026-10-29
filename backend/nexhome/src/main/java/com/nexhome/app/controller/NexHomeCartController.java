package com.nexhome.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexhome.app.dto.NexHomeCartItemAddRequest;
import com.nexhome.app.dto.NexHomeCartResponse;
import com.nexhome.app.dto.NexHomeCheckoutRequest;
import com.nexhome.app.dto.NexHomeCheckoutResponse;
import com.nexhome.app.service.NexHomeCartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Module 6: the signed-in customer's cart (customers only, checked in the service) and checkout. */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class NexHomeCartController {

	private final NexHomeCartService cartService;

	@GetMapping("/me")
	public NexHomeCartResponse getMyCart() {
		return cartService.getMyCart();
	}

	@PostMapping("/items/add")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeCartResponse addItem(@Valid @RequestBody NexHomeCartItemAddRequest request) {
		return cartService.addItem(request);
	}

	@DeleteMapping("/items/delete/{cartItemId}")
	public NexHomeCartResponse removeItem(@PathVariable Long cartItemId) {
		return cartService.removeItem(cartItemId);
	}

	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public NexHomeCheckoutResponse checkout(@Valid @RequestBody NexHomeCheckoutRequest request) {
		return cartService.checkout(request);
	}
}
