package com.nexhome.app.mapper;

import org.springframework.stereotype.Component;

import com.nexhome.app.dto.NexHomeReviewResponse;
import com.nexhome.app.model.NexHomeBooking;
import com.nexhome.app.model.NexHomeBookingReview;
import com.nexhome.app.model.NexHomeCustomerProfile;
import com.nexhome.app.model.NexHomePartnerProfile;

@Component
public class NexHomeReviewMapper {

	/** Reads the booking, customer and partner: load them in the same query for lists. */
	public NexHomeReviewResponse toReviewDto(NexHomeBookingReview review) {
		NexHomeBooking booking = review.getBooking();
		NexHomeCustomerProfile customer = review.getCustomer();
		NexHomePartnerProfile partner = review.getPartner();
		return new NexHomeReviewResponse(review.getId(), booking.getId(), booking.getPublicId(),
				booking.getServiceNameSnapshot(), customer.getId(), customer.getPublicId(),
				NexHomeBookingMapper.nameOf(customer.getFirstName(), customer.getLastName()), partner.getId(),
				partner.getPublicId(), NexHomeBookingMapper.nameOf(partner.getFirstName(), partner.getLastName()),
				review.getServiceRating(), review.getPartnerRating(), review.getReviewText(), review.getReviewStatus(),
				review.getCreationDate(), review.getCreatedBy(), review.getUpdatedDate(), review.getUpdatedBy());
	}
}
