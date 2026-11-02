-- The website's FAQs (APP_FAQS.txt): app FAQs (no category or service) for Web Audiences (V48), published, in
-- this order. A question already there as a WEB app FAQ is not added again.
INSERT INTO nexhome_faqs (question, answer, audience, display_order, is_published, created_by, updated_by)
SELECT v.question, v.answer, 'WEB', v.display_order, TRUE, 1, 1
FROM (VALUES
	($faq$What is NexHome?$faq$,
	 $faq$NexHome is a household services platform that helps customers enquire about available services and connect with service professionals.$faq$, 1),
	($faq$Which locations does NexHome currently serve?$faq$,
	 $faq$NexHome is launching in Nuzvid, Andhra Pradesh. Service coverage may vary within the area and nearby areas depending on the request and professional availability.$faq$, 2),
	($faq$What are NexHome’s operating hours?$faq$,
	 $faq$Monday through Sunday, 9:00 AM to 5:00 PM (IST).$faq$, 3),
	($faq$How can I request a service?$faq$,
	 $faq$Choose a service on the website, contact us through WhatsApp, or complete the enquiry form. Our team reviews requests during business hours.$faq$, 4),
	($faq$Does submitting an enquiry confirm a booking?$faq$,
	 $faq$No. An enquiry is reviewed first. Any service arrangement depends on scope, charges, location and professional availability.$faq$, 5),
	($faq$How are service charges decided?$faq$,
	 $faq$Charges depend on the type and scope of work. Applicable pricing or an estimate will be discussed before a service is confirmed.$faq$, 6),
	($faq$Can I enquire about more than one service?$faq$,
	 $faq$Yes. Please describe each service requirement so our team can review them separately.$faq$, 7),
	($faq$How can I join as a service professional?$faq$,
	 $faq$Complete the professional registration form on this website and send your details, or contact us directly through WhatsApp.$faq$, 8),
	($faq$Is worker registration the same as approval?$faq$,
	 $faq$No. Registration expresses interest. NexHome reviews the details before communicating any next steps.$faq$, 9),
	($faq$Can I request help on Sunday?$faq$,
	 $faq$Yes. Our published business hours are Monday through Sunday, 9:00 AM to 5:00 PM (IST). You can also send a WhatsApp enquiry at any time.$faq$, 10)
) AS v (question, answer, display_order)
WHERE NOT EXISTS (SELECT 1 FROM nexhome_faqs f WHERE f.question = v.question AND f.audience = 'WEB'
                  AND f.category_id IS NULL AND f.service_id IS NULL);
